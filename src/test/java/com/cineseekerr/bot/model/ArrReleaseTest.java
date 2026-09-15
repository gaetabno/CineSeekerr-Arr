package com.cineseekerr.bot.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArrReleaseTest {
    @Test void rejectedArrResultsAreNeverEligibleForTelegramSelection() {
        assertThat(new ArrRelease("g", 7, "release", 1L, 2, "idx", true, List.of("quality"))).isNotNull();
        assertThat(new ArrRelease("g", 7, "release", 1L, 2, "idx", true, List.of("quality")).isApproved()).isFalse();
        assertThat(new ArrRelease("g", 7, "release", 1L, 2, "idx", false, List.of()).isApproved()).isTrue();
    }

    @Test void preservesTheCompleteArrReleaseForTheGrabRequest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String response = """
                {"guid":"release-guid","indexerId":12,"title":"Episode","downloadUrl":"https://example.invalid/download","protocol":"torrent","publishDate":"2026-09-15T12:00:00Z","seriesId":34}
                """;

        ArrRelease release = mapper.readValue(response, ArrRelease.class);
        String request = mapper.writeValueAsString(release);

        assertThat(request).contains("\"guid\":\"release-guid\"")
                .contains("\"indexerId\":12")
                .contains("\"downloadUrl\":\"https://example.invalid/download\"")
                .contains("\"protocol\":\"torrent\"")
                .contains("\"seriesId\":34")
                .doesNotContain("\"additionalProperties\"");
    }
}
