package com.himalayasoni.ledger.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.himalayasoni.ledger.BuildConfig
import com.himalayasoni.ledger.data.FirebaseConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class Account(val uid: String, val email: String)

interface AuthRepository {
    val account: StateFlow<Account?>
    suspend fun signIn(activity: Activity): Result<Unit>
    suspend fun signOut(context: Context)
}

/**
 * Google sign-in with Android's Credential Manager (the system account picker),
 * then Firebase Auth with the Google ID token. Firebase keeps you signed in after that.
 */
class FirebaseAuthRepository : AuthRepository {
    private val auth: FirebaseAuth? = if (FirebaseConfig.isConfigured) FirebaseAuth.getInstance() else null
    private val _account = MutableStateFlow(auth?.currentUser?.toAccount())
    override val account: StateFlow<Account?> = _account.asStateFlow()

    init {
        auth?.addAuthStateListener { _account.value = it.currentUser?.toAccount() }
    }

    override suspend fun signIn(activity: Activity): Result<Unit> = runCatching {
        val auth = auth ?: error("Sync isn't set up in this build yet.")
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw SignInCancelled()
        } catch (e: NoCredentialException) {
            error("No Google account found on this phone.")
        } catch (e: GetCredentialException) {
            error("Google sign-in didn't work: ${e.message ?: e.type}")
        }
        val credential = result.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected sign-in result."
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
        Unit
    }

    override suspend fun signOut(context: Context) {
        auth?.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun FirebaseUser.toAccount() = Account(uid, email.orEmpty())
}

/** The person closed the account picker: not an error worth showing. */
class SignInCancelled : Exception("Sign-in cancelled")
