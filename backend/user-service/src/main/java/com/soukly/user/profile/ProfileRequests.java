package com.soukly.user.profile;

/** Bodies of the profile endpoints (AUTH-05). Passwords and tokens are never logged. */
final class ProfileRequests {

    private ProfileRequests() {
    }

    record ChangePassword(String currentPassword, String newPassword) {

        @Override
        public String toString() {
            return "ChangePassword[]";
        }
    }

    record ChangeEmail(String newEmail, String currentPassword) {

        @Override
        public String toString() {
            return "ChangeEmail[]";
        }
    }

    record ConfirmEmail(String token) {

        @Override
        public String toString() {
            return "ConfirmEmail[]";
        }
    }

    record DeleteAccount(String currentPassword) {

        @Override
        public String toString() {
            return "DeleteAccount[]";
        }
    }
}
