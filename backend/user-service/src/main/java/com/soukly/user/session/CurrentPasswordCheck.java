package com.soukly.user.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.user.account.UserAccount;
import com.soukly.user.token.OpaqueTokens;

/**
 * Checks the current password that the sensitive profile actions ask for (AUTH-05: change of password or email,
 * deletion), with the failure throttling of the login (AUTH-02) on the same counter: wrong current passwords and
 * wrong login passwords add up. Runs in its own transaction, so that a failure is counted even though the action
 * fails.
 */
@Component
public class CurrentPasswordCheck {

    private static final Logger log = LoggerFactory.getLogger(CurrentPasswordCheck.class);

    /** Registration accepts 128 characters at most; a longer password is never right. */
    private static final int MAX_PASSWORD_LENGTH = 1024;

    private final LoginThrottle throttle;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    CurrentPasswordCheck(LoginThrottle throttle, PasswordEncoder passwordEncoder, Clock clock) {
        this.throttle = throttle;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * @throws CurrentPasswordInvalidException the password is not the one of the account
     * @throws SessionException.LoginThrottled too many failures: the password was not checked
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = { CurrentPasswordInvalidException.class, SessionException.class })
    public void verify(UserAccount account, String currentPassword) {
        String emailHash = OpaqueTokens.sha256Hex(account.email());
        Instant now = clock.instant();
        Optional<Duration> wait = throttle.waitBefore(emailHash, now);
        if (wait.isPresent()) {
            throw new SessionException.LoginThrottled(wait.get());
        }
        String password = currentPassword == null || currentPassword.length() > MAX_PASSWORD_LENGTH ? ""
                : currentPassword;
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throttle.recordFailure(emailHash, now);
            log.info("Wrong current password for user {}", account.id());
            throw new CurrentPasswordInvalidException();
        }
        throttle.reset(emailHash);
    }
}
