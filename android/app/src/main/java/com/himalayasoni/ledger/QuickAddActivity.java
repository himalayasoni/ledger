package com.himalayasoni.ledger;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** A small pop-up over whatever you were doing: type the amount, tap a category, done. */
public class QuickAddActivity extends Activity {
    private final StringBuilder amt = new StringBuilder();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView amountView, hintView;
    private GridLayout catGrid, pad;
    private View doneBox, entryBox;
    private String savedId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.quick_add);
        amountView = findViewById(R.id.q_amount);
        hintView = findViewById(R.id.q_hint);
        catGrid = findViewById(R.id.q_cats);
        pad = findViewById(R.id.q_pad);
        doneBox = findViewById(R.id.q_done);
        entryBox = findViewById(R.id.q_entry);

        findViewById(R.id.q_root).setOnClickListener(v -> finish());
        findViewById(R.id.q_card).setOnClickListener(v -> { });
        findViewById(R.id.q_open).setOnClickListener(v -> { startActivity(Links.open(this, "app=1")); finish(); });
        findViewById(R.id.q_undo).setOnClickListener(v -> undo());

        buildPad();
        renderCats(Cloud.cachedCats(this));
        refresh();
        if (!Cloud.linked(this)) hintView.setText(R.string.q_not_linked);
        Cloud.fetchCatsAsync(this, list -> ui.post(() -> { if (savedId == null) renderCats(list); }));
        Cloud.flushAsync(this);
    }

    private void buildPad() {
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫"};
        for (String k : keys) {
            TextView b = cell(k, R.drawable.q_key, 22, R.color.q_ink);
            b.setContentDescription("⌫".equals(k) ? "Delete digit" : k);
            b.setOnClickListener(v -> press(k));
            pad.addView(b, lp(52));
        }
    }

    private void renderCats(List<String> cats) {
        catGrid.removeAllViews();
        int n = Math.min(9, cats.size());
        for (int i = 0; i < n; i++) {
            final String c = cats.get(i);
            TextView b = cell(c, R.drawable.q_chip, 14, R.color.q_ink);
            b.setSingleLine(true);
            b.setOnClickListener(v -> save(c));
            catGrid.addView(b, lp(46));
        }
    }

    private TextView cell(String text, int bg, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(sp);
        t.setTextColor(getColor(color));
        t.setBackgroundResource(bg);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private GridLayout.LayoutParams lp(int heightDp) {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        p.width = 0;
        p.height = dp(heightDp);
        int m = dp(3);
        p.setMargins(m, m, m, m);
        return p;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void press(String k) {
        String s = amt.toString();
        if ("⌫".equals(k)) { if (s.length() > 0) amt.setLength(s.length() - 1); }
        else if (".".equals(k)) { if (!s.contains(".")) { if (s.isEmpty()) amt.append('0'); amt.append('.'); } }
        else {
            if (s.contains(".")) { if (s.length() - s.indexOf('.') > 2) return; }
            else if (s.replaceFirst("^0+", "").length() >= 9) return;
            if ("0".equals(s)) amt.setLength(0);
            amt.append(k);
        }
        tick();
        refresh();
    }

    private double value() {
        try { return amt.length() == 0 ? 0 : Double.parseDouble(amt.toString()); } catch (Exception e) { return 0; }
    }

    private void refresh() {
        String s = amt.toString();
        String shown;
        if (s.isEmpty()) shown = "0";
        else {
            String[] parts = s.split("\\.", -1);
            long whole = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
            shown = indian(whole) + (s.contains(".") ? "." + (parts.length > 1 ? parts[1] : "") : "");
        }
        amountView.setText("₹" + shown);
        amountView.setAlpha(value() > 0 ? 1f : 0.45f);
    }

    /** 1,00,000 style grouping. */
    private static String indian(long n) {
        String s = Long.toString(n);
        if (s.length() <= 3) return s;
        String last3 = s.substring(s.length() - 3), rest = s.substring(0, s.length() - 3);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            if (i > 0 && (rest.length() - i) % 2 == 0) b.append(',');
            b.append(rest.charAt(i));
        }
        return b + "," + last3;
    }

    private void save(String cat) {
        double v = Math.round(value() * 100) / 100.0;
        if (v <= 0) {
            hintView.setText(R.string.q_amount_first);
            ObjectAnimator.ofFloat(amountView, "translationX", 0, -14, 14, -10, 10, 0).setDuration(320).start();
            tick();
            return;
        }
        try {
            JSONObject e = Cloud.newEntry(this, v, cat);
            savedId = e.getString("id");
            Cloud.enqueue(this, e);
            Cloud.flushAsync(this);
        } catch (Exception ex) {
            Toast.makeText(this, "Couldn't save. Try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        tick();
        entryBox.setVisibility(View.GONE);
        doneBox.setVisibility(View.VISIBLE);
        String amount = "₹" + (v == Math.floor(v) ? indian((long) v) : NumberFormat.getNumberInstance(new Locale("en", "IN")).format(v));
        ((TextView) findViewById(R.id.q_done_text)).setText("Logged " + amount + " · " + cat);
        ui.postDelayed(this::finish, 2200);
    }

    private void undo() {
        if (savedId == null) return;
        ui.removeCallbacksAndMessages(null);
        Cloud.remove(this, savedId);
        Cloud.flushAsync(this);
        Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void tick() {
        try {
            Vibrator vib = getSystemService(Vibrator.class);
            if (vib != null) vib.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {}
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }
}
