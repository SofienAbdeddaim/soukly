package com.soukly.user.verification;

public enum TokenReason {

    /** First email of a new account. */
    REGISTRATION,

    /** Any later email: resend request or new registration of a pending account. Limited per hour. */
    RESEND
}
