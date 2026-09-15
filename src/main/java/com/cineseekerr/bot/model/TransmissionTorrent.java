package com.cineseekerr.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Minimal Transmission torrent representation used by the cleanup commands. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransmissionTorrent(
        String hashString,
        String name,
        Integer status,
        Double percentDone,
        Long totalSize,
        String downloadDir
) {
    private static final Set<Integer> COMPLETED_STATES = Set.of(0, 5, 6);

    /** Completed payloads only; active 100% downloads and placeholders are excluded. */
    public boolean isCompleted() {
        return hashString != null && !hashString.isBlank()
                && totalSizeOrZero() > 0
                && percentDone != null && percentDone >= 1.0
                && status != null && COMPLETED_STATES.contains(status);
    }

    /** A real Transmission payload that may be selected for explicit data deletion. */
    public boolean isRemovable() {
        return hashString != null && !hashString.isBlank()
                && totalSizeOrZero() > 0
                && percentDone != null && percentDone >= 0.0 && percentDone <= 1.0
                && status != null && status >= 0 && status <= 6;
    }

    public boolean isIncomplete() {
        return percentDone != null && percentDone < 1.0;
    }

    public int progressPercent() {
        if (percentDone == null) return 0;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, percentDone)) * 100.0);
    }

    public long totalSizeOrZero() {
        return totalSize == null ? 0 : Math.max(0, totalSize);
    }

    /** Syntactic containment check in Transmission's POSIX path namespace. */
    public boolean isUnderAnyDownloadRoot(List<Path> allowedRoots) {
        if (downloadDir == null || downloadDir.isBlank()) return false;
        final Path directory;
        try {
            directory = Path.of(downloadDir).normalize();
        } catch (RuntimeException invalidPath) {
            return false;
        }
        return directory.isAbsolute() && allowedRoots.stream().anyMatch(directory::startsWith);
    }
}
