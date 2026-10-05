package com.sktpj.npcbrain;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PopupUiSourceTest {
    @Test
    public void productionPopupCreationUsesSharedDarkThemeHelpers() throws Exception {
        Path root = Paths.get("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String source = read(path);
                            String name = path.getFileName().toString();
                            if (!"AppDialog.java".equals(name)) {
                                assertFalse(path + " uses raw AlertDialog.Builder",
                                        source.contains("new AlertDialog.Builder("));
                            }
                            if (!"AppPopupMenu.java".equals(name)) {
                                assertFalse(path + " uses raw PopupMenu",
                                        source.contains("new PopupMenu("));
                            }
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    });
        }

        String manifest = read(Paths.get("src/main/AndroidManifest.xml"));
        String styles = read(Paths.get("src/main/res/values/styles.xml"));
        assertTrue(manifest.contains("android:theme=\"@style/NpcBrainTheme\""));
        assertTrue(styles.contains("name=\"NpcBrainTheme\""));
        assertTrue(styles.contains("name=\"NpcBrainAlertDialogTheme\""));
        assertTrue(styles.contains("name=\"NpcBrainPopupTheme\""));
        assertTrue(styles.contains("android:windowBackground"));
        assertTrue(styles.contains("android:textColorPrimary"));
        assertTrue(styles.contains("android:textColorSecondary"));
        assertTrue(styles.contains("android:textColorAlertDialogListItem"));
    }

    @Test
    public void clefBackendPickerUsesVisibleRadioButtonsWithoutImplementationNotes() throws Exception {
        String source = read(Paths.get(
                "src/main/java/com/sktpj/npcbrain/SettingsActivity.java"));
        int start = source.indexOf("private void showClefExecutionBackendPicker");
        int end = source.indexOf("private void handleClefModelButton", start);
        assertTrue(start >= 0);
        assertTrue(end > start);
        String picker = source.substring(start, end);

        assertTrue(picker.contains("RadioGroup"));
        assertTrue(picker.contains("RadioButton"));
        assertTrue(picker.contains("\"GPU\""));
        assertTrue(picker.contains("\"CPU\""));
        assertTrue(picker.contains("getCheckedRadioButtonId()"));
        assertTrue(picker.contains(".setView("));
        assertFalse(picker.contains(".setSingleChoiceItems("));
        assertFalse(picker.contains(".setMessage("));
        assertFalse(picker.contains("自動切替"));
        assertFalse(picker.contains("失敗してもCPU"));
        assertFalse(picker.contains("GPUは試行しません"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
