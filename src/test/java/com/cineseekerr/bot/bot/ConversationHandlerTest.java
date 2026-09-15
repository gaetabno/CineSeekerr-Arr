package com.cineseekerr.bot.bot;

import com.cineseekerr.bot.bot.state.ConversationState;
import com.cineseekerr.bot.bot.state.ConversationStep;
import com.cineseekerr.bot.bot.state.InMemoryConversationStateStore;
import com.cineseekerr.bot.bot.telegram.TelegramMessenger;
import com.cineseekerr.bot.client.RadarrClient;
import com.cineseekerr.bot.client.SonarrClient;
import com.cineseekerr.bot.client.TmdbClient;
import com.cineseekerr.bot.model.ArrRelease;
import com.cineseekerr.bot.model.Language;
import com.cineseekerr.bot.model.ParsedRelease;
import com.cineseekerr.bot.model.ReleaseSource;
import com.cineseekerr.bot.model.Resolution;
import com.cineseekerr.bot.model.SearchResult;
import com.cineseekerr.bot.model.VideoCodec;
import com.cineseekerr.bot.parser.ReleaseNameParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationHandlerTest {

    private static final long CHAT = 1L;

    private InMemoryConversationStateStore store;
    private ConversationHandler handler;
    private TorrentCleanupHandler cleanup;

    @BeforeEach
    void setUp() {
        Messages messages = mock(Messages.class);
        when(messages.get(anyString(), any(Object[].class))).thenAnswer(i -> i.getArgument(0));
        MessageFormatter formatter = mock(MessageFormatter.class);
        when(formatter.stepHeader(any())).thenReturn("hdr ");
        when(formatter.shortlistText(any())).thenReturn("shortlist");
        store = new InMemoryConversationStateStore();
        cleanup = mock(TorrentCleanupHandler.class);
        handler = new ConversationHandler(mock(TmdbClient.class), mock(RadarrClient.class),
                mock(SonarrClient.class), mock(ReleaseNameParser.class),
                mock(TelegramMessenger.class), store, messages, formatter, cleanup);
    }

    @Test
    void serializesEnumCallbacksUsingTheirStableNameNotDisplayLabel() {
        assertThat(ConversationHandler.callbackValue(Resolution.R1080P)).isEqualTo("R1080P");
        assertThat(ConversationHandler.callbackValue(Resolution.R2160P)).isEqualTo("R2160P");
        assertThat(ConversationHandler.callbackValue(VideoCodec.X265)).isEqualTo("X265");
    }

    @Test
    void dispatchesClearAndEliminaToDistinctSafeCleanupModes() {
        handler.onTextMessage(CHAT, "/clear");
        handler.onTextMessage(CHAT, "/elimina");

        verify(cleanup).preview(CHAT, CHAT, CleanupMode.CLEAR);
        verify(cleanup).preview(CHAT, CHAT, CleanupMode.DELETE_DATA);
    }

    /** Regression: "Qualsiasi" on a step after a previous "Qualsiasi" must advance, not loop. */
    @Test
    void anyOnSubtitlesAfterAnyOnAudioAdvancesToQuality() {
        ConversationState s = new ConversationState();
        // audio step already answered with "Qualsiasi": audioFilter stays null
        s.setStep(ConversationStep.AWAITING_SUBTITLES);
        s.setMessageId(42);
        s.setFiltered(twoReleases());
        store.save(CHAT, s);

        handler.onCallback(CHAT, 42, "cbq", "subs:any");

        assertThat(store.find(CHAT)).hasValueSatisfying(x -> {
            assertThat(x.step()).isEqualTo(ConversationStep.AWAITING_QUALITY);
            assertThat(x.filtered()).hasSize(2);
        });
    }

    /** Regression: a full chain of "Qualsiasi" answers must reach the release shortlist. */
    @Test
    void anyOnEveryFilterStepReachesTheShortlist() {
        ConversationState s = new ConversationState();
        s.setStep(ConversationStep.AWAITING_AUDIO);
        s.setMessageId(42);
        s.setFiltered(twoReleases());
        store.save(CHAT, s);

        handler.onCallback(CHAT, 42, "cbq", "audio:any");
        assertThat(store.find(CHAT)).hasValueSatisfying(x ->
                assertThat(x.step()).isEqualTo(ConversationStep.AWAITING_SUBTITLES));

        handler.onCallback(CHAT, 42, "cbq", "subs:any");
        assertThat(store.find(CHAT)).hasValueSatisfying(x ->
                assertThat(x.step()).isEqualTo(ConversationStep.AWAITING_QUALITY));

        handler.onCallback(CHAT, 42, "cbq", "quality:any");
        assertThat(store.find(CHAT)).hasValueSatisfying(x ->
                assertThat(x.step()).isEqualTo(ConversationStep.AWAITING_FORMAT));

        handler.onCallback(CHAT, 42, "cbq", "format:any");
        assertThat(store.find(CHAT)).hasValueSatisfying(x -> {
            assertThat(x.step()).isEqualTo(ConversationStep.AWAITING_RELEASE_CHOICE);
            assertThat(x.shortlist()).hasSize(2);
        });
    }

    /** A specific choice must still filter the results and move on. */
    @Test
    void specificQualityChoiceFiltersAndAdvances() {
        ConversationState s = new ConversationState();
        s.setStep(ConversationStep.AWAITING_QUALITY);
        s.setMessageId(42);
        s.setAudioFilter(Language.ITA);
        s.setSubtitleFilter(Language.ITA);
        s.setFiltered(twoReleases());
        store.save(CHAT, s);

        handler.onCallback(CHAT, 42, "cbq", "quality:R1080P");

        assertThat(store.find(CHAT)).hasValueSatisfying(x -> {
            assertThat(x.qualityFilter()).isEqualTo(Resolution.R1080P);
            assertThat(x.filtered()).hasSize(1);
            // only one codec remains, so the format step auto-skips to the shortlist
            assertThat(x.step()).isEqualTo(ConversationStep.AWAITING_RELEASE_CHOICE);
        });
    }

    private static List<SearchResult> twoReleases() {
        return List.of(
                result("Release.A.1080p", Resolution.R1080P, VideoCodec.X264, Set.of(Language.ITA)),
                result("Release.B.720p", Resolution.R720P, VideoCodec.X265, Set.of(Language.ENG)));
    }

    private static SearchResult result(String name, Resolution resolution, VideoCodec codec, Set<Language> subs) {
        ParsedRelease parsed = new ParsedRelease(name, resolution, Set.of(), subs,
                !subs.isEmpty(), codec, ReleaseSource.UNKNOWN, Set.of(), null);
        ArrRelease release = new ArrRelease("guid-" + name, 1, name, 1L, 10, "indexer", false, List.of());
        return new SearchResult(release, parsed);
    }
}
