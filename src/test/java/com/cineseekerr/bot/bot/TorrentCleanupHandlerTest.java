package com.cineseekerr.bot.bot;

import com.cineseekerr.bot.bot.telegram.TelegramMessenger;
import com.cineseekerr.bot.client.TransmissionClient;
import com.cineseekerr.bot.model.TransmissionTorrent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TorrentCleanupHandlerTest {

    private TransmissionClient transmission;
    private TelegramMessenger messenger;
    private TorrentCleanupHandler handler;

    @BeforeEach
    void setUp() {
        transmission = mock(TransmissionClient.class);
        messenger = mock(TelegramMessenger.class);
        Messages messages = mock(Messages.class);
        when(messages.get(anyString(), any(Object[].class))).thenAnswer(i -> i.getArgument(0));
        when(messenger.sendHtml(anyLong(), anyString(), any())).thenReturn(42);
        handler = new TorrentCleanupHandler(transmission, messenger, messages,
                Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void clearShowsCollectivePreviewAndDoesNothingUntilExplicitConfirmation() {
        when(transmission.cleanupCandidates(false)).thenReturn(completed());

        handler.preview(1L, CleanupMode.CLEAR);

        verify(transmission, never()).removeEligible(anySet(), anyBoolean());
        ArgumentCaptor<InlineKeyboardMarkup> keyboard = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(messenger).sendHtml(eq(1L), anyString(), keyboard.capture());
        assertThat(callbacks(keyboard.getValue())).contains("cleanup:confirm:clear", "cleanup:cancel");
    }

    @Test
    void clearConfirmationRemovesAllSnapshotHashesAndKeepsDownloadedData() {
        when(transmission.cleanupCandidates(false)).thenReturn(completed());
        when(transmission.removeEligible(anySet(), eq(false))).thenReturn(completed());
        handler.preview(1L, CleanupMode.CLEAR);

        boolean handled = handler.onCallback(1L, 42, "cleanup:confirm:clear");

        assertThat(handled).isTrue();
        verify(transmission).removeEligible(Set.of("HASH-A", "HASH-B"), false);
    }

    @Test
    void eliminaFirstShowsResourceSelectionAndCannotDeleteAllAtOnce() {
        when(transmission.cleanupCandidates(true)).thenReturn(completed());

        handler.preview(1L, CleanupMode.DELETE_DATA);

        ArgumentCaptor<InlineKeyboardMarkup> keyboard = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(messenger).sendHtml(eq(1L), anyString(), keyboard.capture());
        assertThat(callbacks(keyboard.getValue()))
                .contains("cleanup:select:0", "cleanup:select:1", "cleanup:cancel")
                .doesNotContain("cleanup:confirm:delete");
        verify(transmission, never()).removeEligible(anySet(), anyBoolean());
    }

    @Test
    void eliminaShowsIncompleteTorrentWithProgressAndRequiresSeparateConfirmation() {
        TransmissionTorrent partial = new TransmissionTorrent("PARTIAL", "Series downloading", 4,
                0.943, 2L << 30, "/downloads/complete/tv-sonarr");
        when(transmission.cleanupCandidates(true)).thenReturn(List.of(partial));
        when(transmission.removeEligible(Set.of("PARTIAL"), true)).thenReturn(List.of(partial));

        handler.preview(1L, CleanupMode.DELETE_DATA);

        ArgumentCaptor<InlineKeyboardMarkup> selection = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(messenger).sendHtml(eq(1L), anyString(), selection.capture());
        assertThat(buttonTexts(selection.getValue())).anyMatch(text -> text.contains("94%"));
        verify(transmission, never()).removeEligible(anySet(), anyBoolean());

        handler.onCallback(1L, 42, "cleanup:select:0");
        ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InlineKeyboardMarkup> confirmation = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(messenger).editHtml(eq(1L), eq(42), warning.capture(), confirmation.capture());
        assertThat(warning.getValue()).isEqualTo("cleanup.preview.delete.one.incomplete");
        assertThat(buttonTexts(confirmation.getValue()))
                .contains("cleanup.confirm.delete.incomplete.button");
        verify(transmission, never()).removeEligible(anySet(), anyBoolean());
        handler.onCallback(1L, 42, "cleanup:confirm:delete");

        verify(transmission).removeEligible(Set.of("PARTIAL"), true);
    }

    @Test
    void eliminaDeletesOnlyTheSelectedResourceAfterSeparateConfirmation() {
        when(transmission.cleanupCandidates(true)).thenReturn(completed());
        when(transmission.removeEligible(Set.of("HASH-B"), true)).thenReturn(List.of(completed().get(1)));
        handler.preview(1L, CleanupMode.DELETE_DATA);

        handler.onCallback(1L, 42, "cleanup:select:1");
        verify(transmission, never()).removeEligible(anySet(), anyBoolean());

        ArgumentCaptor<InlineKeyboardMarkup> keyboard = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(messenger).editHtml(eq(1L), eq(42), anyString(), keyboard.capture());
        assertThat(callbacks(keyboard.getValue())).contains("cleanup:confirm:delete", "cleanup:cancel");

        handler.onCallback(1L, 42, "cleanup:confirm:delete");

        verify(transmission).removeEligible(Set.of("HASH-B"), true);
    }

    @Test
    void invalidSelectionCannotTriggerDeletion() {
        when(transmission.cleanupCandidates(true)).thenReturn(completed());
        handler.preview(1L, CleanupMode.DELETE_DATA);

        handler.onCallback(1L, 42, "cleanup:select:99");
        handler.onCallback(1L, 42, "cleanup:confirm:delete");

        verify(transmission, never()).removeEligible(anySet(), anyBoolean());
    }

    @Test
    void alteredOrMismatchedCallbackCannotTriggerDeletion() {
        when(transmission.cleanupCandidates(true)).thenReturn(completed());
        handler.preview(1L, CleanupMode.DELETE_DATA);

        handler.onCallback(1L, 42, "cleanup:confirm:clear");

        verify(transmission, never()).removeEligible(anySet(), anyBoolean());
    }

    @Test
    void cancelDropsPendingRequestWithoutDeletingAnything() {
        when(transmission.cleanupCandidates(true)).thenReturn(completed());
        handler.preview(1L, CleanupMode.DELETE_DATA);

        handler.onCallback(1L, 42, "cleanup:cancel");
        handler.onCallback(1L, 42, "cleanup:select:0");

        verify(transmission, never()).removeEligible(anySet(), anyBoolean());
    }

    @Test
    void commandCancelRevokesPendingDestructiveSelection() {
        when(transmission.cleanupCandidates(true)).thenReturn(completed());
        handler.preview(1L, CleanupMode.DELETE_DATA);

        handler.cancelPending(1L);
        handler.onCallback(1L, 42, "cleanup:select:0");

        verify(transmission, never()).removeEligible(anySet(), anyBoolean());
    }

    @Test
    void cancelOnOldPreviewDoesNotInvalidateNewerPreview() {
        when(transmission.cleanupCandidates(true)).thenReturn(completed());
        when(messenger.sendHtml(anyLong(), anyString(), any())).thenReturn(41, 42);
        when(transmission.removeEligible(Set.of("HASH-A"), true)).thenReturn(List.of(completed().getFirst()));
        handler.preview(1L, CleanupMode.DELETE_DATA);
        handler.preview(1L, CleanupMode.DELETE_DATA);

        handler.onCallback(1L, 41, "cleanup:cancel");
        handler.onCallback(1L, 42, "cleanup:select:0");
        handler.onCallback(1L, 42, "cleanup:confirm:delete");

        verify(transmission).removeEligible(Set.of("HASH-A"), true);
    }

    @Test
    void doubleConfirmationCanRemoveSelectedResourceOnlyOnce() {
        when(transmission.cleanupCandidates(true)).thenReturn(completed());
        when(transmission.removeEligible(Set.of("HASH-A"), true)).thenReturn(List.of(completed().getFirst()));
        handler.preview(1L, CleanupMode.DELETE_DATA);
        handler.onCallback(1L, 42, "cleanup:select:0");

        handler.onCallback(1L, 42, "cleanup:confirm:delete");
        handler.onCallback(1L, 42, "cleanup:confirm:delete");

        verify(transmission, times(1)).removeEligible(Set.of("HASH-A"), true);
    }

    @Test
    void paginatesDeleteCandidatesAndSelectsFromTheRequestedPage() {
        List<TransmissionTorrent> torrents = manyCompleted(10);
        when(transmission.cleanupCandidates(true)).thenReturn(torrents);
        when(transmission.removeEligible(Set.of("HASH-9"), true)).thenReturn(List.of(torrents.get(9)));
        handler.preview(1L, CleanupMode.DELETE_DATA);

        ArgumentCaptor<InlineKeyboardMarkup> firstPage = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(messenger).sendHtml(eq(1L), anyString(), firstPage.capture());
        assertThat(callbacks(firstPage.getValue()))
                .contains("cleanup:select:0", "cleanup:select:7", "cleanup:page:1")
                .doesNotContain("cleanup:select:8");

        handler.onCallback(1L, 42, "cleanup:page:1");
        handler.onCallback(1L, 42, "cleanup:select:9");
        handler.onCallback(1L, 42, "cleanup:confirm:delete");

        verify(transmission).removeEligible(Set.of("HASH-9"), true);
    }

    @Test
    void stalePageCallbackCannotInvalidateANewerPreview() {
        when(transmission.cleanupCandidates(true)).thenReturn(manyCompleted(10));
        when(messenger.sendHtml(anyLong(), anyString(), any())).thenReturn(41, 42);
        handler.preview(1L, CleanupMode.DELETE_DATA);
        handler.preview(1L, CleanupMode.DELETE_DATA);

        handler.onCallback(1L, 41, "cleanup:page:1");
        handler.onCallback(1L, 42, "cleanup:select:0");

        ArgumentCaptor<InlineKeyboardMarkup> edits = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(messenger, atLeastOnce()).editHtml(eq(1L), eq(42), anyString(), edits.capture());
        assertThat(callbacks(edits.getValue())).contains("cleanup:confirm:delete");
    }

    @Test
    void anotherGroupMemberCannotSelectOrConfirmDestructiveCleanup() {
        when(transmission.cleanupCandidates(true)).thenReturn(completed());
        handler.preview(-100L, 111L, CleanupMode.DELETE_DATA);

        handler.onCallback(-100L, 222L, 42, "cleanup:select:0");
        handler.onCallback(-100L, 222L, 42, "cleanup:confirm:delete");

        verify(transmission, never()).removeEligible(anySet(), anyBoolean());
        verify(messenger, never()).editHtml(eq(-100L), eq(42), anyString(), any());
    }

    private static List<TransmissionTorrent> completed() {
        return List.of(
                new TransmissionTorrent("HASH-A", "Movie A", 6, 1.0, 1L << 30,
                        "/downloads/complete/movies"),
                new TransmissionTorrent("HASH-B", "Series B", 6, 1.0, 2L << 30,
                        "/downloads/complete/tv-sonarr"));
    }

    private static List<TransmissionTorrent> manyCompleted(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new TransmissionTorrent("HASH-" + i, "Resource " + i, 6, 1.0,
                        (long) (i + 1) << 20, "/downloads/complete/resource-" + i))
                .toList();
    }

    private static List<String> callbacks(InlineKeyboardMarkup markup) {
        return markup.getKeyboard().stream()
                .flatMap(row -> row.stream())
                .map(button -> button.getCallbackData())
                .toList();
    }

    private static List<String> buttonTexts(InlineKeyboardMarkup markup) {
        return markup.getKeyboard().stream()
                .flatMap(row -> row.stream())
                .map(button -> button.getText())
                .toList();
    }
}
