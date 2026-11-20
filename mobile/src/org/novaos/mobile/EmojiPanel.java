package org.novaos.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Emoji keyboard of Nova Chat: recently used, then the categories. */
final class EmojiPanel extends LinearLayout {
    interface Listener {
        void onEmoji(String emoji);

        void onBackspace();
    }

    private final Listener listener;
    private final List<TextView> tabs = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private final GridView grid;
    private List<String> shown = new ArrayList<>();
    private int tab;

    EmojiPanel(Context c, Listener l) {
        super(c);
        listener = l;
        setOrientation(VERTICAL);
        setBackgroundColor(0xF2141831);
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        List<String> labels = new ArrayList<>();
        labels.add("🕘");
        labels.addAll(Arrays.asList(Emoji.TABS));
        for (int i = 0; i < labels.size(); i++) {
            TextView t = new TextView(c);
            t.setText(labels.get(i));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(Color.WHITE);
            int index = i;
            t.setOnClickListener(v -> select(index));
            row.addView(t, new LinearLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 44)));
            tabs.add(t);
        }
        hs.addView(row);
        top.addView(hs, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton back = new ImageButton(c);
        back.setImageResource(R.drawable.ic_backspace);
        back.setBackgroundResource(R.drawable.round_bg);
        back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(c, 10);
        back.setPadding(p, p, p, p);
        back.setContentDescription(c.getString(R.string.emoji_delete));
        back.setOnClickListener(v -> listener.onBackspace());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 42));
        bl.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 8), 0);
        top.addView(back, bl);
        addView(top);
        grid = new GridView(c);
        grid.setColumnWidth(Ui.dp(c, 46));
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalScrollBarEnabled(false);
        grid.setSelector(android.R.color.transparent);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> {
            String e = shown.get(pos);
            remember(e);
            listener.onEmoji(e);
        });
        addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        select(recent().isEmpty() ? 1 : 0);
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("chat_ui", Context.MODE_PRIVATE);
    }

    private List<String> recent() {
        String s = prefs().getString("recent_emoji", "").trim();
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(" ")));
    }

    private void remember(String e) {
        prefs().edit().putString("recent_emoji", String.join(" ", Emoji.remember(recent(), e, 32))).apply();
    }

    private void select(int index) {
        tab = index;
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).setBackgroundResource(i == index ? R.drawable.pill_bg : 0);
        shown = index == 0 ? recent() : Arrays.asList(Emoji.LIST[index - 1]);
        adapter.notifyDataSetChanged();
        grid.setSelection(0);
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int i) {
            return shown.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View old, ViewGroup parent) {
            TextView t = old instanceof TextView ? (TextView) old : new TextView(parent.getContext());
            t.setText(shown.get(i));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(Color.WHITE);
            t.setMinHeight(Ui.dp(parent.getContext(), 50));
            return t;
        }
    }
}
