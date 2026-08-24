package com.sktpj.npcbrain;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class NpcInnerLifeUiBridge {
    private static final String TAG = "npcbrain_inner_life_status_v0424";
    private static final long REFRESH_MS = 1000L;

    private NpcInnerLifeUiBridge() {
    }

    static void install(NpcStatusActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null || content.getChildCount() == 0) return;
            View rootView = content.getChildAt(0);
            if (!(rootView instanceof LinearLayout)) return;
            LinearLayout root = (LinearLayout) rootView;
            if (findTagged(root) != null) return;
            if (root.getChildCount() < 4 || !(root.getChildAt(3) instanceof ScrollView)) return;
            ScrollView statusScroll = (ScrollView) root.getChildAt(3);
            if (statusScroll.getChildCount() == 0
                    || !(statusScroll.getChildAt(0) instanceof LinearLayout)) return;
            LinearLayout statusCard = (LinearLayout) statusScroll.getChildAt(0);

            LinearLayout surface = new LinearLayout(activity);
            surface.setTag(TAG);
            surface.setOrientation(LinearLayout.VERTICAL);
            surface.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
            surface.setBackground(panelBackground());
            surface.setClickable(true);
            surface.setFocusable(true);

            TextView title = new TextView(activity);
            title.setText("内面 / 思考ストリーム");
            title.setTextColor(Color.rgb(229, 240, 254));
            title.setTextSize(13);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            surface.addView(title);

            TextView summary = new TextView(activity);
            summary.setText("内面状態を読み込み中…");
            summary.setTextColor(Color.rgb(170, 195, 223));
            summary.setTextSize(11);
            summary.setMaxLines(4);
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            summaryParams.topMargin = dp(activity, 5);
            surface.addView(summary, summaryParams);

            Button details = new Button(activity);
            details.setText("思考ストリームを見る");
            details.setAllCaps(false);
            details.setTextSize(11);
            details.setTextColor(Color.rgb(220, 235, 252));
            details.setBackground(buttonBackground());
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 44));
            buttonParams.topMargin = dp(activity, 7);
            surface.addView(details, buttonParams);

            LinearLayout.LayoutParams surfaceParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            surfaceParams.topMargin = dp(activity, 8);
            surfaceParams.bottomMargin = dp(activity, 8);
            int insertIndex = Math.min(1, statusCard.getChildCount());
            statusCard.addView(surface, insertIndex, surfaceParams);

            View.OnClickListener open = v -> showDetails(activity);
            surface.setOnClickListener(open);
            details.setOnClickListener(open);

            Runnable refresh = new Runnable() {
                @Override
                public void run() {
                    if (surface.getParent() == null || activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    summary.setText(summaryFor(activity));
                    surface.setContentDescription("内面と思考ストリーム。" + summary.getText());
                    surface.postDelayed(this, REFRESH_MS);
                }
            };
            surface.post(refresh);
        } catch (Exception ignored) {
        }
    }

    private static String summaryFor(NpcStatusActivity activity) {
        String npcId = selectedNpcId(activity);
        android.content.Context storage = NpcContexts.storage(activity, npcId);
        CharacterStateStore character = new CharacterStateStore(storage);
        NpcInnerLifeStore store = new NpcInnerLifeStore(storage);
        if (character.isDead()) {
            NpcInnerLifeState existing = store.loadExisting();
            return existing == null
                    ? "死亡 · 内面状態の保存なし"
                    : "死亡 · 最後の状態\n" + NpcInnerLifeStore.compactSummary(existing);
        }
        double ext = character.traitPercent(CharacterStateStore.extraversionKey()) / 100.0;
        double neu = character.traitPercent(CharacterStateStore.neuroticismKey()) / 100.0;
        double open = character.traitPercent(CharacterStateStore.opennessKey()) / 100.0;
        return store.compactSummary(System.currentTimeMillis(), ext, neu, open);
    }

    private static void showDetails(NpcStatusActivity activity) {
        String npcId = selectedNpcId(activity);
        android.content.Context storage = NpcContexts.storage(activity, npcId);
        CharacterStateStore character = new CharacterStateStore(storage);
        NpcInnerLifeStore store = new NpcInnerLifeStore(storage);
        NpcInnerLifeState state;
        if (character.isDead()) {
            state = store.loadExisting();
        } else {
            state = store.loadOrCreate(
                    System.currentTimeMillis(),
                    character.traitPercent(CharacterStateStore.extraversionKey()) / 100.0,
                    character.traitPercent(CharacterStateStore.neuroticismKey()) / 100.0,
                    character.traitPercent(CharacterStateStore.opennessKey()) / 100.0);
        }

        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 18), dp(activity, 12), dp(activity, 18), dp(activity, 18));
        scroll.addView(body);

        if (state == null) {
            addText(activity, body, "まだ内面状態は記録されていません。", 14, true);
        } else {
            addText(activity, body, state.mood + " · " + state.focus, 15, true);
            addText(activity, body, "次にしたいこと: " + state.intention, 12, false);
            addText(activity, body, "", 6, false);
            addNeed(activity, body, "エネルギー", state.energy, false);
            addNeed(activity, body, "空腹", state.hunger, true);
            addNeed(activity, body, "誰かと話したさ", state.socialNeed, true);
            addNeed(activity, body, "退屈", state.boredom, true);
            addNeed(activity, body, "好奇心", state.curiosity, false);
            addNeed(activity, body, "安全への気がかり", state.safetyConcern, true);
        }

        addText(activity, body, "思考ストリーム", 14, true);
        List<NpcThoughtEntry> entries = store.latestThoughts(20);
        if (entries.isEmpty()) {
            addText(activity, body, "まだまとまった考えはありません。", 12, false);
        } else {
            SimpleDateFormat time = new SimpleDateFormat("HH:mm", Locale.JAPAN);
            for (NpcThoughtEntry entry : entries) {
                String label = time.format(new Date(entry.timeMs))
                        + "  " + sourceLabel(entry.source)
                        + "\n" + entry.text;
                TextView row = addText(activity, body, label, 12, false);
                row.setPadding(0, dp(activity, 7), 0, dp(activity, 7));
            }
        }

        new AlertDialog.Builder(activity)
                .setTitle(character.isDead() ? "最後の内面状態" : "内面 / 思考ストリーム")
                .setView(scroll)
                .setPositiveButton("閉じる", null)
                .show();
    }

    private static void addNeed(
            NpcStatusActivity activity,
            LinearLayout body,
            String label,
            double value,
            boolean highMeansNeed
    ) {
        int percent = (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0);
        String suffix = highMeansNeed ? "高いほど強い" : "";
        addText(activity, body,
                label + "  " + percent + "%" + (suffix.isEmpty() ? "" : "  · " + suffix),
                11,
                false);
    }

    private static TextView addText(
            NpcStatusActivity activity,
            LinearLayout body,
            String text,
            int size,
            boolean bold
    ) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(bold ? Color.rgb(226, 238, 252) : Color.rgb(169, 192, 218));
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.START);
        body.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private static String sourceLabel(String source) {
        if (NpcThoughtEntry.SOURCE_AMBIENT.equals(source)) return "AMBIENT";
        if (NpcThoughtEntry.SOURCE_REFLECTION.equals(source)) return "REFLECTION";
        return "LOCAL";
    }

    private static String selectedNpcId(NpcStatusActivity activity) {
        try {
            Field field = NpcStatusActivity.class.getDeclaredField("selectedNpcId");
            field.setAccessible(true);
            Object value = field.get(activity);
            if (value instanceof String) return NpcId.of((String) value).value();
        } catch (Exception ignored) {
        }
        return "npc1";
    }

    private static View findTagged(View view) {
        if (TAG.equals(view.getTag())) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findTagged(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(12, 22, 35));
        drawable.setStroke(1, Color.rgb(45, 70, 98));
        drawable.setCornerRadius(12f);
        return drawable;
    }

    private static GradientDrawable buttonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(29, 55, 85));
        drawable.setStroke(1, Color.rgb(55, 87, 120));
        drawable.setCornerRadius(10f);
        return drawable;
    }

    private static int dp(NpcStatusActivity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
