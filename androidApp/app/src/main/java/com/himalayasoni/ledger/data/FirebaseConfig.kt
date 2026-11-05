package com.himalayasoni.ledger.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.himalayasoni.ledger.BuildConfig

/** Starts Firebase with the values read from google-services.json at build time. */
object FirebaseConfig {
    val isConfigured: Boolean get() = BuildConfig.FIREBASE_APP_ID.isNotEmpty() && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotEmpty()

    fun init(context: Context) {
        if (!isConfigured || FirebaseApp.getApps(context).isNotEmpty()) return
        FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .build(),
        )
        // Room is our offline store, so Firestore doesn't need its own copy on disk.
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
    }
}
