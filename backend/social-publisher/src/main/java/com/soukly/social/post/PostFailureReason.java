package com.soukly.social.post;

/** Why a {@link PostBadge#FAILED} post failed, in words the front end translates (SOC-06); never Meta's message. */
public enum PostFailureReason {
    /** The network did not answer after every automatic attempt. */
    RETRIES_EXHAUSTED,
    /** The network refused one of the photos. */
    MEDIA_REJECTED,
    /** The network was not connected when the post was due; connected again since. */
    NOT_CONNECTED,
    /** The network refused the post. */
    REJECTED
}
