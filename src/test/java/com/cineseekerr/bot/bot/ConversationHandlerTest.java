package com.cineseekerr.bot.bot;

import com.cineseekerr.bot.model.Resolution;
import com.cineseekerr.bot.model.VideoCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationHandlerTest {

    @Test
    void serializesEnumCallbacksUsingTheirStableNameNotDisplayLabel() {
        assertThat(ConversationHandler.callbackValue(Resolution.R1080P)).isEqualTo("R1080P");
        assertThat(ConversationHandler.callbackValue(Resolution.R2160P)).isEqualTo("R2160P");
        assertThat(ConversationHandler.callbackValue(VideoCodec.X265)).isEqualTo("X265");
    }
}
