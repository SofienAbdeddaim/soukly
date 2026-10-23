package com.soukly.social.post;

/** The member has no post of this listing on this network (SOC-06): {@code 404}, whoever owns the listing. */
public class PostNotFoundException extends RuntimeException {

    PostNotFoundException() {
        super("post not found", null, false, false);
    }
}
