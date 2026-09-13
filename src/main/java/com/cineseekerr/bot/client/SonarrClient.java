package com.cineseekerr.bot.client;

import com.cineseekerr.bot.config.CineSeekerrProperties;
import com.cineseekerr.bot.model.ArrEpisode;
import com.cineseekerr.bot.model.ArrRelease;
import com.cineseekerr.bot.model.ArrSeries;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sonarr v3 manual-search API. Sonarr owns Transmission dispatch and all post-download work. */
@Component
public class SonarrClient {
    private final RestClient client;
    private final CineSeekerrProperties.Sonarr settings;

    public SonarrClient(RestClient.Builder builder, CineSeekerrProperties properties) {
        settings = properties.sonarr();
        client = builder.baseUrl(required(settings.baseUrl(), "SONARR_URL"))
                .defaultHeader("X-Api-Key", required(settings.apiKey(), "SONARR_API_KEY")).build();
    }

    public ArrSeries ensureSeries(int tmdbId) {
        try {
            List<Map<String, Object>> lookup = client.get().uri(b -> b.path("/api/v3/series/lookup")
                            .queryParam("term", "tmdb:" + tmdbId).build())
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            if (lookup == null || lookup.isEmpty()) {
                throw new ApiClientException("Sonarr non trova una serie per TMDB " + tmdbId);
            }
            Map<String, Object> series = new LinkedHashMap<>(lookup.getFirst());
            Object tvdbId = series.get("tvdbId");
            if (tvdbId == null) throw new ApiClientException("La serie Sonarr non ha un TVDB ID verificabile");

            List<ArrSeries> existing = client.get().uri(b -> b.path("/api/v3/series")
                            .queryParam("tvdbId", tvdbId).build())
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            if (existing != null && !existing.isEmpty()) return existing.getFirst();

            // Keep all lookup fields, particularly seasons and titleSlug, for compatibility.
            series.put("qualityProfileId", required(settings.qualityProfileId(), "SONARR_QUALITY_PROFILE_ID"));
            series.put("rootFolderPath", required(settings.rootFolder(), "SONARR_ROOT_FOLDER"));
            series.put("monitored", true);
            series.put("seasonFolder", true);
            series.put("addOptions", Map.of("searchForMissingEpisodes", false));
            return client.post().uri("/api/v3/series").body(series).retrieve().body(ArrSeries.class);
        } catch (RestClientException e) {
            throw new ApiClientException("Sonarr non è raggiungibile o ha rifiutato la richiesta", e);
        }
    }

    /** Performs a Sonarr manual search for one season pack or one resolved episode. */
    public List<ArrRelease> manualSearch(int seriesId, int season, Integer episodeNumber) {
        try {
            if (episodeNumber == null) {
                return releases(b -> b.path("/api/v3/release").queryParam("seriesId", seriesId)
                        .queryParam("seasonNumber", season).build());
            }
            List<ArrEpisode> episodes = client.get().uri(b -> b.path("/api/v3/episode")
                            .queryParam("seriesId", seriesId).build())
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            int episodeId = (episodes == null ? List.<ArrEpisode>of() : episodes).stream()
                    .filter(e -> Integer.valueOf(season).equals(e.seasonNumber())
                            && Integer.valueOf(episodeNumber).equals(e.episodeNumber()) && e.id() != null)
                    .findFirst().map(ArrEpisode::id)
                    .orElseThrow(() -> new ApiClientException("Sonarr non trova l'episodio S%02dE%02d".formatted(season, episodeNumber)));
            return releases(b -> b.path("/api/v3/release").queryParam("episodeId", episodeId).build());
        } catch (RestClientException e) {
            throw new ApiClientException("Ricerca manuale Sonarr fallita", e);
        }
    }

    public void grab(ArrRelease release) {
        if (release.guid() == null || release.indexerId() == null) {
            throw new ApiClientException("Release Sonarr incompleta o cache scaduta: ripeti la ricerca");
        }
        try {
            client.post().uri("/api/v3/release").body(Map.of("guid", release.guid(), "indexerId", release.indexerId()))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new ApiClientException("Grab Sonarr fallito: la cache della ricerca può essere scaduta", e);
        }
    }

    private List<ArrRelease> releases(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri) {
        List<ArrRelease> releases = client.get().uri(uri).retrieve().body(new ParameterizedTypeReference<>() { });
        return releases == null ? List.of() : releases;
    }

    private static <T> T required(T value, String variable) {
        if (value == null || (value instanceof String text && text.isBlank())) throw new ApiClientException("Configura " + variable);
        return value;
    }
}
