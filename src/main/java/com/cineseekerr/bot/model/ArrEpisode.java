package com.cineseekerr.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Minimal Sonarr episode identity used to request a manual release search for one episode. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArrEpisode(Integer id, Integer seasonNumber, Integer episodeNumber) { }
