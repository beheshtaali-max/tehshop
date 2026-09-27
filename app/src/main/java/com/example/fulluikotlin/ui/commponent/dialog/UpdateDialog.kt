package com.example.fulluikotlin.ui.commponent.dialog


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import com.example.fulluikotlin.ui.utils.ApkUpdateManager
import androidx.compose.runtime.remember
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.height
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
import com.example.fulluikotlin.domain.model.UpdateDetails
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors
import pw.fullvpn.android.BuildConfig

@Composable
fun UpdateDialog(
    model: UpdateDetails,
    onUpdateClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !model.forcedUpdate,
            dismissOnClickOutside = !model.forcedUpdate
        ),
        onDismissRequest = {
            if (!model.forcedUpdate) onDismiss()
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
                                text = model.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.fullColors.textTitleDevicesActive,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                            )
                        }
                    }

                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.fullColors.whitBlack,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        modifier = Modifier
                            .padding(top = 20.dp, bottom = 5.dp)
                            .fillMaxWidth()
                    )

                    Text(
                        text = "- حجم اپدیت : ${model.size}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.fullColors.whitBlack,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                            .fillMaxWidth()
                    )

                    Text(
                        text = "- ورژن اپدیت : ${model.version}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.fullColors.whitBlack,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                            .fillMaxWidth()
                    )

                    Text(
                        text = "- ورژن فعلی : ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.fullColors.whitBlack,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        modifier = Modifier
                            .padding(top = 5.dp, bottom = 25.dp)
                            .fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        if (!model.forcedUpdate) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.fullColors.buttonDismissDialog
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "بعدا",
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.fullColors.textDismissDialog,
                                    textAlign = TextAlign.End,
                                    fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(15.dp))

                        Button(
                            onClick = { onUpdateClick(model.link) },
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
                                text = "آپدیت",
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


@Composable
fun UpdateDownloadingDialog(
    model: UpdateDetails,
    progressPercent: Int?,
    downloadedBytes: Long,
    totalBytes: Long,
    onLaterClick: () -> Unit
) {
    val progressText = progressPercent?.let { "$it٪" } ?: "در حال دانلود"
    val sizeText = remember(downloadedBytes, totalBytes) {
        val downloaded = ApkUpdateManager.formatBytes(downloadedBytes)
        if (totalBytes > 0L) {
            "$downloaded از ${ApkUpdateManager.formatBytes(totalBytes)}"
        } else {
            downloaded
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
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
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
                        Text(
                            text = "دانلود آپدیت",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.fullColors.textTitleDevicesActive,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                            modifier = Modifier
                                .padding(vertical = 15.dp)
                                .fillMaxWidth()
                        )
                    }

                    Text(
                        text = "در حال دانلود نسخه ${model.version}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.fullColors.whitBlack,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        modifier = Modifier
                            .padding(top = 20.dp, bottom = 8.dp)
                            .fillMaxWidth()
                    )

                    if (progressPercent == null) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.fullColors.buttonAcceptDialog,
                            trackColor = MaterialTheme.fullColors.bgDiscExitDevice
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = progressPercent.coerceIn(0, 100) / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.fullColors.buttonAcceptDialog,
                            trackColor = MaterialTheme.fullColors.bgDiscExitDevice
                        )
                    }

                    Text(
                        text = "$progressText  •  $sizeText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.fullColors.whitBlack,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        modifier = Modifier
                            .padding(top = 14.dp, bottom = 24.dp)
                            .fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = onLaterClick,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.fullColors.buttonDismissDialog
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "بعدا؛ دانلود ادامه داشته باشد",
                            modifier = Modifier.padding(vertical = 2.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.fullColors.textDismissDialog,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateReadyDialog(
    model: UpdateDetails,
    onInstallClick: () -> Unit,
    onLaterClick: () -> Unit
) {
    Dialog(
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        onDismissRequest = onLaterClick
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
                        Text(
                            text = "دانلود کامل شد",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.fullColors.textTitleDevicesActive,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                            modifier = Modifier
                                .padding(vertical = 15.dp)
                                .fillMaxWidth()
                        )
                    }

                    Text(
                        text = "فایل آپدیت نسخه ${model.version} با موفقیت دانلود شد. برای نصب، روی دکمه زیر کلیک کنید.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.fullColors.whitBlack,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        modifier = Modifier
                            .padding(top = 20.dp, bottom = 25.dp)
                            .fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onLaterClick,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.fullColors.buttonDismissDialog
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "بعدا",
                                modifier = Modifier.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fullColors.textDismissDialog,
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                            )
                        }

                        Spacer(modifier = Modifier.width(15.dp))

                        Button(
                            onClick = onInstallClick,
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
                                text = "نصب آپدیت",
                                modifier = Modifier.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fullColors.textAcceptDialog,
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateFailedDialog(
    model: UpdateDetails,
    message: String,
    onRetryClick: () -> Unit,
    onLaterClick: () -> Unit
) {
    Dialog(
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        onDismissRequest = onLaterClick
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
                        Text(
                            text = "خطا در دانلود",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.fullColors.textTitleDevicesActive,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                            modifier = Modifier
                                .padding(vertical = 15.dp)
                                .fillMaxWidth()
                        )
                    }

                    Text(
                        text = message.ifBlank { "دانلود آپدیت انجام نشد. لطفاً دوباره تلاش کنید." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.fullColors.whitBlack,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        modifier = Modifier
                            .padding(top = 20.dp, bottom = 25.dp)
                            .fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onLaterClick,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.fullColors.buttonDismissDialog
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "بعدا",
                                modifier = Modifier.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fullColors.textDismissDialog,
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                            )
                        }

                        Spacer(modifier = Modifier.width(15.dp))

                        Button(
                            onClick = onRetryClick,
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
                                text = "تلاش مجدد",
                                modifier = Modifier.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fullColors.textAcceptDialog,
                                textAlign = TextAlign.Center,
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
fun CustomUpdateDialogPreview() {
    FullKotlinTheme(true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE0E0E0))
        ) {
            UpdateDialog(
                model = UpdateDetails(
                    version = "1.1",
                    title = "عنوان اپدیت جدید اندروید",
                    description = "توضیحات آپدیت جدید اندروید",
                    link = "",
                    forcedUpdate = true,
                    size = "11 Mb"
                ),
                onDismiss = {},
                onUpdateClick = {}
            )
        }
    }
}
