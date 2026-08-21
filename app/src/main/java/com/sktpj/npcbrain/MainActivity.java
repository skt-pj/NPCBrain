package com.sktpj.npcbrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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
    private Button thinkButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiKeyStore = new SecureApiKeyStore(this);
        memoryStore = new MemoryStore(this);
        setContentView(buildUi());
    }

    private View buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(titleBox, titleParams);

        TextView title = new TextView(this);
        title.setText("NPCBrain");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(title);

        TextView model = new TextView(this);
        model.setText("GPT-5.6 Luna · reasoning MAX");
        model.setTextSize(12);
        titleBox.addView(model);

        Button menuButton = new Button(this);
        menuButton.setText("⋮");
        menuButton.setContentDescription("メニュー");
        menuButton.setOnClickListener(v -> showMenu(menuButton));
        header.addView(menuButton, new LinearLayout.LayoutParams(dp(56), dp(48)));
        root.addView(header);

        TextView description = new TextView(this);
        description.setText("状況や課題を入力すると、知覚・注意・記憶・予測・実行制御・価値判断・誤り監視・行動選択を別々に処理し、Global Workspaceで統合します。");
        description.setTextSize(14);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(10);
        root.addView(description, descriptionParams);

        inputView = new EditText(this);
        inputView.setHint("NPCが置かれた状況、考えさせたい課題、観察情報など");
        inputView.setGravity(Gravity.TOP | Gravity.START);
        inputView.setMinLines(5);
        inputView.setMaxLines(10);
        inputView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(12);
        root.addView(inputView, inputParams);

        thinkButton = new Button(this);
        thinkButton.setText("THINK");
        thinkButton.setOnClickListener(v -> startThinking());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        buttonParams.topMargin = dp(10);
        root.addView(thinkButton, buttonParams);

        statusView = new TextView(this);
        statusView.setText("待機中");
        statusView.setTextSize(12);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(8);
        root.addView(statusView, statusParams);

        outputView = new TextView(this);
        outputView.setText("結果はここに表示されます。");
        outputView.setTextSize(16);
        outputView.setTextIsSelectable(true);
        outputView.setPadding(0, dp(8), 0, dp(24));

        ScrollView scroll = new ScrollView(this);
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
        popup.getMenu().add("記憶を消去");
        popup.setOnMenuItemClickListener(item -> {
            if ("OpenAI APIキー".contentEquals(item.getTitle())) {
                showApiKeyDialog();
            } else if ("記憶を消去".contentEquals(item.getTitle())) {
                memoryStore.clear();
                Toast.makeText(this, "記憶を消去しました", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
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
                .setMessage("端末のAndroid Keystoreで暗号化して保存します。")
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

    private void showError(String message) {
        statusView.setText("エラー");
        outputView.setText(message);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
