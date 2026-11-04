package com.himalayasoni.ledger;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** Ledger → Settings → Connect opens ledger://link?uid=…&rt=…&email=…, which lands here. */
public class LinkActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri u = getIntent().getData();
        String uid = u == null ? null : u.getQueryParameter("uid");
        String rt = u == null ? null : u.getQueryParameter("rt");
        String email = u == null ? "" : u.getQueryParameter("email");
        if (uid != null && rt != null && !uid.isEmpty() && !rt.isEmpty()) {
            Cloud.link(this, uid, rt, email == null ? "" : email);
            Cloud.fetchCatsAsync(this, null);
            Cloud.flushAsync(this);
            Toast.makeText(this, "Quick add is connected. It now saves straight to your account.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Couldn't connect. Sign in to Ledger first, then try again.", Toast.LENGTH_LONG).show();
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
