package com.cineseekerr.bot.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ArrReleaseTest {
    @Test void rejectedArrResultsAreNeverEligibleForTelegramSelection() {
        assertThat(new ArrRelease("g", 7, "release", 1L, 2, "idx", true, List.of("quality"))).isNotNull();
        assertThat(new ArrRelease("g", 7, "release", 1L, 2, "idx", true, List.of("quality")).isApproved()).isFalse();
        assertThat(new ArrRelease("g", 7, "release", 1L, 2, "idx", false, List.of()).isApproved()).isTrue();
    }
}
