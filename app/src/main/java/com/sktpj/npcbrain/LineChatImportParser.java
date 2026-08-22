package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LineChatImportParser {
    private static final Pattern MESSAGE_LINE = Pattern.compile("^(\\d{1,2}:\\d{2})\\t+([^\\t]+)(?:\\t(.*))?$");
    private static final Pattern TARGET_TITLE = Pattern.compile("^\\[LINE\\]\\s*(.+?)とのトーク(?:履歴)?(?:\\.txt)?$");
    private static final Pattern DATE_LINE = Pattern.compile("^\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2}.*$");

    static final class Message {
        final String time;
        final String sender;
        final String text;

        Message(String time, String sender, String text) {
            this.time = safe(time);
            this.sender = safe(sender);
            this.text = text == null ? "" : text;
        }
    }

    static final class ParsedChat {
        final String sourceTitle;
        final String suggestedTargetName;
        final List<String> speakerNames;
        final List<Message> messages;

        ParsedChat(
                String sourceTitle,
                String suggestedTargetName,
                List<String> speakerNames,
                List<Message> messages
        ) {
            this.sourceTitle = safe(sourceTitle);
            this.suggestedTargetName = safe(suggestedTargetName);
            this.speakerNames = speakerNames;
            this.messages = messages;
        }
    }

    private LineChatImportParser() {
    }

    static ParsedChat parse(String sourceTitle, String rawText) {
        String text = rawText == null ? "" : stripBom(rawText);
        List<Message> messages = new ArrayList<>();
        Set<String> speakers = new LinkedHashSet<>();

        String currentTime = null;
        String currentSender = null;
        StringBuilder currentText = null;

        String[] lines = text.split("\\r?\\n", -1);
        for (String rawLine : lines) {
            String line = stripBom(rawLine);
            Matcher messageMatcher = MESSAGE_LINE.matcher(line);
            if (messageMatcher.matches()) {
                flush(messages, speakers, currentTime, currentSender, currentText);
                currentTime = messageMatcher.group(1);
                currentSender = safe(messageMatcher.group(2));
                currentText = new StringBuilder(messageMatcher.group(3) == null ? "" : messageMatcher.group(3));
                continue;
            }

            String trimmed = line.trim();
            if (isStructuralLine(trimmed)) {
                flush(messages, speakers, currentTime, currentSender, currentText);
                currentTime = null;
                currentSender = null;
                currentText = null;
                continue;
            }

            if (currentText != null) {
                if (currentText.length() > 0) currentText.append('\n');
                currentText.append(line);
            }
        }
        flush(messages, speakers, currentTime, currentSender, currentText);

        String suggestedTarget = extractSuggestedTarget(sourceTitle, lines);
        return new ParsedChat(
                sourceTitle,
                suggestedTarget,
                new ArrayList<>(speakers),
                messages
        );
    }

    static String resolveSuggestedSpeaker(ParsedChat chat) {
        if (chat == null || chat.speakerNames.isEmpty()) return "";
        String target = normalizeName(chat.suggestedTargetName);
        if (!target.isEmpty()) {
            String matched = "";
            for (String speaker : chat.speakerNames) {
                if (!target.equals(normalizeName(speaker))) continue;
                if (!matched.isEmpty()) return "";
                matched = speaker;
            }
            if (!matched.isEmpty()) return matched;
        }
        if (chat.speakerNames.size() == 1) return chat.speakerNames.get(0);
        return "";
    }

    static List<String> analysisSample(ParsedChat chat, String speaker, int maxChars) {
        if (chat == null) throw new IllegalArgumentException("chat is required");
        if (speaker == null || speaker.trim().isEmpty()) {
            throw new IllegalArgumentException("speaker is required");
        }
        if (maxChars <= 0) throw new IllegalArgumentException("maxChars must be positive");

        List<String> utterances = new ArrayList<>();
        int totalChars = 0;
        for (Message message : chat.messages) {
            if (!speaker.equals(message.sender)) continue;
            String value = message.text == null ? "" : message.text.trim();
            if (value.isEmpty()) continue;
            utterances.add(value);
            totalChars += value.length();
        }
        if (utterances.isEmpty()) return utterances;
        if (totalChars <= maxChars) return utterances;

        int sampleCount = Math.min(
                utterances.size(),
                Math.max(1, Math.min(1000, maxChars / 32))
        );
        if (utterances.size() >= 3 && maxChars >= 96) {
            sampleCount = Math.max(3, sampleCount);
        }

        List<String> sampled = new ArrayList<>();
        int remainingBudget = maxChars;
        for (int i = 0; i < sampleCount; i++) {
            int index;
            if (sampleCount == 1) {
                index = utterances.size() / 2;
            } else {
                index = (int) Math.round(i * (utterances.size() - 1.0) / (sampleCount - 1.0));
            }
            String value = utterances.get(index);
            int remainingItems = sampleCount - i;
            int perItemBudget = Math.max(1, remainingBudget / remainingItems);
            String selected = value.length() <= perItemBudget
                    ? value
                    : value.substring(0, perItemBudget);
            sampled.add(selected);
            remainingBudget -= selected.length();
            if (remainingBudget <= 0) break;
        }
        return sampled;
    }

    static String normalizeName(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("[\\s\\u3000]+", "");
    }

    private static void flush(
            List<Message> messages,
            Set<String> speakers,
            String time,
            String sender,
            StringBuilder text
    ) {
        if (time == null || sender == null || sender.isEmpty() || text == null) return;
        Message message = new Message(time, sender, text.toString());
        messages.add(message);
        speakers.add(sender);
    }

    private static boolean isStructuralLine(String trimmed) {
        if (trimmed.isEmpty()) return true;
        if (trimmed.startsWith("[LINE]")) return true;
        if (trimmed.startsWith("保存日時") || trimmed.startsWith("Saved on")) return true;
        return DATE_LINE.matcher(trimmed).matches();
    }

    private static String extractSuggestedTarget(String sourceTitle, String[] lines) {
        String fromTitle = targetFromLine(sourceTitle);
        if (!fromTitle.isEmpty()) return fromTitle;
        int limit = Math.min(lines.length, 8);
        for (int i = 0; i < limit; i++) {
            String candidate = targetFromLine(lines[i]);
            if (!candidate.isEmpty()) return candidate;
        }
        return "";
    }

    private static String targetFromLine(String value) {
        String text = safe(value);
        Matcher matcher = TARGET_TITLE.matcher(text);
        if (!matcher.matches()) return "";
        return safe(matcher.group(1));
    }

    private static String stripBom(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        return value.charAt(0) == '\ufeff' ? value.substring(1) : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
