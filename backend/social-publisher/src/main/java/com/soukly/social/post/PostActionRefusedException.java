package com.soukly.social.post;

/**
 * "Try again" or "Publish again" refused (SOC-06), answered {@code 409} with the type of {@link #reason()}.
 */
public class PostActionRefusedException extends RuntimeException {

    /** The type of the problem, and its title and detail in English. */
    public enum Reason {
        NOT_RETRYABLE("not-retryable", "Post cannot be tried again",
                "This post is not in a failure that can be tried again."),
        RETRY_LIMIT_REACHED("retry-limit-reached", "No attempt left",
                "This post was tried again too many times."),
        LISTING_NOT_ONLINE("listing-not-online", "Listing not online",
                "The listing is not online any more: its post cannot be published."),
        NETWORK_NOT_CONNECTED("network-not-connected", "Network not connected",
                "Connect this network again before publishing the listings.");

        private final String slug;
        private final String title;
        private final String detail;

        Reason(String slug, String title, String detail) {
            this.slug = slug;
            this.title = title;
            this.detail = detail;
        }

        public String slug() {
            return slug;
        }

        public String title() {
            return title;
        }

        public String detail() {
            return detail;
        }
    }

    private final Reason reason;

    PostActionRefusedException(Reason reason) {
        super(reason.slug(), null, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
