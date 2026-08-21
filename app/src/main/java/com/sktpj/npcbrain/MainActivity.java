package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private SecureApiKeyStore apiKeyStore;
    private MemoryStore memoryStore;
    private EditText inputView;
    private TextView statusView;
    private TextView outputView;
    private TextView memoryStatusView;
    private Button thinkButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureEdgeToEdgeWindow();
        apiKeyStore = new SecureApiKeyStore(this);
        memoryStore = new MemoryStore(this);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250, 250, 252));
        applySafeInsets(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(64));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(titleBox, titleParams);

        TextView title = new TextView(this);
        title.setText("NPCBrain");
        title.setTextColor(Color.rgb(24, 24, 28));
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(title);

        memoryStatusView = new TextView(this);
        memoryStatusView.setTextColor(Color.rgb(92, 92, 102));
        memoryStatusView.setTextSize(12);
        titleBox.addView(memoryStatusView);
        refreshMemoryStatus();

        Button menuButton = new Button(this);
        menuButton.setText("⋮");
        menuButton.setTextSize(24);
        menuButton.setMinWidth(0);
        menuButton.setMinimumWidth(0);
        menuButton.setPadding(0, 0, 0, 0);
        menuButton.setContentDescription("設定と記憶メニュー");
        menuButton.setOnClickListener(v -> showMenu(menuButton));
        header.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        TextView model = new TextView(this);
        model.setText("GPT-5.6 Luna · reasoning MAX");
        model.setTextColor(Color.rgb(64, 64, 74));
        model.setTextSize(13);
        LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        modelParams.topMargin = dp(2);
        root.addView(model, modelParams);

        TextView description = new TextView(this);
        description.setText("知覚・注意・作業記憶・エピソード記憶・意味記憶・予測・実行制御・価値判断・誤り監視・行動選択を分担し、Global Workspaceで統合します。");
        description.setTextColor(Color.rgb(74, 74, 84));
        description.setTextSize(14);
        description.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(8);
        root.addView(description, descriptionParams);

        inputView = new EditText(this);
        inputView.setHint("NPCが置かれた状況、考えさせたい課題、観察情報など");
        inputView.setTextSize(16);
        inputView.setGravity(Gravity.TOP | Gravity.START);
        inputView.setMinLines(5);
        inputView.setMaxLines(9);
        inputView.setPadding(dp(12), dp(12), dp(12), dp(12));
        inputView.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(12);
        root.addView(inputView, inputParams);

        thinkButton = new Button(this);
        thinkButton.setText("考える");
        thinkButton.setTextSize(16);
        thinkButton.setTypeface(Typeface.DEFAULT_BOLD);
        thinkButton.setContentDescription("入力内容について考える");
        thinkButton.setOnClickListener(v -> startThinking());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        buttonParams.topMargin = dp(10);
        root.addView(thinkButton, buttonParams);

        statusView = new TextView(this);
        statusView.setText("待機中");
        statusView.setTextColor(Color.rgb(92, 92, 102));
        statusView.setTextSize(12);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(8);
        root.addView(statusView, statusParams);

        outputView = new TextView(this);
        outputView.setText("結果はここに表示されます。");
        outputView.setTextColor(Color.rgb(32, 32, 38));
        outputView.setTextSize(16);
        outputView.setLineSpacing(0f, 1.18f);
        outputView.setTextIsSelectable(true);
        outputView.setPadding(dp(2), dp(10), dp(2), dp(24));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(outputView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(4);
        root.addView(scroll, scrollParams);

        return root;
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("OpenAI APIキー");
        popup.getMenu().add("記憶を見る");
        popup.getMenu().add("記憶を消去");
        popup.setOnMenuItemClickListener(item -> {
            if ("OpenAI APIキー".contentEquals(item.getTitle())) {
                showApiKeyDialog();
            } else if ("記憶を見る".contentEquals(item.getTitle())) {
                showMemoryDialog();
            } else if ("記憶を消去".contentEquals(item.getTitle())) {
                confirmClearMemory();
            }
            return true;
        });
        popup.show();
    }

    private void showMemoryDialog() {
        TextView memoryText = new TextView(this);
        memoryText.setText(memoryStore.preview());
        memoryText.setTextSize(15);
        memoryText.setTextColor(Color.rgb(32, 32, 38));
        memoryText.setTextIsSelectable(true);
        int pad = dp(20);
        memoryText.setPadding(pad, dp(8), pad, dp(16));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(memoryText, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("長期記憶")
                .setMessage("エピソード記憶は経験、意味記憶は繰り返し使える知識として保存します。")
                .setView(scroll)
                .setPositiveButton("閉じる", null)
                .show();
    }

    private void confirmClearMemory() {
        new AlertDialog.Builder(this)
                .setTitle("記憶を消去")
                .setMessage("エピソード記憶と意味記憶をすべて削除します。APIキーは削除しません。")
                .setPositiveButton("消去", (dialog, which) -> {
                    memoryStore.clear();
                    refreshMemoryStatus();
                    Toast.makeText(this, "長期記憶を消去しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void refreshMemoryStatus() {
        if (memoryStatusView == null || memoryStore == null) return;
        MemoryStore.Stats stats = memoryStore.stats();
        memoryStatusView.setText("記憶  E " + stats.episodes + " · S " + stats.semantics);
    }

    private void showApiKeyDialog() {
        EditText keyInput = new EditText(this);
        keyInput.setHint("sk-...");
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, 0, pad, 0);
        box.addView(keyInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("OpenAI APIキー")
                .setMessage("端末のAndroid Keystoreで暗号化して保存します。長期記憶にはAPIキーを保存しません。")
                .setView(box)
                .setPositiveButton("保存", (dialog, which) -> {
                    String key = keyInput.getText().toString().trim();
                    if (key.isEmpty()) {
                        Toast.makeText(this, "APIキーが空です", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        apiKeyStore.save(key);
                        Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show();
                    } catch (Exception error) {
                        Toast.makeText(this, "保存失敗: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("削除", (dialog, which) -> {
                    apiKeyStore.clear();
                    Toast.makeText(this, "APIキーを削除しました", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void startThinking() {
        String input = inputView.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "入力してください", Toast.LENGTH_SHORT).show();
            return;
        }

        final String apiKey;
        try {
            apiKey = apiKeyStore.load();
        } catch (Exception error) {
            showError("APIキーを読み出せません: " + error.getMessage());
            return;
        }
        if (apiKey.isEmpty()) {
            showApiKeyDialog();
            return;
        }

        thinkButton.setEnabled(false);
        inputView.setEnabled(false);
        outputView.setText("");
        statusView.setText("思考開始");

        new Thread(() -> {
            try {
                BrainEngine engine = new BrainEngine(new OpenAiClient(apiKey), memoryStore);
                String result = engine.think(input, (stage, current, total) -> runOnUiThread(
                        () -> statusView.setText(current + "/" + total + "  " + stage)));
                runOnUiThread(() -> {
                    outputView.setText(result);
                    statusView.setText("完了");
                    refreshMemoryStatus();
                    thinkButton.setEnabled(true);
                    inputView.setEnabled(true);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    showError(error.getMessage() == null ? error.toString() : error.getMessage());
                    thinkButton.setEnabled(true);
                    inputView.setEnabled(true);
                });
            }
        }, "npcbrain-think").start();
    }

    private void configureEdgeToEdgeWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
    }

    private void applySafeInsets(View root) {
        final int horizontal = dp(16);
        final int topBase = dp(6);
        final int bottomBase = dp(10);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();

            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    top = Math.max(top, cutout.getSafeInsetTop());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
            }

            view.setPadding(horizontal + left, topBase + top, horizontal + right, bottomBase + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void showError(String message) {
        statusView.setText("エラー");
        outputView.setText(message);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
