package com.cineseekerr.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A download/import item returned by the Radarr or Sonarr v3 queue API. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArrQueueItem(
        String title,
        String status,
        String trackedDownloadStatus,
        Long size,
        Long sizeleft,
        String timeleft,
        String errorMessage
) {
    public long sizeOrZero() {
        return size == null ? 0 : size;
    }

    public long sizeLeftOrZero() {
        return sizeleft == null ? 0 : sizeleft;
    }
}
