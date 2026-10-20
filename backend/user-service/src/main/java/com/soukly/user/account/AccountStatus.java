package com.soukly.user.account;

public enum AccountStatus {

    /** Registered, email not verified yet: the account cannot sign in. */
    PENDING_VERIFICATION,

    /** Email verified. */
    ACTIVE
}
