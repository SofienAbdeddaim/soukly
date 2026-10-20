package com.soukly.user.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.user.account.AccountStatus;
import com.soukly.user.account.UserAccount;
import com.soukly.user.account.UserAccountRepository;
import com.soukly.user.jwt.AccessTokens;
import com.soukly.user.registration.EmailAddresses;
import com.soukly.user.token.OpaqueTokens;

/**
 * Login, refresh and logout.
 *
 * <ul>
 * <li>Login answers the same way, in the same time, for an unknown email and a wrong password: a password is
 * hashed in both cases.</li>
 * <li>Each refresh token is used once: a refresh replaces it with a new one in the same family, valid 30 days
 * again. Presenting a replaced token revokes the whole family.</li>
 * </ul>
 */
@Service
class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /** Registration accepts 128 characters at most; a longer password is never right. */
    private static final int MAX_PASSWORD_LENGTH = 1024;

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final LoginThrottle throttle;
    private final AccessTokens accessTokens;
    private final PasswordEncoder passwordEncoder;
    private final SessionProperties properties;
    private final Clock clock;
    /** Compared with when the email is unknown, so that the response takes as long as for a known email. */
    private final String dummyPasswordHash;

    SessionService(UserAccountRepository users, RefreshTokenRepository refreshTokens, LoginThrottle throttle,
            AccessTokens accessTokens, PasswordEncoder passwordEncoder, SessionProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.throttle = throttle;
        this.accessTokens = accessTokens;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(OpaqueTokens.generate());
    }

    @Transactional(noRollbackFor = SessionException.class)
    public Session login(LoginRequest request, String userAgent) {
        String email = Optional.ofNullable(EmailAddresses.normalize(request.email())).orElse("");
        String emailHash = OpaqueTokens.sha256Hex(email);
        Instant now = clock.instant();
        Optional<Duration> wait = throttle.waitBefore(emailHash, now);
        if (wait.isPresent()) {
            throw new SessionException.LoginThrottled(wait.get());
        }

        String password = request.password() == null || request.password().length() > MAX_PASSWORD_LENGTH ? ""
                : request.password();
        Optional<UserAccount> account = users.findByEmail(email);
        boolean matches;
        if (account.isPresent()) {
            matches = passwordEncoder.matches(password, account.get().passwordHash());
        } else {
            passwordEncoder.matches(password, dummyPasswordHash);
            matches = false;
        }
        if (!matches) {
            throttle.recordFailure(emailHash, now);
            log.info("Login failed{}", account.map(found -> " for user " + found.id()).orElse(" (unknown email)"));
            throw new SessionException.InvalidCredentials();
        }

        UserAccount user = account.get();
        throttle.reset(emailHash);
        if (user.status() != AccountStatus.ACTIVE) {
            log.info("Login refused for user {}: email not verified", user.id());
            throw new SessionException.EmailNotVerified();
        }
        String refreshToken = issueRefreshToken(user.id(), UUID.randomUUID(), userAgent, now);
        log.info("User {} signed in", user.id());
        return new Session(accessTokens.issue(user.id()), user, refreshToken);
    }

    @Transactional(noRollbackFor = SessionException.class)
    public Session refresh(String presentedToken, String userAgent) {
        if (!OpaqueTokens.isPlausible(presentedToken)) {
            throw new SessionException.SessionExpired();
        }
        RefreshToken token = refreshTokens.findByTokenHash(OpaqueTokens.sha256Hex(presentedToken))
                .orElseThrow(SessionException.SessionExpired::new);
        Instant now = clock.instant();
        if (token.isRevoked()) {
            throw new SessionException.SessionRevoked();
        }
        if (token.isUsed()) {
            refreshTokens.revokeFamily(token.familyId(), now);
            log.warn("Replaced refresh token presented again: session family {} of user {} revoked",
                    token.familyId(), token.userId());
            throw new SessionException.SessionRevoked();
        }
        if (token.isExpired(now)) {
            throw new SessionException.SessionExpired();
        }
        UserAccount user = users.findById(token.userId())
                .filter(found -> found.status() == AccountStatus.ACTIVE)
                .orElseThrow(SessionException.SessionExpired::new);

        token.markUsed(now);
        String refreshToken = issueRefreshToken(user.id(), token.familyId(), userAgent, now);
        return new Session(accessTokens.issue(user.id()), user, refreshToken);
    }

    /** Revokes the family of the token, if it is one of ours; the already issued access tokens expire by themselves. */
    @Transactional
    public void logout(String presentedToken) {
        if (!OpaqueTokens.isPlausible(presentedToken)) {
            return;
        }
        refreshTokens.findByTokenHash(OpaqueTokens.sha256Hex(presentedToken)).ifPresent(token -> {
            refreshTokens.revokeFamily(token.familyId(), clock.instant());
            log.info("User {} signed out", token.userId());
        });
    }

    private String issueRefreshToken(UUID userId, UUID familyId, String userAgent, Instant now) {
        String token = OpaqueTokens.generate();
        refreshTokens.save(new RefreshToken(userId, familyId, OpaqueTokens.sha256Hex(token), now,
                now.plus(properties.refreshTokenTtl()), userAgent));
        return token;
    }
}
