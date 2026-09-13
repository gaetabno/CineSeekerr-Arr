package com.cineseekerr.bot.model;

/** An Arr-approved manual-search release plus parser-derived display fallback attributes. */
public record SearchResult(ArrRelease release, ParsedRelease parsed) {
    public int seeders() { return release.seedersOrZero(); }
}
