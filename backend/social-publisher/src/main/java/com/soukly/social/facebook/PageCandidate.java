package com.soukly.social.facebook;

/** A Page offered in the choice among several Pages: never its token. */
public record PageCandidate(String pageId, String name, String category, String pictureUrl) {

    static PageCandidate of(ManagedPage page) {
        return new PageCandidate(page.id(), page.name(), page.category(), page.pictureUrl());
    }
}
