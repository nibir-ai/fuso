package com.fuso.core.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fuso.core.data.remote.AuthTokens
import com.fuso.core.data.remote.SupabaseConfig
import com.fuso.core.data.remote.SupabaseApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class UserSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSec: Long,
)

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "fuso_sync")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val store = context.syncDataStore

    val session: Flow<UserSession?> = store.data.map { prefs ->
        val userId = prefs[Keys.USER_ID] ?: return@map null
        UserSession(
            userId = userId,
            email = prefs[Keys.EMAIL].orEmpty(),
            accessToken = prefs[Keys.ACCESS_TOKEN] ?: return@map null,
            refreshToken = prefs[Keys.REFRESH_TOKEN] ?: return@map null,
            expiresAtEpochSec = prefs[Keys.EXPIRES_AT] ?: 0L,
        )
    }

    suspend fun sessionOnce(): UserSession? = session.first()

    suspend fun saveSession(tokens: AuthTokens, fallbackEmail: String) {
        val user = tokens.user ?: return
        store.edit { prefs ->
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.EMAIL] = user.email ?: fallbackEmail
            prefs[Keys.ACCESS_TOKEN] = tokens.access_token.orEmpty()
            prefs[Keys.REFRESH_TOKEN] = tokens.refresh_token.orEmpty()
            prefs[Keys.EXPIRES_AT] = tokens.expires_at ?: 0L
        }
    }

    suspend fun updateTokens(access: String, refresh: String, expiresAtEpochSec: Long) {
        store.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = access
            prefs[Keys.REFRESH_TOKEN] = refresh
            prefs[Keys.EXPIRES_AT] = expiresAtEpochSec
        }
    }

    suspend fun clear() {
        store.edit { prefs ->
            listOf(Keys.USER_ID, Keys.EMAIL, Keys.ACCESS_TOKEN, Keys.REFRESH_TOKEN, Keys.EXPIRES_AT, Keys.LAST_SYNCED_AT)
                .forEach { prefs.remove(it) }
        }
    }

    suspend fun lastSyncedAtMillis(): Long? = store.data.first()[Keys.LAST_SYNCED_AT]

    suspend fun setLastSyncedAt(millis: Long) {
        store.edit { it[Keys.LAST_SYNCED_AT] = millis }
    }

    suspend fun isAccessTokenValid(): Boolean {
        val current = sessionOnce() ?: return false
        val nowPlusSkew = System.currentTimeMillis() / 1000 + Keys.EXPIRY_SKEW_SEC
        return current.expiresAtEpochSec > nowPlusSkew && current.accessToken.isNotBlank()
    }

    suspend fun freshAccessToken(config: SupabaseConfig): String? {
        if (isAccessTokenValid()) return sessionOnce()?.accessToken
        val current = sessionOnce() ?: return null
        val api = SupabaseApi(config)
        val refreshed = api.refreshToken(current.refreshToken).getOrNull() ?: return null
        val access = refreshed.access_token ?: return null
        updateTokens(
            access = access,
            refresh = refreshed.refresh_token ?: current.refreshToken,
            expiresAtEpochSec = refreshed.expires_at ?: 0L,
        )
        return access
    }

    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        const val EXPIRY_SKEW_SEC = 60L
    }
}
