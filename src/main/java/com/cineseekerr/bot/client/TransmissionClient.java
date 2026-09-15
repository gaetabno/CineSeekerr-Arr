package com.cineseekerr.bot.client;

import com.cineseekerr.bot.config.CineSeekerrProperties;
import com.cineseekerr.bot.model.TransmissionTorrent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Narrow Transmission RPC client used only for confirmed cleanup of completed torrents. */
@Component
public class TransmissionClient {
    private static final String SESSION_HEADER = "X-Transmission-Session-Id";
    private static final List<String> TORRENT_FIELDS =
            List.of("hashString", "name", "status", "percentDone", "totalSize", "downloadDir");

    private final RestClient client;
    private final String rpcUrl;
    private final List<Path> allowedDownloadRoots;
    private final AtomicReference<String> sessionId = new AtomicReference<>();

    @Autowired
    public TransmissionClient(RestClient.Builder builder, CineSeekerrProperties properties) {
        this(builder, properties.transmission());
    }

    TransmissionClient(RestClient.Builder builder, CineSeekerrProperties.Transmission settings) {
        if (settings == null) throw new ApiClientException("Configura TRANSMISSION_RPC_URL");
        this.rpcUrl = required(settings.rpcUrl(), "TRANSMISSION_RPC_URL");
        String username = required(settings.username(), "TRANSMISSION_USERNAME");
        String password = required(settings.password(), "TRANSMISSION_PASSWORD");
        if (settings.allowedDownloadDirs().isEmpty()) {
            throw new ApiClientException("Configura TRANSMISSION_ALLOWED_DOWNLOAD_DIRS");
        }
        this.allowedDownloadRoots = settings.allowedDownloadDirs().stream()
                .map(path -> absoluteNormalized(path, "TRANSMISSION_ALLOWED_DOWNLOAD_DIRS"))
                .toList();
        this.client = builder.defaultHeaders(headers -> headers.setBasicAuth(username, password)).build();
    }

    /** Returns completed positive-size torrents; data deletion also requires an allowlisted path. */
    public List<TransmissionTorrent> cleanupCandidates(boolean deleteData) {
        TransmissionResponse response = rpc(Map.of(
                "method", "torrent-get",
                "arguments", Map.of("fields", TORRENT_FIELDS)));
        List<TransmissionTorrent> torrents = response.arguments() == null
                ? List.of() : response.arguments().torrentsOrEmpty();
        return torrents.stream()
                .filter(TransmissionTorrent::isCompleted)
                .filter(torrent -> !deleteData || torrent.isUnderAnyDownloadRoot(allowedDownloadRoots))
                .toList();
    }

    /**
     * Re-reads Transmission and removes only hashes that are both in the preview snapshot and
     * still eligible. This prevents active, moved, or newly arrived torrents from being swept.
     */
    public List<TransmissionTorrent> removeCompleted(Set<String> snapshotHashes, boolean deleteData) {
        if (snapshotHashes == null || snapshotHashes.isEmpty()) return List.of();
        List<TransmissionTorrent> eligible = cleanupCandidates(deleteData).stream()
                .filter(torrent -> snapshotHashes.contains(torrent.hashString()))
                .toList();
        if (eligible.isEmpty()) return List.of();

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("ids", eligible.stream().map(TransmissionTorrent::hashString).toList());
        arguments.put("delete-local-data", deleteData);
        rpc(Map.of("method", "torrent-remove", "arguments", arguments));
        return eligible;
    }

    private TransmissionResponse rpc(Map<String, Object> request) {
        try {
            return checked(invoke(request));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.CONFLICT) throw unavailable(e);
            String freshSessionId = e.getResponseHeaders().getFirst(SESSION_HEADER);
            if (freshSessionId == null || freshSessionId.isBlank()) {
                throw new ApiClientException("Transmission ha rifiutato la sessione RPC", e);
            }
            sessionId.set(freshSessionId);
            try {
                return checked(invoke(request));
            } catch (RestClientException retryFailure) {
                throw unavailable(retryFailure);
            }
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    private TransmissionResponse invoke(Map<String, Object> request) {
        return client.post().uri(rpcUrl)
                .headers(headers -> {
                    String current = sessionId.get();
                    if (current != null) headers.set(SESSION_HEADER, current);
                })
                .body(request)
                .retrieve()
                .body(TransmissionResponse.class);
    }

    private static TransmissionResponse checked(TransmissionResponse response) {
        if (response == null || !"success".equals(response.result())) {
            throw new ApiClientException("Transmission ha rifiutato la richiesta RPC");
        }
        return response;
    }

    private static ApiClientException unavailable(Exception cause) {
        return new ApiClientException("Transmission non è raggiungibile o ha rifiutato la richiesta", cause);
    }

    private static String required(String value, String variable) {
        if (value == null || value.isBlank()) throw new ApiClientException("Configura " + variable);
        return value;
    }

    private static Path absoluteNormalized(String value, String variable) {
        final Path path;
        try {
            path = Path.of(required(value, variable)).normalize();
        } catch (RuntimeException invalidPath) {
            throw new ApiClientException("Configura " + variable + " con percorsi assoluti validi", invalidPath);
        }
        if (!path.isAbsolute()) {
            throw new ApiClientException("Configura " + variable + " con percorsi assoluti validi");
        }
        return path;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TransmissionResponse(TransmissionArguments arguments, String result) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TransmissionArguments(List<TransmissionTorrent> torrents) {
        List<TransmissionTorrent> torrentsOrEmpty() {
            return torrents == null ? List.of() : torrents;
        }
    }
}
