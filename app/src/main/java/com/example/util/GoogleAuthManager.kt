package com.example.util

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.data.model.GoogleAuthRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.json.JSONObject

object GoogleAuthManager {
    const val SERVER_CLIENT_ID = "860410619918-0fql7n70arbii6ev82aun5a0cejgoq1c.apps.googleusercontent.com"
    private const val TAG = "GoogleAuthManager"

    suspend fun signIn(context: Context): Result<GoogleAuthRequest> {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(SERVER_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = credentialManager.getCredential(
                request = request,
                context = context
            )
            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                // Parse JWT payload for accurate Google ID, email, name, avatar
                val jwtData = parseJwtPayload(idToken)
                val googleId = jwtData.optString("sub").takeIf { it.isNotBlank() }
                    ?: googleIdTokenCredential.id.takeIf { it.isNotBlank() }
                    ?: "gid_${System.currentTimeMillis()}"

                val email = jwtData.optString("email").takeIf { it.isNotBlank() }
                    ?: googleIdTokenCredential.id.takeIf { it.contains("@") }
                    ?: "user@gmail.com"

                val name = googleIdTokenCredential.displayName?.takeIf { it.isNotBlank() }
                    ?: jwtData.optString("name").takeIf { it.isNotBlank() }
                    ?: googleIdTokenCredential.givenName?.let { "$it ${googleIdTokenCredential.familyName ?: ""}".trim() }
                    ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }

                val avatar = googleIdTokenCredential.profilePictureUri?.toString()
                    ?: jwtData.optString("picture").takeIf { it.isNotBlank() }
                    ?: "https://lh3.googleusercontent.com/a/default-user"

                Log.d(TAG, "Google Sign-In successful for email: $email, googleId: $googleId")
                Result.success(
                    GoogleAuthRequest(
                        googleId = googleId,
                        email = email,
                        name = name,
                        avatar = avatar
                    )
                )
            } else {
                Result.failure(Exception("Unsupported credential type: ${credential::class.java.simpleName}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled by user")
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Credential Manager error: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In unexpected error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun parseJwtPayload(jwtToken: String): JSONObject {
        return try {
            val parts = jwtToken.split(".")
            if (parts.size >= 2) {
                val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
                JSONObject(payload)
            } else {
                JSONObject()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JWT payload: ${e.message}")
            JSONObject()
        }
    }

    suspend fun signOut(context: Context) {
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Log.d(TAG, "Google Credential State cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear credential state: ${e.message}")
        }
    }
}
