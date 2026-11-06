package com.example.fulluikotlin.data.local.datastore

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fulluikotlin.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.userDataStore by preferencesDataStore(
    name = "user_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler<Preferences> {
        // A corrupted preferences file used to leave the app stuck until Clear Data.
        // Recover with an empty store so the user can reach Login again.
        emptyPreferences()
    }
)

class UserDataStore(private val context: Context) {

    data class AutoLoginCredentials(
        val username: String,
        val password: String,
        val device: String
    )

    private val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val DEVICE_KEY = stringPreferencesKey("device")
    private val CREATION_DATE_KEY = stringPreferencesKey("creation_date")
    private val EXP_DATE_KEY = stringPreferencesKey("exp_date")
    private val TOTAL_TRAFICK_KEY = stringPreferencesKey("total_traffic")
    private val REMAIN_TRAFICK_KEY = stringPreferencesKey("remain_traffic")
    private val GROUP_NAME_KEY = stringPreferencesKey("group_name")
    private val REMAIN_DAYS_KEY = stringPreferencesKey("remain_days")
    private val REMAIN_DEVICE_KEY = stringPreferencesKey("remain_device")
    private val IS_EXPIRED_KEY = booleanPreferencesKey("is_expired")
    private val DEVICES_LIST_KEY = stringPreferencesKey("devices_list")

    private val MULTI_LOGIN_DEVICES_KEY = stringPreferencesKey("multi_login_devices")
    private val MULTI_LOGIN_USERNAME_KEY = stringPreferencesKey("multi_login_username")
    private val MULTI_LOGIN_PASSWORD_KEY = stringPreferencesKey("multi_login_password")
    private val MULTI_LOGIN_DEVICE_KEY = stringPreferencesKey("multi_login_device")

    private val SAVED_USERNAME_KEY = stringPreferencesKey("saved_username")
    private val SAVED_PASSWORD_KEY = stringPreferencesKey("saved_password")

    val isLoggedIn: Flow<Boolean> = context.userDataStore.data
        .map { it[IS_LOGGED_IN_KEY] ?: false }

    val userFlow: Flow<User?> = context.userDataStore.data
        .map { prefs ->
            val id = prefs[USER_ID_KEY] ?: return@map null
            val username = prefs[USERNAME_KEY] ?: return@map null
            val device = prefs[DEVICE_KEY] ?: return@map null
            val devices = prefs[DEVICES_LIST_KEY] ?: return@map null
            val creationDate = prefs[CREATION_DATE_KEY] ?: ""
            val expDate = prefs[EXP_DATE_KEY] ?: ""
            val totalTraffic = prefs[TOTAL_TRAFICK_KEY] ?: ""
            val remainTraffic = prefs[REMAIN_TRAFICK_KEY] ?: ""
            val groupName = prefs[GROUP_NAME_KEY] ?: ""
            val remainDays = prefs[REMAIN_DAYS_KEY]?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
            val remainDevice = prefs[REMAIN_DEVICE_KEY] ?: ""
            val isExpired = prefs[IS_EXPIRED_KEY] ?: true
            User(
                id = id,
                username = username,
                device = device,
                devices = devices,
                creationDate = creationDate,
                expDate = expDate,
                totalTraffic = totalTraffic,
                remainTraffic = remainTraffic,
                groupName = groupName,
                remainDays = remainDays,
                remainDevice = remainDevice,
                isExpired = isExpired
            )
        }

    /**
     * Persists the whole authenticated session in a single DataStore transaction.
     * This prevents `is_logged_in=true` from being saved without the credentials
     * needed by Splash for the next automatic login.
     */
    suspend fun saveAuthenticatedSession(user: User, password: String) {
        val devicesList = user.devices
            ?.split("&&")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val devicesJson = Json.encodeToString(devicesList)

        context.userDataStore.edit { prefs ->
            writeUser(prefs, user)
            prefs[IS_LOGGED_IN_KEY] = true

            prefs[MULTI_LOGIN_DEVICES_KEY] = devicesJson
            prefs[MULTI_LOGIN_USERNAME_KEY] = user.username
            prefs[MULTI_LOGIN_PASSWORD_KEY] = password
            prefs[MULTI_LOGIN_DEVICE_KEY] = user.device

            // Saved credentials are also used as a migration fallback for users
            // coming from versions where multi-login keys may not exist.
            prefs[SAVED_USERNAME_KEY] = user.username
            prefs[SAVED_PASSWORD_KEY] = password
        }
    }

    /** Updates user/account values while preserving the already saved credentials. */
    suspend fun saveUser(user: User) {
        context.userDataStore.edit { prefs ->
            writeUser(prefs, user)
            prefs[IS_LOGGED_IN_KEY] = true
        }
    }

    private fun writeUser(prefs: MutablePreferences, user: User) {
        prefs[USER_ID_KEY] = user.id
        prefs[USERNAME_KEY] = user.username
        prefs[DEVICE_KEY] = user.device
        prefs[DEVICES_LIST_KEY] = user.devices ?: ""
        prefs[CREATION_DATE_KEY] = user.creationDate
        prefs[EXP_DATE_KEY] = user.expDate
        prefs[TOTAL_TRAFICK_KEY] = user.totalTraffic
        prefs[REMAIN_TRAFICK_KEY] = user.remainTraffic
        prefs[GROUP_NAME_KEY] = user.groupName
        prefs[REMAIN_DAYS_KEY] = user.remainDays.toString()
        prefs[REMAIN_DEVICE_KEY] = user.remainDevice
        prefs[IS_EXPIRED_KEY] = user.isExpired
    }

    suspend fun saveSavedCredentials(username: String, password: String) {
        context.userDataStore.edit {
            it[SAVED_USERNAME_KEY] = username
            it[SAVED_PASSWORD_KEY] = password
        }
    }

    suspend fun getSavedCredentials(): Pair<String?, String?> {
        val prefs = context.userDataStore.data.first()
        return prefs[SAVED_USERNAME_KEY] to prefs[SAVED_PASSWORD_KEY]
    }

    /**
     * Reads a complete credential set for automatic login.
     * Prefer the current multi-login keys, then fall back to the saved username/password
     * plus the device stored in the authenticated user record.
     */
    suspend fun getAutoLoginCredentials(): AutoLoginCredentials? {
        val prefs = context.userDataStore.data.first()

        val multiUsername = prefs[MULTI_LOGIN_USERNAME_KEY]
        val multiPassword = prefs[MULTI_LOGIN_PASSWORD_KEY]
        val multiDevice = prefs[MULTI_LOGIN_DEVICE_KEY]
        if (!multiUsername.isNullOrBlank() && !multiPassword.isNullOrBlank() && !multiDevice.isNullOrBlank()) {
            return AutoLoginCredentials(multiUsername, multiPassword, multiDevice)
        }

        val savedUsername = prefs[SAVED_USERNAME_KEY]
        val savedPassword = prefs[SAVED_PASSWORD_KEY]
        val savedDevice = prefs[DEVICE_KEY]
        if (!savedUsername.isNullOrBlank() && !savedPassword.isNullOrBlank() && !savedDevice.isNullOrBlank()) {
            return AutoLoginCredentials(savedUsername, savedPassword, savedDevice)
        }

        return null
    }

    suspend fun logout() {
        context.userDataStore.edit { prefs ->
            prefs.remove(IS_LOGGED_IN_KEY)
            prefs.remove(USER_ID_KEY)
            prefs.remove(USERNAME_KEY)
            prefs.remove(DEVICE_KEY)
            prefs.remove(DEVICES_LIST_KEY)
            prefs.remove(CREATION_DATE_KEY)
            prefs.remove(EXP_DATE_KEY)
            prefs.remove(TOTAL_TRAFICK_KEY)
            prefs.remove(REMAIN_TRAFICK_KEY)
            prefs.remove(GROUP_NAME_KEY)
            prefs.remove(REMAIN_DAYS_KEY)
            prefs.remove(REMAIN_DEVICE_KEY)
            prefs.remove(IS_EXPIRED_KEY)
            prefs.remove(MULTI_LOGIN_DEVICES_KEY)
            prefs.remove(MULTI_LOGIN_USERNAME_KEY)
            prefs.remove(MULTI_LOGIN_PASSWORD_KEY)
            prefs.remove(MULTI_LOGIN_DEVICE_KEY)
            // Keep SAVED_USERNAME/PASSWORD so Login can be pre-filled after logout.
        }
    }

    suspend fun saveMultiLoginDevices(devices: List<String>) {
        val json = Json.encodeToString(devices)
        context.userDataStore.edit { it[MULTI_LOGIN_DEVICES_KEY] = json }
    }

    suspend fun getMultiLoginDevices(): List<String>? {
        val json = context.userDataStore.data.first()[MULTI_LOGIN_DEVICES_KEY] ?: return null
        return runCatching { Json.decodeFromString<List<String>>(json) }
            .getOrDefault(emptyList())
    }

    val multiLoginDevicesFlow: Flow<List<String>> = context.userDataStore.data
        .map { prefs ->
            val json = prefs[MULTI_LOGIN_DEVICES_KEY]
            if (json != null) {
                runCatching { Json.decodeFromString<List<String>>(json) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }

    suspend fun getMultiLoginCredentials(): Triple<String?, String?, String?> {
        val prefs = context.userDataStore.data.first()
        return Triple(
            prefs[MULTI_LOGIN_USERNAME_KEY],
            prefs[MULTI_LOGIN_PASSWORD_KEY],
            prefs[MULTI_LOGIN_DEVICE_KEY]
        )
    }

    suspend fun saveMultiLoginCredentials(username: String, password: String, device: String) {
        context.userDataStore.edit {
            it[MULTI_LOGIN_USERNAME_KEY] = username
            it[MULTI_LOGIN_PASSWORD_KEY] = password
            it[MULTI_LOGIN_DEVICE_KEY] = device
        }
    }

    suspend fun clearMultiLoginData() {
        context.userDataStore.edit {
            it.remove(MULTI_LOGIN_DEVICES_KEY)
            it.remove(MULTI_LOGIN_USERNAME_KEY)
            it.remove(MULTI_LOGIN_PASSWORD_KEY)
            it.remove(MULTI_LOGIN_DEVICE_KEY)
        }
    }

    suspend fun updateMultiLoginDevices(updatedDevices: List<String>) {
        saveMultiLoginDevices(updatedDevices)
    }
}
