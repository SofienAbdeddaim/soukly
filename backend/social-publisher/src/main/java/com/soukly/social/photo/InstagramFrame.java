package com.soukly.social.photo;

/**
 * Where a photo goes in its Instagram version (SOC-05): {@value #WIDTH} px wide, its proportions brought into the
 * range Instagram accepts for a feed photo, from {@code 4:5} (portrait) to {@code 1.91:1} (landscape), by adding
 * margins, never by cropping. A photo taller than {@code 4:5} gets margins on its left and right, one wider than
 * {@code 1.91:1} above and below; the others fill the width without margin. The whole photo is always visible.
 *
 * @param imageWidth  of the photo once scaled
 * @param imageHeight of the photo once scaled
 * @param width       of the version, always {@value #WIDTH}
 * @param height      of the version
 * @param left        margin on the left (the one on the right is what remains)
 * @param top         margin above (the one below is what remains)
 */
record InstagramFrame(int imageWidth, int imageHeight, int width, int height, int left, int top) {

    static final int WIDTH = 1080;
    /** {@code 4:5}: 1080 × 1350. */
    static final int MAX_HEIGHT = 1350;
    /** The lowest height keeping {@code 1080 / height <= 1.91}: 1080 × 566 is 1.908:1. */
    static final int MIN_HEIGHT = (int) Math.ceil(WIDTH / 1.91);

    static InstagramFrame of(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid dimensions " + width + "x" + height);
        }
        // Taller than 4:5, compared without rounding: width / height < 1080 / 1350
        if ((long) width * MAX_HEIGHT < (long) height * WIDTH) {
            int imageWidth = Math.max(1, (int) Math.round((double) width * MAX_HEIGHT / height));
            return new InstagramFrame(imageWidth, MAX_HEIGHT, WIDTH, MAX_HEIGHT, (WIDTH - imageWidth) / 2, 0);
        }
        int imageHeight = Math.max(1, Math.min(MAX_HEIGHT, (int) Math.round((double) height * WIDTH / width)));
        // Wider than 1.91:1 (or within a pixel of it): margins above and below
        int frameHeight = Math.max(MIN_HEIGHT, imageHeight);
        return new InstagramFrame(WIDTH, imageHeight, WIDTH, frameHeight, 0, (frameHeight - imageHeight) / 2);
    }

    boolean hasMargins() {
        return imageWidth != width || imageHeight != height;
    }
}
