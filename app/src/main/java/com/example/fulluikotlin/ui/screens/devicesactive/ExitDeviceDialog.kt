package com.example.fulluikotlin.ui.screens.devicesactive

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pw.fullvpn.android.R
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors

@Composable
fun ExitDeviceDialog(
    device: Devices,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = {
            if (!isLoading) onDismiss()
        }
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
                                text = " دستگاه : ${
                                    device.deviceName.substringAfter(
                                        '_',
                                        device.deviceName
                                    ).replace("-", " ")
                                }",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.fullColors.textTitleDevicesActive,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                            )
                            Text(
                                text = "آخرین فعالیت: ${device.lastActive}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.fullColors.textLastActiveDevicesActive,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "آیا از خروج این دستگاه مطمئن هستید؟",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.fullColors.whitBlack,
                            fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 5.dp)
                        )

                        Icon(
                            painter = painterResource(R.drawable.ic_info),
                            contentDescription = "iconCardAccount",
                            tint = MaterialTheme.fullColors.whitBlack,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // دکمه‌ها
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.fullColors.buttonBackExitDialog
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "بازگشت به قبل",
                                modifier = Modifier.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fullColors.buttonBackExitDialog,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                            )
                        }

                        Spacer(modifier = Modifier.width(15.dp))

                        OutlinedButton(
                            onClick = onConfirm,
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.fullColors.red3
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    text = "خروج",
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.fullColors.red3,
                                    textAlign = TextAlign.End,
                                    fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                                )
                            }
                        }
                    }
                    if (errorMessage != null) {
                        Log.e("erorr", errorMessage)
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun CustomExitDialogPreview() {
    FullKotlinTheme(true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE0E0E0))
        ) {
            // دیالوگ باز
            ExitDeviceDialog(
                device = Devices(
                    "2",
                    "Samsung Galaxy A33",
                    "1404/10/20 ، 17:42"
                ),
                isLoading = false,
                errorMessage = "",
                onDismiss = {},
                onConfirm = {}
            )
        }
    }
}
