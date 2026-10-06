package com.leetvise.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatingTest {

    @Test
    void firstRevisionUsesMinimumIntervals() {
        assertThat(Rating.AGAIN.nextInterval(0)).isEqualTo(1);
        assertThat(Rating.HARD.nextInterval(0)).isEqualTo(3);
        assertThat(Rating.GOOD.nextInterval(0)).isEqualTo(7);
        assertThat(Rating.EASY.nextInterval(0)).isEqualTo(14);
    }

    @Test
    void intervalsGrowAndAreCapped() {
        assertThat(Rating.GOOD.nextInterval(7)).isEqualTo(14);
        assertThat(Rating.EASY.nextInterval(14)).isEqualTo(42);
        assertThat(Rating.HARD.nextInterval(10)).isEqualTo(12);
        assertThat(Rating.EASY.nextInterval(100)).isEqualTo(Rating.MAX_INTERVAL_DAYS);
        assertThat(Rating.AGAIN.nextInterval(60)).isEqualTo(1);
    }

    @Test
    void parsesCaseInsensitively() {
        assertThat(Rating.parse(" Good ")).isEqualTo(Rating.GOOD);
        assertThatThrownBy(() -> Rating.parse("meh")).isInstanceOf(IllegalArgumentException.class);
    }
}
