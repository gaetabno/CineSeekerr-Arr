package com.cineseekerr.bot.client;

import com.cineseekerr.bot.config.CineSeekerrProperties;
import com.cineseekerr.bot.model.ArrMovie;
import com.cineseekerr.bot.model.ArrQueueItem;
import com.cineseekerr.bot.model.ArrQueueResponse;
import com.cineseekerr.bot.model.ArrRelease;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Radarr v3 manual-search API. This client never talks to Transmission or the media filesystem:
 * Radarr owns dispatch, import, rename, and library organization.
 */
@Component
public class RadarrClient {
    private final RestClient client;
    private final CineSeekerrProperties.Radarr settings;

    public RadarrClient(RestClient.Builder builder, CineSeekerrProperties properties) {
        settings = properties.radarr();
        client = builder.baseUrl(required(settings.baseUrl(), "RADARR_URL"))
                .defaultHeader("X-Api-Key", required(settings.apiKey(), "RADARR_API_KEY")).build();
    }

    public ArrMovie ensureMovie(int tmdbId) {
        try {
            List<ArrMovie> existing = client.get().uri(b -> b.path("/api/v3/movie")
                            .queryParam("tmdbId", tmdbId).build())
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            if (existing != null && !existing.isEmpty()) return existing.getFirst();

            List<Map<String, Object>> lookup = client.get().uri(b -> b.path("/api/v3/movie/lookup")
                            .queryParam("term", "tmdb:" + tmdbId).build())
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            if (lookup == null || lookup.isEmpty()) {
                throw new ApiClientException("Radarr non trova il film TMDB " + tmdbId);
            }

            // Reuse the full lookup payload: Radarr versions may require fields beyond the
            // stable minimum (for example titleSlug). Search stays disabled until the user grabs.
            Map<String, Object> movie = new LinkedHashMap<>(lookup.getFirst());
            movie.put("qualityProfileId", required(settings.qualityProfileId(), "RADARR_QUALITY_PROFILE_ID"));
            movie.put("rootFolderPath", required(settings.rootFolder(), "RADARR_ROOT_FOLDER"));
            movie.put("monitored", true);
            movie.put("addOptions", Map.of("searchForMovie", false));
            return client.post().uri("/api/v3/movie").body(movie).retrieve().body(ArrMovie.class);
        } catch (RestClientException e) {
            throw new ApiClientException("Radarr non è raggiungibile o ha rifiutato la richiesta", e);
        }
    }

    /** GET /release performs the manual search and stores grabbable reports in Radarr's short-lived cache. */
    public List<ArrRelease> manualSearch(int movieId) {
        try {
            List<ArrRelease> releases = client.get().uri(b -> b.path("/api/v3/release")
                            .queryParam("movieId", movieId).build())
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            return releases == null ? List.of() : releases;
        } catch (RestClientException e) {
            throw new ApiClientException("Ricerca manuale Radarr fallita", e);
        }
    }

    /** Returns active downloads and imports currently tracked by Radarr. */
    public List<ArrQueueItem> queue() {
        try {
            ArrQueueResponse page = client.get().uri(b -> b.path("/api/v3/queue")
                            .queryParam("page", 1).queryParam("pageSize", 100).build())
                    .retrieve().body(ArrQueueResponse.class);
            return page == null ? List.of() : page.recordsOrEmpty();
        } catch (RestClientException e) {
            throw new ApiClientException("Impossibile leggere la coda Radarr", e);
        }
    }

    /** Grabs a report previously returned by {@link #manualSearch(int)}. */
    public void grab(ArrRelease release) {
        if (release.guid() == null || release.indexerId() == null) {
            throw new ApiClientException("Release Radarr incompleta o cache scaduta: ripeti la ricerca");
        }
        try {
            client.post().uri("/api/v3/release")
                    .body(Map.of("guid", release.guid(), "indexerId", release.indexerId()))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new ApiClientException("Grab Radarr fallito: la cache della ricerca può essere scaduta", e);
        }
    }

    private static <T> T required(T value, String variable) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw new ApiClientException("Configura " + variable);
        }
        return value;
    }
}
