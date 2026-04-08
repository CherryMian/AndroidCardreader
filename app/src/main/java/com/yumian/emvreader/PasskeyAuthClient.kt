package com.yumian.emvreader

import android.content.Context
import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Passkey auth client hitting the local backend (http://localhost:8000).
 * Replace localhost with production base URL when HTTPS is available.
 */
object PasskeyAuthClient {
    private const val logTag = "PasskeyAuth"
    private const val BASE_URL = "http://192.168.0.54:8000"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    data class PasskeyChallenge(val challenge: String, val credentialId: String? = null)
    data class PasskeyCredential(
        val credentialId: String,
        val clientDataJson: String = "",
        val authenticatorData: String = "",
        val signature: String = "",
        val publicKey: String = "" // only needed for register
    )

    private data class RegisterInitRequest(@Json(name = "username") val username: String)
    private data class RegisterInitResponse(@Json(name = "challenge") val challenge: String)
    private data class RegisterFinishRequest(
        @Json(name = "username") val username: String,
        @Json(name = "cred_id") val credId: String,
        @Json(name = "public_key") val publicKey: String
    )

    private data class LoginInitRequest(@Json(name = "username") val username: String)
    private data class LoginInitResponse(
        @Json(name = "challenge") val challenge: String,
        @Json(name = "cred_id") val credId: String?
    )

    private data class LoginFinishRequest(
        @Json(name = "username") val username: String,
        @Json(name = "cred_id") val credId: String
    )

    private data class TokenResponse(@Json(name = "token") val token: String)

    suspend fun requestRegisterChallenge(context: Context, username: String): PasskeyChallenge =
        withContext(Dispatchers.IO) {
            Log.d(logTag, "request register challenge for user=$username")
            val body = moshi.adapter(RegisterInitRequest::class.java)
                .toJson(RegisterInitRequest(username))
                .toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$BASE_URL/auth/register/init")
                .post(body)
                .header("X-Device-Id", DeviceIdProvider.get(context))
                .build()
            client.newCall(request).execute().use { resp ->
                checkResponse(resp)
                val parsed = moshi.adapter(RegisterInitResponse::class.java)
                    .fromJson(resp.body?.string() ?: "")
                    ?: throw IOException("响应解析失败")
                return@withContext PasskeyChallenge(parsed.challenge)
            }
        }

    suspend fun performPasskeyRegister(context: Context, challenge: PasskeyChallenge): PasskeyCredential =
        withContext(Dispatchers.Default) {
            Log.d(logTag, "perform passkey register for challenge=${challenge.challenge}")
            // TODO: integrate Credential Manager / FIDO2 create-passkey flow and return attestation data.
            // Placeholder only.
            PasskeyCredential(
                credentialId = "demo-cred-id",
                publicKey = "demo-public-key"
            )
        }

    suspend fun finishRegister(context: Context, username: String, credential: PasskeyCredential) =
        withContext(Dispatchers.IO) {
            Log.d(logTag, "finish register user=$username credId=${credential.credentialId}")
            val body = moshi.adapter(RegisterFinishRequest::class.java)
                .toJson(
                    RegisterFinishRequest(
                        username = username,
                        credId = credential.credentialId,
                        publicKey = credential.publicKey
                    )
                ).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$BASE_URL/auth/register/finish")
                .post(body)
                .header("X-Device-Id", DeviceIdProvider.get(context))
                .build()
            client.newCall(request).execute().use { resp ->
                checkResponse(resp)
            }
        }

    suspend fun requestLoginChallenge(context: Context, username: String): PasskeyChallenge =
        withContext(Dispatchers.IO) {
            Log.d(logTag, "request login challenge for user=$username")
            val body = moshi.adapter(LoginInitRequest::class.java)
                .toJson(LoginInitRequest(username))
                .toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$BASE_URL/auth/login/init")
                .post(body)
                .header("X-Device-Id", DeviceIdProvider.get(context))
                .build()
            client.newCall(request).execute().use { resp ->
                checkResponse(resp)
                val parsed = moshi.adapter(LoginInitResponse::class.java)
                    .fromJson(resp.body?.string() ?: "")
                    ?: throw IOException("响应解析失败")
                return@withContext PasskeyChallenge(parsed.challenge, parsed.credId)
            }
        }

    suspend fun performPasskeyLogin(context: Context, challenge: PasskeyChallenge): PasskeyCredential =
        withContext(Dispatchers.Default) {
            Log.d(logTag, "perform passkey login for challenge=${challenge.challenge}")
            // TODO: integrate Credential Manager / FIDO2 get-passkey flow and return assertion data.
            // Placeholder only.
            PasskeyCredential(
                credentialId = challenge.credentialId ?: "demo-cred-id",
                clientDataJson = "demo-client-data",
                authenticatorData = "demo-auth-data",
                signature = "demo-signature"
            )
        }

    suspend fun finishLogin(context: Context, username: String, credential: PasskeyCredential): String =
        withContext(Dispatchers.IO) {
            Log.d(logTag, "finish login user=$username credId=${credential.credentialId}")
            val body = moshi.adapter(LoginFinishRequest::class.java)
                .toJson(LoginFinishRequest(username, credential.credentialId))
                .toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$BASE_URL/auth/login/finish")
                .post(body)
                .header("X-Device-Id", DeviceIdProvider.get(context))
                .build()
            client.newCall(request).execute().use { resp ->
                checkResponse(resp)
                val parsed = moshi.adapter(TokenResponse::class.java)
                    .fromJson(resp.body?.string() ?: "")
                    ?: throw IOException("响应解析失败")
                return@withContext parsed.token
            }
        }

    private fun checkResponse(resp: Response) {
        if (!resp.isSuccessful) {
            val code = resp.code
            val body = resp.body?.string()
            throw IOException("请求失败($code): ${body ?: ""}")
        }
    }
}
