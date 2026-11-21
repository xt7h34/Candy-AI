package com.candyai

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** The person closed the Google sheet without choosing an account. Not an error worth showing. */
class GoogleCancelled : Exception()

object GoogleAuth {
    /**
     * Opens the Google account sheet and returns an ID token whose audience is the WEB client ID
     * (that is what the server checks). [ctx] must be an Activity.
     */
    suspend fun idToken(ctx: Context): String {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(ctx).getCredential(ctx, request)
        } catch (e: GetCredentialCancellationException) {
            throw GoogleCancelled()
        }
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(cred.data).idToken
        }
        throw IllegalStateException("not a Google credential")
    }
}
