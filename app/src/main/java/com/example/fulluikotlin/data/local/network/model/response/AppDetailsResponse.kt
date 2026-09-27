package com.example.fulluikotlin.data.local.network.model.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AppDetailsResponse(
    val STATUS: String,
    val telegram_support_id: String? = null,
    val telegram_channel_id: String? = null,
    val buy_acc_link: String? = null,
    val test_acc_link: String? = null,
    val recharge_acc_link: String? = null,
    val instagram_link: String? = null,
    val popupstatus: String? = null,
    val defaultprotocol: String? = null,
    val usagePeriod: JsonElement? = null,
    val updatedetails: UpdateDetailsResponse? = null,
    val customdialogstatus: CustomDialogStatusResponse? = null,
    val windowsupdatedetails: UpdateDetailsResponse? = null,
    val blockedapp: List<BlockedAppResponse>? = null,
    val about_us: String? = null,
    val faq: List<FaqResponse>? = null
)

@Serializable
data class UpdateDetailsResponse(
    val updateversion: String? = null,
    val updatetitle: String? = null,
    val updatedescription: String? = null,
    val updatesize: String? = null,
    val updatelink: String? = null,
    val ForcedUpdate: String? = null
)

@Serializable
data class CustomDialogStatusResponse(
    val customdialogstatus: String? = null,
    val customdialogid: String? = null,
    val customdialogtitle: String? = null,
    val customdialogdescription: String? = null,
    val customdialogbuttontext: String? = null,
    val customdialogbuttonlink: String? = null,
    val customdialogtime: String? = null,
    val customdialogstarttime: String? = null,
    val customdialogusesteps: String? = null,
    val forceshowcustomdialog: String? = null
)

@Serializable
data class BlockedAppResponse(
    val name: String,
    val app: String
)

@Serializable
data class FaqResponse(
    val question: String,
    val answer: String
)
