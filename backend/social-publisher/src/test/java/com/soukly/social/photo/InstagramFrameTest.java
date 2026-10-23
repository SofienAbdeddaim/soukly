package com.soukly.social.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Margins of the Instagram versions (SOC-05): never cropped, always between 4:5 and 1.91:1, 1080 px wide. */
class InstagramFrameTest {

    @Test
    void aPortraitTallerThanFourFifthsGetsMarginsOnBothSides() {
        // 9:16, a phone photo: 900 × 1600
        InstagramFrame frame = InstagramFrame.of(900, 1600);

        assertThat(frame).isEqualTo(new InstagramFrame(759, 1350, 1080, 1350, 160, 0));
        assertThat(frame.hasMargins()).isTrue();
    }

    @Test
    void aLandscapeWithinTheRangeFillsTheWidth() {
        // 4:3, 1600 × 1200
        InstagramFrame frame = InstagramFrame.of(1600, 1200);

        assertThat(frame).isEqualTo(new InstagramFrame(1080, 810, 1080, 810, 0, 0));
        assertThat(frame.hasMargins()).isFalse();
    }

    @Test
    void aPanoramaGetsMarginsAboveAndBelow() {
        // 3:1, 1600 × 533
        InstagramFrame frame = InstagramFrame.of(1600, 533);

        assertThat(frame).isEqualTo(new InstagramFrame(1080, 360, 1080, 566, 0, 103));
        assertThat(ratio(frame)).isLessThanOrEqualTo(1.91);
    }

    @Test
    void aSquareFillsTheVersion() {
        assertThat(InstagramFrame.of(1200, 1200)).isEqualTo(new InstagramFrame(1080, 1080, 1080, 1080, 0, 0));
    }

    @Test
    void theLimitsThemselvesNeedNoMargin() {
        assertThat(InstagramFrame.of(1080, 1350)).isEqualTo(new InstagramFrame(1080, 1350, 1080, 1350, 0, 0));
        assertThat(InstagramFrame.of(1600, 2000).hasMargins()).isFalse();
    }

    @Test
    void aSmallPhotoIsEnlargedToTheWidth() {
        assertThat(InstagramFrame.of(400, 300)).isEqualTo(new InstagramFrame(1080, 810, 1080, 810, 0, 0));
    }

    @ParameterizedTest
    @CsvSource({ "1600,533", "1600,100", "1600,837", "1600,838", "1599,838", "1600,1200", "1200,1200", "1280,1600",
        "1279,1600", "300,1600", "1600,300", "1, 1600", "1600, 1", "1527,800", "1528,800" })
    void everyVersionIsWithinTheRangeAndShowsTheWholePhoto(int width, int height) {
        InstagramFrame frame = InstagramFrame.of(width, height);

        assertThat(frame.width()).isEqualTo(1080);
        assertThat(ratio(frame)).isBetween(0.8, 1.91);
        assertThat(frame.imageWidth()).isBetween(1, frame.width());
        assertThat(frame.imageHeight()).isBetween(1, frame.height());
        assertThat(frame.left() + frame.imageWidth()).isLessThanOrEqualTo(frame.width());
        assertThat(frame.top() + frame.imageHeight()).isLessThanOrEqualTo(frame.height());
        // Margins on one axis only, centered: the proportions of the photo are kept within a pixel
        assertThat(frame.left() == 0 || frame.top() == 0).isTrue();
        assertThat(Math.abs((double) frame.imageWidth() / frame.imageHeight() - (double) width / height)
                / ((double) width / height)).isLessThan(0.01 + 1.0 / Math.min(frame.imageWidth(), frame.imageHeight()));
    }

    @Test
    void emptyDimensionsAreRefused() {
        assertThatThrownBy(() -> InstagramFrame.of(0, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    private static double ratio(InstagramFrame frame) {
        return (double) frame.width() / frame.height();
    }
}
