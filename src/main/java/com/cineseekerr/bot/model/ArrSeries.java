package com.cineseekerr.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArrSeries(Integer id, Integer tvdbId, Integer tmdbId, String title, Integer year) { }
