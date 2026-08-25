package com.fuso.core.data.remote

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class SupabaseConfig(
    val url: String,
    val anonKey: String,
)

@Serializable
data class AuthTokens(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_at: Long? = null,
    val user: AuthUser? = null,
)

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null,
)

@Serializable
data class RemoteEntry(
    val id: String,
    val user_id: String,
    val type: String,
    val title: String,
    val blocks_json: JsonElement,
    val tags_json: JsonElement,
    val is_pinned: Boolean,
    val is_archived: Boolean,
    val created_at: String,
    val updated_at: String,
    val deleted_at: String? = null,
    val color_index: Int? = null,
)

sealed interface SyncHttpError {
    data object Unauthorized : SyncHttpError
    data class Other(val code: Int, val body: String) : SyncHttpError
}

class SyncException(val error: SyncHttpError) : Exception(error.toString())

@Singleton
class SupabaseApi @Inject constructor(
    private val config: SupabaseConfig,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun signUp(email: String, password: String): Result<AuthTokens> =
        authCall("/auth/v1/signup", email, password)

    suspend fun signIn(email: String, password: String): Result<AuthTokens> =
        authCall("/auth/v1/token?grant_type=password", email, password)

    suspend fun refreshToken(refreshToken: String): Result<AuthTokens> = runCatching {
        val payload = buildJsonObject { put("refresh_token", JsonPrimitive(refreshToken)) }
        val request = Request.Builder()
            .url("${config.url}/auth/v1/token?grant_type=refresh_token")
            .header("apikey", config.anonKey)
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(jsonMedia))
            .build()
        val body = executeBody(request)
        json.decodeFromString(AuthTokens.serializer(), body)
    }

    suspend fun upsertEntries(accessToken: String, entries: List<RemoteEntry>): Result<Unit> = runCatching {
        val payload = json.encodeToString(ListSerializer(RemoteEntry.serializer()), entries)
        val request = Request.Builder()
            .url("${config.url}/rest/v1/fuso_entries?on_conflict=id")
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer $accessToken")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        executeBody(request)
        Unit
    }

    suspend fun deleteEntry(accessToken: String, entryId: String): Result<Unit> = runCatching {
        val request = Request.Builder()
            .url("${config.url}/rest/v1/fuso_entries?id=eq.$entryId")
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        executeBody(request)
        Unit
    }

    suspend fun fetchUpdatedSince(accessToken: String, sinceIsoExclusive: String?): Result<List<RemoteEntry>> = runCatching {
        val filter = if (sinceIsoExclusive.isNullOrBlank()) "" else "&updated_at=gt.$sinceIsoExclusive"
        val request = Request.Builder()
            .url("${config.url}/rest/v1/fuso_entries?select=*&order=updated_at.asc$filter")
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        json.decodeFromString(ListSerializer(RemoteEntry.serializer()), executeBody(request))
    }

    private suspend fun authCall(path: String, email: String, password: String): Result<AuthTokens> = runCatching {
        val payload = buildJsonObject {
            put("email", JsonPrimitive(email))
            put("password", JsonPrimitive(password))
        }
        val request = Request.Builder()
            .url("${config.url}$path")
            .header("apikey", config.anonKey)
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(jsonMedia))
            .build()
        json.decodeFromString(AuthTokens.serializer(), executeBody(request))
    }

    private suspend fun executeBody(request: Request): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 401 -> throw SyncException(SyncHttpError.Unauthorized)
                    !response.isSuccessful -> throw SyncException(SyncHttpError.Other(response.code, body))
                    else -> body
                }
            }
        }
}
