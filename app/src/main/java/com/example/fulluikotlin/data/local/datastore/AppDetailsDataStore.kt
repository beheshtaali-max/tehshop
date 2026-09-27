package com.example.fulluikotlin.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fulluikotlin.domain.model.Faq
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json


private val Context.appDetailsDataStore by preferencesDataStore(name = "app_details")

class AppDetailsDataStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val TELEGRAM_SUPPORT_ID = stringPreferencesKey("telegram_support_id")
    private val TELEGRAM_CHANNEL_ID = stringPreferencesKey("telegram_channel_id")
    private val BUY_ACC_LINK = stringPreferencesKey("buy_acc_link")
    private val TEST_ACC_LINK = stringPreferencesKey("test_acc_link")
    private val RECHARGE_ACC_LINK = stringPreferencesKey("recharge_acc_link")
    private val INSTAGRAM_LINK = stringPreferencesKey("instagram_link")
    private val DEFAULT_PROTOCOL = stringPreferencesKey("defaultprotocol")
    private val USAGE_PERIOD_MINUTES = longPreferencesKey("usage_period_minutes")
    private val ABOUT_US = stringPreferencesKey("about_us")
    private val FAQ_LIST = stringPreferencesKey("faq_list")

    val telegramSupportIdFlow: Flow<String?> = context.appDetailsDataStore.data
        .map { it[TELEGRAM_SUPPORT_ID] }

    val telegramChannelIdFlow: Flow<String?> = context.appDetailsDataStore.data
        .map { it[TELEGRAM_CHANNEL_ID] }

    val buyAccLinkFlow: Flow<String?> = context.appDetailsDataStore.data
        .map { it[BUY_ACC_LINK] }

    val testAccLinkFlow: Flow<String?> = context.appDetailsDataStore.data
        .map { it[TEST_ACC_LINK] }

    val rechargeAccLinkFlow: Flow<String?> = context.appDetailsDataStore.data
        .map { it[RECHARGE_ACC_LINK] }

    val instagramLinkFlow: Flow<String?> = context.appDetailsDataStore.data
        .map { it[INSTAGRAM_LINK] }

    val defaultProtocolFlow: Flow<String?> = context.appDetailsDataStore.data
        .map { it[DEFAULT_PROTOCOL] }

    val usagePeriodMinutesFlow: Flow<Long> = context.appDetailsDataStore.data
        .map { prefs ->
            (prefs[USAGE_PERIOD_MINUTES] ?: DEFAULT_USAGE_PERIOD_MINUTES).coerceAtLeast(1L)
        }

    val aboutUsFlow: Flow<String?> = context.appDetailsDataStore.data
        .map { it[ABOUT_US] }

    val faqListFlow: Flow<List<Faq>> = context.appDetailsDataStore.data
        .map { prefs ->
            prefs[FAQ_LIST]?.let { json.decodeFromString<List<Faq>>(it) } ?: emptyList()
        }


    suspend fun saveTelegramSupportId(value: String) {
        context.appDetailsDataStore.edit { it[TELEGRAM_SUPPORT_ID] = value }
    }

    suspend fun saveTelegramChannelId(value: String) {
        context.appDetailsDataStore.edit { it[TELEGRAM_CHANNEL_ID] = value }
    }

    suspend fun saveBuyAccLink(value: String) {
        context.appDetailsDataStore.edit { it[BUY_ACC_LINK] = value }
    }

    suspend fun saveTestAccLink(value: String) {
        context.appDetailsDataStore.edit { it[TEST_ACC_LINK] = value }
    }

    suspend fun saveRechargeAccLink(value: String) {
        context.appDetailsDataStore.edit { it[RECHARGE_ACC_LINK] = value }
    }

    suspend fun saveInstagramLink(value: String) {
        context.appDetailsDataStore.edit { it[INSTAGRAM_LINK] = value }
    }

    suspend fun saveDefaultProtocol(value: String) {
        context.appDetailsDataStore.edit { it[DEFAULT_PROTOCOL] = value }
    }

    suspend fun saveUsagePeriodMinutes(value: Long) {
        context.appDetailsDataStore.edit {
            it[USAGE_PERIOD_MINUTES] = value.coerceAtLeast(1L)
        }
    }

    suspend fun saveAboutUs(value: String) {
        context.appDetailsDataStore.edit { it[ABOUT_US] = value }
    }

    suspend fun saveFaqList(list: List<Faq>) {
        val jsonString = json.encodeToString(list)
        context.appDetailsDataStore.edit { it[FAQ_LIST] = jsonString }
    }


    suspend fun saveAllDetails(
        telegramSupportId: String,
        telegramChannelId: String,
        buyAccLink: String,
        testAccLink: String,
        rechargeAccLink: String,
        instagramLink: String,
        defaultProtocol: String,
        usagePeriodMinutes: Long,
        aboutUs: String,
        faqList: List<Faq>
    ) {
        context.appDetailsDataStore.edit { prefs ->
            prefs[TELEGRAM_SUPPORT_ID] = telegramSupportId
            prefs[TELEGRAM_CHANNEL_ID] = telegramChannelId
            prefs[BUY_ACC_LINK] = buyAccLink
            prefs[TEST_ACC_LINK] = testAccLink
            prefs[RECHARGE_ACC_LINK] = rechargeAccLink
            prefs[INSTAGRAM_LINK] = instagramLink
            prefs[DEFAULT_PROTOCOL] = defaultProtocol
            prefs[USAGE_PERIOD_MINUTES] = usagePeriodMinutes.coerceAtLeast(1L)
            prefs[ABOUT_US] = aboutUs
            prefs[FAQ_LIST] = json.encodeToString(faqList)
        }
    }

    suspend fun clearAll() {
        context.appDetailsDataStore.edit { it.clear() }
    }

    companion object {
        private const val DEFAULT_USAGE_PERIOD_MINUTES = 1L
    }
}