package com.himalayasoni.ledger;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * "Say it" through Android's own voice typing (the Google mic screen), so Ledger
 * never asks for microphone access itself. The words go to Ledger's entry form.
 */
public class VoiceActivity extends Activity {
    private static final int REQ = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say it, like “chai 20” or “lent 200 to Ashish”");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            startActivityForResult(i, REQ);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Voice typing isn't available on this phone. Type it instead.", Toast.LENGTH_LONG).show();
            startActivity(Links.open(this, "add=1"));
            finish();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ && resultCode == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty() && r.get(0) != null && !r.get(0).trim().isEmpty()) {
                startActivity(Links.open(this, "say=" + Uri.encode(r.get(0).trim())));
            }
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
