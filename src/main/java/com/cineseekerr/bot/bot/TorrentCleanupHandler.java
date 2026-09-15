package com.cineseekerr.bot.bot;

import com.cineseekerr.bot.bot.telegram.TelegramMessenger;
import com.cineseekerr.bot.client.ApiClientException;
import com.cineseekerr.bot.client.TransmissionClient;
import com.cineseekerr.bot.model.TransmissionTorrent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.cineseekerr.bot.bot.MessageFormatter.esc;
import static com.cineseekerr.bot.bot.MessageFormatter.humanSize;
import static com.cineseekerr.bot.bot.MessageFormatter.truncate;

/** Chat-scoped selection and confirmation flow for completed Transmission torrents. */
@Component
public class TorrentCleanupHandler {
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(10);
    private static final String CALLBACK_PREFIX = "cleanup:";
    private static final int PAGE_SIZE = 8;

    private final TransmissionClient transmission;
    private final TelegramMessenger messenger;
    private final Messages messages;
    private final Clock clock;
    private final Map<Long, PendingCleanup> pending = new ConcurrentHashMap<>();

    @Autowired
    public TorrentCleanupHandler(TransmissionClient transmission, TelegramMessenger messenger, Messages messages) {
        this(transmission, messenger, messages, Clock.systemUTC());
    }

    TorrentCleanupHandler(TransmissionClient transmission, TelegramMessenger messenger,
                          Messages messages, Clock clock) {
        this.transmission = transmission;
        this.messenger = messenger;
        this.messages = messages;
        this.clock = clock;
    }

    public void preview(long chatId, CleanupMode mode) {
        preview(chatId, chatId, mode);
    }

    public void preview(long chatId, long userId, CleanupMode mode) {
        pending.remove(chatId);
        List<TransmissionTorrent> torrents = transmission.cleanupCandidates(mode.deleteData());
        if (torrents.isEmpty()) {
            messenger.sendHtml(chatId, messages.get("cleanup.none"), null);
            return;
        }
        if (mode == CleanupMode.DELETE_DATA) showDeleteSelection(chatId, userId, torrents);
        else showClearConfirmation(chatId, userId, torrents);
    }

    private void showDeleteSelection(long chatId, long userId, List<TransmissionTorrent> torrents) {
        Integer messageId = messenger.sendHtml(chatId, selectionText(torrents, 0), selectionKeyboard(torrents, 0));
        if (messageId != null) {
            pending.put(chatId, new PendingCleanup(userId, CleanupMode.DELETE_DATA, Stage.SELECT,
                    List.copyOf(torrents), messageId, clock.instant().plus(CONFIRMATION_TTL)));
        }
    }

    private void showClearConfirmation(long chatId, long userId, List<TransmissionTorrent> torrents) {
        long bytes = torrents.stream().mapToLong(TransmissionTorrent::totalSizeOrZero).sum();
        InlineKeyboardMarkup markup = keyboard(List.of(
                new InlineKeyboardRow(button(messages.get("cleanup.confirm.clear.button"),
                        CALLBACK_PREFIX + "confirm:" + CleanupMode.CLEAR.callbackValue())),
                new InlineKeyboardRow(button(messages.get("cancel.button"), CALLBACK_PREFIX + "cancel"))));
        Integer messageId = messenger.sendHtml(chatId, previewText(CleanupMode.CLEAR, torrents, bytes), markup);
        if (messageId != null) {
            pending.put(chatId, new PendingCleanup(userId, CleanupMode.CLEAR, Stage.CONFIRM,
                    List.copyOf(torrents), messageId, clock.instant().plus(CONFIRMATION_TTL)));
        }
    }

    public boolean onCallback(long chatId, int messageId, String data) {
        return onCallback(chatId, chatId, messageId, data);
    }

    /** Returns true when the callback belongs to this flow, even if it is stale or invalid. */
    public boolean onCallback(long chatId, long userId, int messageId, String data) {
        if (data == null || !data.startsWith(CALLBACK_PREFIX)) return false;
        PendingCleanup request = pending.get(chatId);

        // In group chats only the user who started the flow may select, cancel, or confirm.
        if (request != null && request.messageId() == messageId && request.userId() != userId) return true;

        if (data.equals(CALLBACK_PREFIX + "cancel")) {
            if (request != null && request.messageId() == messageId) pending.remove(chatId, request);
            messenger.editHtml(chatId, messageId, messages.get("cleanup.cancelled"), null);
            return true;
        }
        if (!isCurrent(request, userId, messageId)) {
            expire(chatId, messageId, request);
            return true;
        }
        if (data.startsWith(CALLBACK_PREFIX + "page:")) {
            return showPage(chatId, messageId, data, request);
        }
        if (data.startsWith(CALLBACK_PREFIX + "select:")) {
            return selectForDeletion(chatId, messageId, data, request);
        }

        CleanupMode callbackMode = modeFromConfirmation(data);
        if (callbackMode == null || request.stage() != Stage.CONFIRM || request.mode() != callbackMode
                || (callbackMode == CleanupMode.DELETE_DATA && request.torrents().size() != 1)) {
            expire(chatId, messageId, request);
            return true;
        }
        if (!pending.remove(chatId, request)) {
            messenger.editHtml(chatId, messageId, messages.get("cleanup.expired"), null);
            return true;
        }
        remove(chatId, messageId, request);
        return true;
    }

    private boolean showPage(long chatId, int messageId, String data, PendingCleanup request) {
        if (request.mode() != CleanupMode.DELETE_DATA || request.stage() != Stage.SELECT) {
            expire(chatId, messageId, request);
            return true;
        }
        Integer page = parseNonNegative(data, CALLBACK_PREFIX + "page:");
        int pageCount = pageCount(request.torrents().size());
        if (page == null || page >= pageCount) {
            expire(chatId, messageId, request);
            return true;
        }
        messenger.editHtml(chatId, messageId, selectionText(request.torrents(), page),
                selectionKeyboard(request.torrents(), page));
        return true;
    }

    private boolean selectForDeletion(long chatId, int messageId, String data, PendingCleanup request) {
        if (request.mode() != CleanupMode.DELETE_DATA || request.stage() != Stage.SELECT) {
            expire(chatId, messageId, request);
            return true;
        }
        Integer index = parseNonNegative(data, CALLBACK_PREFIX + "select:");
        if (index == null || index >= request.torrents().size()) {
            expire(chatId, messageId, request);
            return true;
        }

        TransmissionTorrent selected = request.torrents().get(index);
        PendingCleanup confirmation = new PendingCleanup(request.userId(), CleanupMode.DELETE_DATA, Stage.CONFIRM,
                List.of(selected), messageId, request.expiresAt());
        if (!pending.replace(chatId, request, confirmation)) {
            messenger.editHtml(chatId, messageId, messages.get("cleanup.expired"), null);
            return true;
        }

        String text = messages.get("cleanup.preview.delete.one",
                esc(truncate(selected.name(), 120)), humanSize(selected.totalSizeOrZero()));
        InlineKeyboardMarkup markup = keyboard(List.of(
                new InlineKeyboardRow(button(messages.get("cleanup.confirm.delete.button"),
                        CALLBACK_PREFIX + "confirm:" + CleanupMode.DELETE_DATA.callbackValue())),
                new InlineKeyboardRow(button(messages.get("cancel.button"), CALLBACK_PREFIX + "cancel"))));
        messenger.editHtml(chatId, messageId, text, markup);
        return true;
    }

    private void remove(long chatId, int messageId, PendingCleanup request) {
        Set<String> hashes = request.torrents().stream().map(TransmissionTorrent::hashString)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        try {
            List<TransmissionTorrent> removed = transmission.removeCompleted(Set.copyOf(hashes), request.mode().deleteData());
            if (removed.isEmpty()) {
                messenger.editHtml(chatId, messageId, messages.get("cleanup.none.remaining"), null);
            } else {
                long bytes = removed.stream().mapToLong(TransmissionTorrent::totalSizeOrZero).sum();
                String key = request.mode() == CleanupMode.DELETE_DATA ? "cleanup.done.delete" : "cleanup.done.clear";
                messenger.editHtml(chatId, messageId, messages.get(key, removed.size(), humanSize(bytes)), null);
            }
        } catch (ApiClientException e) {
            messenger.editHtml(chatId, messageId, messages.get("error.generic", esc(e.getMessage())), null);
        }
    }

    /** Revokes any pending cleanup when the user sends /annulla or /cancel. */
    public void cancelPending(long chatId) {
        pending.remove(chatId);
    }

    private boolean isCurrent(PendingCleanup request, long userId, int messageId) {
        return request != null && request.userId() == userId && request.messageId() == messageId
                && !clock.instant().isAfter(request.expiresAt());
    }

    private void expire(long chatId, int messageId, PendingCleanup request) {
        if (request != null && request.messageId() == messageId) pending.remove(chatId, request);
        messenger.editHtml(chatId, messageId, messages.get("cleanup.expired"), null);
    }

    private String selectionText(List<TransmissionTorrent> torrents, int page) {
        return messages.get("cleanup.select.delete", torrents.size(), page + 1, pageCount(torrents.size()));
    }

    private InlineKeyboardMarkup selectionKeyboard(List<TransmissionTorrent> torrents, int page) {
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, torrents.size());
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (int index = start; index < end; index++) {
            TransmissionTorrent torrent = torrents.get(index);
            String label = "🗑 " + truncate(torrent.name(), 45) + " (" + humanSize(torrent.totalSizeOrZero()) + ")";
            rows.add(new InlineKeyboardRow(button(label, CALLBACK_PREFIX + "select:" + index)));
        }
        List<InlineKeyboardButton> navigation = new ArrayList<>();
        if (page > 0) navigation.add(button("⬅️", CALLBACK_PREFIX + "page:" + (page - 1)));
        if (end < torrents.size()) navigation.add(button("➡️", CALLBACK_PREFIX + "page:" + (page + 1)));
        if (!navigation.isEmpty()) rows.add(new InlineKeyboardRow(navigation));
        rows.add(new InlineKeyboardRow(button(messages.get("cancel.button"), CALLBACK_PREFIX + "cancel")));
        return keyboard(rows);
    }

    private String previewText(CleanupMode mode, List<TransmissionTorrent> torrents, long bytes) {
        String key = mode == CleanupMode.DELETE_DATA ? "cleanup.preview.delete" : "cleanup.preview.clear";
        StringBuilder text = new StringBuilder(messages.get(key, torrents.size(), humanSize(bytes)));
        for (TransmissionTorrent torrent : torrents.stream().limit(10).toList()) {
            text.append("\n• ").append(esc(truncate(torrent.name(), 80)));
        }
        if (torrents.size() > 10) text.append("\n… +").append(torrents.size() - 10);
        return text.toString();
    }

    private static int pageCount(int size) {
        return (size + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private static Integer parseNonNegative(String data, String prefix) {
        try {
            int value = Integer.parseInt(data.substring(prefix.length()));
            return value >= 0 ? value : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static CleanupMode modeFromConfirmation(String data) {
        String prefix = CALLBACK_PREFIX + "confirm:";
        return data.startsWith(prefix) ? CleanupMode.fromCallback(data.substring(prefix.length())) : null;
    }

    private static InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private static InlineKeyboardMarkup keyboard(List<InlineKeyboardRow> rows) {
        return new InlineKeyboardMarkup(new ArrayList<>(rows));
    }

    private enum Stage { SELECT, CONFIRM }

    private record PendingCleanup(long userId, CleanupMode mode, Stage stage,
                                  List<TransmissionTorrent> torrents, int messageId, Instant expiresAt) { }
}
