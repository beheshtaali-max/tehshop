package com.example.fulluikotlin.domain.model

import kotlinx.serialization.Serializable

data class AppDetails(
    val telegramSupportId: String,
    val telegramChannelId: String,
    val buyAccLink: String,
    val testAccLink: String,
    val rechargeAccLink: String,
    val instagramLink: String,
    val popupStatus: Boolean,
    val defaultProtocol: String,
    val usagePeriodMinutes: Long,
    val updateDetails: UpdateDetails?,
    val windowsUpdateDetails: UpdateDetails?,
    val customDialogStatus: CustomDialogStatus?,
    val blockedApps: List<BlockedApp>,
    val aboutUs: String,
    val faq: List<Faq>
)

data class UpdateDetails(
    val version: String,
    val title: String,
    val description: String,
    val size: String,
    val link: String,
    val forcedUpdate: Boolean
)

data class CustomDialogStatus(
    val status: String,
    val id: String,
    val title: String,
    val description: String,
    val buttonText: String,
    val buttonLink: String,
    val time: String,
    val startTime: Long,
    val useSteps: Int,
    val forceShow: String
)

@Serializable
data class BlockedApp(
    val name: String,
    val packageName: String
)

@Serializable
data class Faq(
    val question: String,
    val answer: String
)