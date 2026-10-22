package com.soukly.social.caption;

import java.util.List;

/**
 * A post text, as {@code POST /social/caption-preview} answers it and as SOC-04 and SOC-05 post it.
 *
 * @param text     the whole text, hashtags included on its last line
 * @param hashtags the hashtags of the last line, with their {@code #}
 */
public record Caption(String text, List<String> hashtags) {
}
