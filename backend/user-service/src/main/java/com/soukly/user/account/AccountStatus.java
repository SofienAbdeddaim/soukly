package com.soukly.user.account;

public enum AccountStatus {

    /** Registered, email not verified yet: the account cannot sign in. */
    PENDING_VERIFICATION,

    /** Email verified. */
    ACTIVE,

    /**
     * Deleted by its owner (AUTH-05): cannot sign in, its address is free again; erased for good by the purge after
     * 30 days.
     */
    DELETED
}
