package com.example.fulluikotlin.ui.commponent.dialog


import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pw.fullvpn.android.R
import com.example.fulluikotlin.domain.model.ExpiryType
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors

@Composable
fun ExpiryDialog(
    type: ExpiryType,
    username: String? = null,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    onDismiss: () -> Unit = onSecondaryClick
) {
    val usernamePrefix = username
        ?.takeIf { it.isNotBlank() }
        ?.let { "کاربر $it، " }
        ?: ""

    if (username != null) {
        Log.e("EXPIRE",username)
    }
    val (title, description, primaryButtonText, secondaryButtonText) = when (type) {
        ExpiryType.VOLUME_EXPIRED -> {
            listOf(
                "حجم شما تمام شد",
                "${usernamePrefix}حجم ترافیک شما به پایان رسیده است. لطفاً برای ادامه خرید کنید.",
                "خرید حجم",
                "خروج از حساب"
            )
        }

        ExpiryType.DATE_EXPIRED -> {
            listOf(
                "اشتراک شما منقضی شد",
                "${usernamePrefix}تاریخ اعتبار اکانت شما به پایان رسیده. برای تمدید اقدام کنید.",
                "تمدید اشتراک",
                "خروج از حساب"
            )
        }

        ExpiryType.DATE_EXPIRING_SOON -> {
            listOf(
                "اشتراک رو به اتمام است",
                "${usernamePrefix}کمتر از ۲۴ ساعت تا پایان اعتبار اکانت شما باقی مانده است. برای جلوگیری از قطع سرویس، اشتراک را تمدید کنید.",
                "تمدید اشتراک",
                "باشه"
            )
        }
    }
    Dialog(
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        onDismissRequest = {}
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.fullColors.bgBottomSheet
                )
            ) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.fullColors.bgBottomSheet)
                        .padding(20.dp)
                ) {

                    Card(
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.fullColors.bgDiscExitDevice
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 15.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.fullColors.textTitleDevicesActive,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                            )
                        }
                    }

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.fullColors.whitBlack,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        modifier = Modifier
                            .padding(vertical = 20.dp)
                            .fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        OutlinedButton(
                            onClick = onSecondaryClick,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.fullColors.buttonDismissDialog
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = secondaryButtonText,
                                modifier = Modifier.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fullColors.textDismissDialog,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                            )
                        }

                        Spacer(modifier = Modifier.width(15.dp))

                        Button(
                            onClick = onPrimaryClick,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.fullColors.buttonAcceptDialog
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.fullColors.buttonAcceptDialog
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = primaryButtonText,
                                modifier = Modifier.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fullColors.textAcceptDialog,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                            )
                        }
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun CustomExpiryDialogPreview() {
    FullKotlinTheme(true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE0E0E0))
        ) {
            ExpiryDialog(
                type = ExpiryType.DATE_EXPIRED,
                onPrimaryClick = { },
                onSecondaryClick = { },
                onDismiss = { }
            )
        }
    }
}
