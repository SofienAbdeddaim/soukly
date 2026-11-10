package com.soukly.social.post;

/**
 * Where a post goes: the accounts of the seller (SOC-04, SOC-05), or Soukly's own Page and Instagram account
 * (SOC-07). Each channel has its table, with the same columns, and its metrics.
 */
enum PostChannel {

    SELLER("social_posts", "social.posts", "soukly_share"),
    HOUSE("house_posts", "social.house_posts", "soukly_house");

    private final String table;
    private final String metric;
    private final String campaign;

    PostChannel(String table, String metric, String campaign) {
        this.table = table;
        this.metric = metric;
        this.campaign = campaign;
    }

    /** The table of the posts; a constant of this enum, never an input. */
    String table() {
        return table;
    }

    /** Name of the counter of the posts entering a status. */
    String metric() {
        return metric;
    }

    /** {@code utm_campaign} of the links of the posts. */
    String campaign() {
        return campaign;
    }

    /** In the logs: "Facebook post" or "Facebook house post". */
    String label() {
        return this == HOUSE ? " house" : "";
    }
}
