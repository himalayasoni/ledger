package com.himalayasoni.ledger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Talks to the same Firestore database Ledger uses, as the signed-in person.
 * Ledger hands over a sign-in token once ("Connect" in Settings); entries saved
 * in the pop-up are written straight to the cloud, and Ledger picks them up live.
 * Anything that can't be sent yet waits in a small queue on the phone.
 */
final class Cloud {
    private Cloud() {}

    static final List<String> DEFAULT_CATS = Arrays.asList("Food", "Groceries", "Transport", "Bills", "Shopping", "Rent", "Health", "Fun", "Travel");

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("cloud", Context.MODE_PRIVATE); }

    static boolean linked(Context c) {
        SharedPreferences p = prefs(c);
        return p.getString("rt", null) != null && p.getString("uid", null) != null && !p.getBoolean("relink", false);
    }

    static void link(Context c, String uid, String refreshToken, String email) {
        prefs(c).edit().putString("uid", uid).putString("rt", refreshToken).putString("email", email)
                .remove("idt").remove("idtExp").putBoolean("relink", false).apply();
    }

    static List<String> cachedCats(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("cats", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {}
        return out.isEmpty() ? DEFAULT_CATS : out;
    }

    static String mode(Context c) { return prefs(c).getString("mode", "UPI"); }

    static JSONObject newEntry(Context c, double amount, String cat) throws Exception {
        long now = System.currentTimeMillis();
        String id = Long.toString(now, 36) + Long.toString(Math.abs(new Random().nextLong()), 36).substring(0, 6);
        JSONObject e = new JSONObject();
        e.put("id", id); e.put("type", "spent"); e.put("amount", amount); e.put("desc", ""); e.put("person", "");
        e.put("cat", cat); e.put("mode", mode(c)); e.put("note", "");
        e.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now)));
        e.put("createdAt", now); e.put("updatedAt", now);
        return e;
    }

    // ---------- queue ----------
    static synchronized JSONArray pending(Context c) {
        try { return new JSONArray(prefs(c).getString("pending", "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    private static synchronized void savePending(Context c, JSONArray a) { prefs(c).edit().putString("pending", a.toString()).apply(); }

    static synchronized void enqueue(Context c, JSONObject e) { JSONArray a = pending(c); a.put(e); savePending(c, a); }

    /** Undo: drop it if it hasn't been sent; otherwise send a deletion. */
    static synchronized void remove(Context c, String id) {
        JSONArray a = pending(c), keep = new JSONArray(); boolean found = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e != null && id.equals(e.optString("id"))) found = true; else keep.put(e);
        }
        if (!found) {
            try {
                JSONObject t = new JSONObject();
                t.put("id", id); t.put("deleted", true); t.put("updatedAt", System.currentTimeMillis());
                keep.put(t);
            } catch (Exception ignored) {}
        }
        savePending(c, keep);
    }

    /** For when the pop-up isn't connected: hand queued entries to Ledger when it opens. */
    static synchronized String takeInbox(Context c) {
        if (linked(c)) return null;
        JSONArray a = pending(c);
        if (a.length() == 0) return null;
        savePending(c, new JSONArray());
        return Base64.encodeToString(a.toString().getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    static void flushAsync(Context c) {
        final Context app = c.getApplicationContext();
        new Thread(() -> { try { flush(app); } catch (Exception ignored) {} }).start();
    }

    private static synchronized void flush(Context c) throws Exception {
        if (!linked(c)) return;
        JSONArray a = pending(c);
        if (a.length() == 0) return;
        String token = idToken(c);
        if (token == null) return;
        String uid = prefs(c).getString("uid", "");
        String base = "projects/" + BuildConfig.FIREBASE_PROJECT + "/databases/(default)/documents/users/" + uid + "/entries/";
        JSONArray writes = new JSONArray();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.getJSONObject(i);
            JSONObject w = new JSONObject();
            w.put("update", new JSONObject().put("name", base + e.getString("id")).put("fields", toFields(e)));
            w.put("updateTransforms", new JSONArray().put(new JSONObject().put("fieldPath", "srv").put("setToServerValue", "REQUEST_TIME")));
            writes.put(w);
            ids.add(e.getString("id"));
        }
        String url = "https://firestore.googleapis.com/v1/projects/" + BuildConfig.FIREBASE_PROJECT + "/databases/(default)/documents:commit";
        Http r = Http.post(url, "application/json", new JSONObject().put("writes", writes).toString(), token);
        if (r.code == 200) {
            JSONArray now = pending(c), keep = new JSONArray();
            for (int i = 0; i < now.length(); i++) {
                JSONObject e = now.optJSONObject(i);
                if (e != null && !ids.contains(e.optString("id"))) keep.put(e);
            }
            savePending(c, keep);
        } else if (r.code == 401 || r.code == 403) {
            prefs(c).edit().remove("idt").remove("idtExp").apply();
        }
    }

    /** Ledger's top categories and last payment mode, which Ledger keeps up to date in the cloud. */
    static void fetchCatsAsync(Context c, Consumer<List<String>> done) {
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                if (!linked(app)) return;
                String token = idToken(app);
                if (token == null) return;
                String uid = prefs(app).getString("uid", "");
                String url = "https://firestore.googleapis.com/v1/projects/" + BuildConfig.FIREBASE_PROJECT
                        + "/databases/(default)/documents/users/" + uid + "/entries/cfg-quick";
                Http r = Http.get(url, token);
                if (r.code != 200) return;
                JSONObject f = new JSONObject(r.body).getJSONObject("fields");
                JSONArray vals = f.getJSONObject("cats").getJSONObject("arrayValue").optJSONArray("values");
                JSONArray cats = new JSONArray();
                List<String> list = new ArrayList<>();
                if (vals != null) for (int i = 0; i < vals.length(); i++) {
                    String s = vals.getJSONObject(i).optString("stringValue", "");
                    if (!s.isEmpty()) { cats.put(s); list.add(s); }
                }
                SharedPreferences.Editor ed = prefs(app).edit().putString("cats", cats.toString());
                if (f.has("mode")) ed.putString("mode", f.getJSONObject("mode").optString("stringValue", "UPI"));
                ed.apply();
                if (!list.isEmpty() && done != null) done.accept(list);
            } catch (Exception ignored) {}
        }).start();
    }

    private static String idToken(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        String t = p.getString("idt", null);
        if (t != null && p.getLong("idtExp", 0) > System.currentTimeMillis() + 60_000) return t;
        String body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(p.getString("rt", ""), "UTF-8");
        Http r = Http.post("https://securetoken.googleapis.com/v1/token?key=" + BuildConfig.FIREBASE_KEY,
                "application/x-www-form-urlencoded", body, null);
        if (r.code == 400 || r.code == 401 || r.code == 403) { p.edit().putBoolean("relink", true).apply(); return null; }
        if (r.code != 200) return null;
        JSONObject j = new JSONObject(r.body);
        String idt = j.getString("id_token");
        long exp = System.currentTimeMillis() + Long.parseLong(j.optString("expires_in", "3600")) * 1000L;
        SharedPreferences.Editor ed = p.edit().putString("idt", idt).putLong("idtExp", exp);
        if (j.has("refresh_token")) ed.putString("rt", j.getString("refresh_token"));
        ed.apply();
        return idt;
    }

    private static JSONObject toFields(JSONObject e) throws Exception {
        JSONObject f = new JSONObject();
        Iterator<String> it = e.keys();
        while (it.hasNext()) {
            String k = it.next(); Object v = e.get(k);
            JSONObject val = new JSONObject();
            if (v instanceof Boolean) val.put("booleanValue", v);
            else if (v instanceof Integer || v instanceof Long) val.put("integerValue", String.valueOf(v));
            else if (v instanceof Number) val.put("doubleValue", ((Number) v).doubleValue());
            else val.put("stringValue", String.valueOf(v));
            f.put(k, val);
        }
        return f;
    }

    /** Minimal HTTP helper (runs on a background thread). */
    static final class Http {
        final int code; final String body;
        private Http(int code, String body) { this.code = code; this.body = body; }

        static Http get(String url, String bearer) throws Exception { return send("GET", url, null, null, bearer); }
        static Http post(String url, String type, String body, String bearer) throws Exception { return send("POST", url, type, body, bearer); }

        private static Http send(String method, String url, String type, String body, String bearer) throws Exception {
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setRequestMethod(method);
            h.setConnectTimeout(10_000); h.setReadTimeout(15_000);
            if (bearer != null) h.setRequestProperty("Authorization", "Bearer " + bearer);
            if (body != null) {
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", type);
                try (OutputStream o = h.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            int code = h.getResponseCode();
            InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            if (in != null) { byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) buf.write(b, 0, n); in.close(); }
            h.disconnect();
            return new Http(code, buf.toString("UTF-8"));
        }
    }
}
