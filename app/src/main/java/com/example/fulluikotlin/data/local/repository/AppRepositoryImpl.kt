package com.example.fulluikotlin.data.local.repository

import com.example.fulluikotlin.data.local.network.api.AppApi
import com.example.fulluikotlin.data.local.network.model.response.AppDetailsResponse
import com.example.fulluikotlin.domain.model.AppDetails
import com.example.fulluikotlin.domain.model.BlockedApp
import com.example.fulluikotlin.domain.model.CustomDialogStatus
import com.example.fulluikotlin.domain.model.Faq
import com.example.fulluikotlin.domain.model.UpdateDetails
import com.example.fulluikotlin.domain.repository.AppRepository
import com.example.fulluikotlin.domain.utils.Resource
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class AppRepositoryImpl(
    private val appApi: AppApi
) : AppRepository {

    override suspend fun getAppDetails(): Resource<AppDetails> {
        return try {
            val response = appApi.getAppDetails()
            if (response.STATUS == "success") {
                val details = mapToDomain(response)
                Resource.Success(details)
            } else {
                Resource.Error("خطا در دریافت اطلاعات برنامه")
            }
        } catch (e: Exception) {
            Resource.Exception(e)
        }
    }

    private fun mapToDomain(response: AppDetailsResponse): AppDetails {
        return AppDetails(
            telegramSupportId = response.telegram_support_id ?: "",
            telegramChannelId = response.telegram_channel_id ?: "",
            buyAccLink = response.buy_acc_link ?: "",
            testAccLink = response.test_acc_link ?: "",
            rechargeAccLink = response.recharge_acc_link ?: "",
            instagramLink = response.instagram_link ?: "",
            popupStatus = response.popupstatus == "true",
            defaultProtocol = response.defaultprotocol ?: "",
            usagePeriodMinutes = (response.usagePeriod as? JsonPrimitive)
                ?.contentOrNull
                ?.toDoubleOrNull()
                ?.toLong()
                ?.coerceAtLeast(1L)
                ?: DEFAULT_USAGE_PERIOD_MINUTES,
            updateDetails = response.updatedetails?.takeIf { details ->
                !details.updateversion.isNullOrBlank() && !details.updatelink.isNullOrBlank()
            }?.let {
                UpdateDetails(
                    version = it.updateversion!!,
                    title = it.updatetitle ?: "",
                    description = it.updatedescription ?: "",
                    size = it.updatesize ?: "",
                    link = it.updatelink!!,
                    forcedUpdate = it.ForcedUpdate == "true"
                )
            },
            windowsUpdateDetails = response.windowsupdatedetails?.let {
                UpdateDetails(
                    version = it.updateversion ?: "",
                    title = it.updatetitle ?: "",
                    description = it.updatedescription ?: "",
                    size = it.updatesize ?: "",
                    link = it.updatelink ?: "",
                    forcedUpdate = it.ForcedUpdate == "true"
                )
            },
            customDialogStatus = response.customdialogstatus?.takeIf { customDialog ->
                !customDialog.customdialogbuttontext.isNullOrBlank() && !customDialog.customdialogdescription.isNullOrBlank() && !customDialog.customdialogstatus.equals(
                    "ShowDisable"
                )
            }?.let {
                CustomDialogStatus(
                    status = it.customdialogstatus ?: "",
                    id = it.customdialogid ?: "",
                    title = it.customdialogtitle ?: "",
                    description = it.customdialogdescription ?: "",
                    buttonText = it.customdialogbuttontext ?: "",
                    buttonLink = it.customdialogbuttonlink ?: "",
                    time = it.customdialogtime ?: "",
                    startTime = it.customdialogstarttime?.toLongOrNull() ?: 0,
                    useSteps = it.customdialogusesteps?.toIntOrNull() ?: 0,
                    forceShow = it.forceshowcustomdialog ?: ""
                )
            },
            blockedApps = response.blockedapp?.map {
                BlockedApp(name = it.name, packageName = it.app)
            } ?: emptyList(),
            aboutUs = response.about_us ?: "",
            faq = response.faq?.map {
                Faq(question = it.question, answer = it.answer)
            } ?: emptyList()
        )
    }

    companion object {
        private const val DEFAULT_USAGE_PERIOD_MINUTES = 1L
    }
}