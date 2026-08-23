package com.sktpj.npcbrain;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class LineChatImportContextTest {
    @Test
    public void contextSampleIncludesBothSpeakers() {
        String raw = "[LINE] Aとのトーク履歴\n"
                + "2026/08/20(木)\n"
                + "10:00\tA\t大学の研究室以来だね\n"
                + "10:01\tUser\tもう卒業して3年か\n"
                + "10:02\tA\t今度また同期で集まろう\n";
        LineChatImportParser.ParsedChat chat = LineChatImportParser.parse("[LINE] Aとのトーク履歴.txt", raw);
        List<String> context = LineChatImportParser.contextSample(chat, 1000);
        String joined = String.join("\n", context);
        assertTrue(joined.contains("A:"));
        assertTrue(joined.contains("User:"));
        assertTrue(joined.contains("研究室"));
    }
}
