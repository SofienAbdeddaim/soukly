package com.soukly.user.mail;

import java.time.Instant;
import java.util.UUID;

import com.soukly.user.account.Language;

/**
 * Email to send once the transaction that requested it commits. Published as an application event.
 */
public sealed interface AccountMail {

    UUID userId();

    String to();

    String displayName();

    Language language();

    /** Link to verify the email of a pending account; {@code token} is never stored or logged. */
    record Verification(UUID userId, String to, String displayName, Language language, String token)
            implements AccountMail {

        @Override
        public String toString() {
            return "Verification[userId=" + userId + "]";
        }
    }

    /** Someone tried to register with the email of an active account. */
    record AccountExists(UUID userId, String to, String displayName, Language language) implements AccountMail {

        @Override
        public String toString() {
            return "AccountExists[userId=" + userId + "]";
        }
    }

    /** Link to choose a new password (AUTH-04); {@code token} is never stored or logged. */
    record PasswordReset(UUID userId, String to, String displayName, Language language, String token)
            implements AccountMail {

        @Override
        public String toString() {
            return "PasswordReset[userId=" + userId + "]";
        }
    }

    /** The password was reset at {@code changedAt}; tells the owner what to do if it was not them. */
    record PasswordChanged(UUID userId, String to, String displayName, Language language, Instant changedAt)
            implements AccountMail {

        @Override
        public String toString() {
            return "PasswordChanged[userId=" + userId + "]";
        }
    }
}
