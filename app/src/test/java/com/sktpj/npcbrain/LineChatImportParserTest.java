package com.sktpj.npcbrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class LineChatImportParserTest {
    @Test
    public void parsesStandardDirectChatAndSuggestedTarget() {
        String text = "[LINE] 島田　恵未とのトーク履歴\n"
                + "保存日時：2026/08/22 10:44\n\n"
                + "2026/08/20(木)\n"
                + "10:10\t自分\tおはよう\n"
                + "10:11\t島田 恵未\tおはよー\n"
                + "10:12\t島田 恵未\t今日どうする？";

        LineChatImportParser.ParsedChat chat = LineChatImportParser.parse(
                "[LINE] 島田　恵未とのトーク.txt",
                text
        );

        assertEquals(3, chat.messages.size());
        assertEquals("自分", chat.messages.get(0).sender);
        assertEquals("おはよー", chat.messages.get(1).text);
        assertEquals(2, chat.speakerNames.size());
        assertEquals("島田 恵未", LineChatImportParser.resolveSuggestedSpeaker(chat));
    }

    @Test
    public void keepsContinuationLinesInsideMessage() {
        String text = "2026/08/20(木)\n"
                + "10:11\t島田 恵未\t1行目\n"
                + "2行目\n"
                + "3行目\n"
                + "10:12\t自分\t了解";

        LineChatImportParser.ParsedChat chat = LineChatImportParser.parse("chat.txt", text);

        assertEquals(2, chat.messages.size());
        assertEquals("1行目\n2行目\n3行目", chat.messages.get(0).text);
        assertEquals("了解", chat.messages.get(1).text);
    }

    @Test
    public void doesNotGuessTargetWhenTitleDoesNotMatchSpeakers() {
        String text = "10:11\tA\tone\n10:12\tB\ttwo";
        LineChatImportParser.ParsedChat chat = LineChatImportParser.parse(
                "[LINE] Cとのトーク.txt",
                text
        );

        assertEquals("", LineChatImportParser.resolveSuggestedSpeaker(chat));
    }

    @Test
    public void evenlySamplesLargeHistoryWithinBudget() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            text.append("10:10\tTarget\tm")
                    .append(String.format("%03d", i))
                    .append("-")
                    .append("x".repeat(120))
                    .append('\n');
        }
        LineChatImportParser.ParsedChat chat = LineChatImportParser.parse(
                "[LINE] Targetとのトーク.txt",
                text.toString()
        );

        List<String> sample = LineChatImportParser.analysisSample(chat, "Target", 4000);
        int total = 0;
        boolean hasBeginning = false;
        boolean hasMiddle = false;
        boolean hasEnd = false;
        for (String value : sample) {
            total += value.length();
            if (value.startsWith("m000-")) hasBeginning = true;
            if (value.startsWith("m099-") || value.startsWith("m100-")) hasMiddle = true;
            if (value.startsWith("m199-")) hasEnd = true;
        }

        assertFalse(sample.isEmpty());
        assertTrue(total <= 4000);
        assertTrue(hasBeginning);
        assertTrue(hasMiddle);
        assertTrue(hasEnd);
    }
}
