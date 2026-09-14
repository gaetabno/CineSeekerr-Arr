package com.cineseekerr.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Paginated response envelope shared by Radarr and Sonarr queue endpoints. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArrQueueResponse(List<ArrQueueItem> records) {
    public List<ArrQueueItem> recordsOrEmpty() {
        return records == null ? List.of() : records;
    }
}
