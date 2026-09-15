package com.cineseekerr.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

/** Typed configuration for the Telegram, TMDB and *arr services. */
@ConfigurationProperties(prefix = "cineseekerr")
public record CineSeekerrProperties(Telegram telegram, Tmdb tmdb, Radarr radarr, Sonarr sonarr,
                                    Transmission transmission, String language) {
    public CineSeekerrProperties {
        if (language == null || language.isBlank()) language = "en";
        if (tmdb != null && (tmdb.language() == null || tmdb.language().isBlank())) {
            tmdb = new Tmdb(tmdb.apiKey(), tmdb.baseUrl(), "it".equals(language) ? "it-IT" : "en-US");
        }
    }

    public record Telegram(String botToken, Set<Long> allowedChatIds) {
        public Telegram { allowedChatIds = allowedChatIds == null ? Set.of() : Set.copyOf(allowedChatIds); }
    }
    public record Tmdb(String apiKey, String baseUrl, String language) { }

    /** Radarr must already have a download client and import/rename policy configured. */
    public record Radarr(String baseUrl, String apiKey, Integer qualityProfileId, String rootFolder) { }
    /** Sonarr must already have a download client and import/rename policy configured. */
    public record Sonarr(String baseUrl, String apiKey, Integer qualityProfileId, String rootFolder) { }
    /** Credentials are used only by explicitly confirmed Transmission cleanup commands. */
    public record Transmission(String rpcUrl, String username, String password,
                               List<String> allowedDownloadDirs) {
        public Transmission {
            allowedDownloadDirs = allowedDownloadDirs == null ? List.of() : List.copyOf(allowedDownloadDirs);
        }
    }
}
