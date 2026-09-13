package com.cineseekerr.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Release returned by Radarr/Sonarr's manual-search cache. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArrRelease(String guid, Integer indexerId, String title, Long size, Integer seeders,
                         String indexer, Boolean rejected, List<String> rejections) {
    public boolean isApproved() { return !Boolean.TRUE.equals(rejected) && (rejections == null || rejections.isEmpty()); }
    public long sizeOrZero() { return size == null ? 0 : size; }
    public int seedersOrZero() { return seeders == null ? 0 : seeders; }
}
