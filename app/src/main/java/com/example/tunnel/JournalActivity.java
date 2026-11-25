package com.example.tunnel;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import java.util.ArrayList;

public final class JournalActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final ArrayList<String> entries = new ArrayList<>();
    private final ArrayList<String> visible = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private Switch detail;
    private ListView list;
    private String snapshot = "";
    private boolean active;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            worker.execute(() -> {
                String value = VpnJournal.read(getApplicationContext());
                runOnUiThread(() -> {
                    if (!active) return;
                    if (!value.equals(snapshot)) {
                        snapshot = value;
                        rebuild();
                    }
                    handler.postDelayed(this, 2000);
                });
            });
        }
    };
    private void rebuild() {
        int count = Math.max(50, visible.size());
        entries.clear();
        for (String line : snapshot.split("\n")) {
            if (line.isEmpty()) continue;
            boolean detailed = line.length() > 16 && line.charAt(15) == 'D' && line.charAt(16) == ' ';
            if (!detail.isChecked() && detailed) continue;
            entries.add(line.length() > 16 && line.charAt(14) == ' ' && line.charAt(16) == ' '
                    ? line.substring(0, 15) + line.substring(17) : line);
        }
        visible.clear();
        visible.addAll(entries.subList(0, Math.min(count, entries.size())));
        adapter.notifyDataSetChanged();
    }
    private void loadMore() {
        int start = visible.size();
        int end = Math.min(start + 50, entries.size());
        if (start == end) return;
        visible.addAll(entries.subList(start, end));
        adapter.notifyDataSetChanged();
    }
    @Override protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(AppLocale.context(base)); }

    @Override public void onCreate(Bundle state) {
        VexisUi.applyTheme(this);
        super.onCreate(state);
        LinearLayout root = DetailUi.root(this, getString(R.string.secondary_61));
        root.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        setContentView(root);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = DetailUi.dp(this, 16);
        body.setPadding(padding, 0, padding, 0);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        detail = VexisUi.switchControl(this);
        detail.setText(getString(R.string.secondary_62)); detail.setTextSize(14); detail.setTextColor(DetailUi.TEXT);
        body.addView(detail, new LinearLayout.LayoutParams(-1, DetailUi.dp(this, 63)));
        DetailUi.divider(this, body);
        list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, visible);
        list.setAdapter(adapter);
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int state) { }
            @Override public void onScroll(AbsListView view, int first, int count, int total) {
                if (total > 0 && first + count >= total - 20) loadMore();
            }
        });
        detail.setOnCheckedChangeListener((view, checked) -> { visible.clear(); rebuild(); list.setSelection(0); });
    }
    @Override protected void onResume() { super.onResume(); active = true; refresh.run(); }
    @Override protected void onPause() { active = false; handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
