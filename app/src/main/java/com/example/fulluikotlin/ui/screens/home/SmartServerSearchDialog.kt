package com.example.fulluikotlin.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.fulluikotlin.ui.theme.fullColors
import pw.fullvpn.android.R

@Composable
fun SmartServerSearchDialog(
    state: SmartServerSearchState,
    onCancel: () -> Unit,
    onConnectCurrentBest: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit
) {
    when (state) {
        SmartServerSearchState.Idle -> Unit
        is SmartServerSearchState.Searching -> SearchingDialogContent(
            state = state,
            onCancel = onCancel,
            onConnectCurrentBest = onConnectCurrentBest
        )
        is SmartServerSearchState.Error -> ErrorDialogContent(
            message = state.message,
            onRetry = onRetry,
            onDismiss = onDismissError
        )
    }
}

@Composable
private fun SearchingDialogContent(
    state: SmartServerSearchState.Searching,
    onCancel: () -> Unit,
    onConnectCurrentBest: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.fullColors.bgServesList
                )
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = if (state.connectingToCurrentBest) {
                            "در حال آماده‌سازی اتصال"
                        } else if (state.isCancelling) {
                            "در حال توقف بررسی"
                        } else {
                            "در حال پیدا کردن بهترین سرور"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.fullColors.whitBlack,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        textAlign = TextAlign.Right
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "برای اندازه‌گیری واقعی، اتصال سرورها هم‌زمان بررسی می‌شود.",
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.fullColors.pingServers,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                        textAlign = TextAlign.Right
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val progress = if (state.total > 0) {
                        state.completed.toFloat() / state.total.toFloat()
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.fullColors.purple,
                        trackColor = MaterialTheme.fullColors.purple.copy(alpha = 0.16f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "${state.completed} سرور از ${state.total} سرور بررسی شد",
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.fullColors.whitBlack,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_medium)),
                        textAlign = TextAlign.Right
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = state.bestServerName?.let { name ->
                            val delay = state.bestDelayMs
                                .takeIf { it > 0 }
                                ?.let { " • $it میلی‌ثانیه" }
                                .orEmpty()
                            "بهترین سرور تا این لحظه: $name$delay"
                        } ?: "هنوز سرور مناسبی پیدا نشده است",
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.fullColors.whitBlack,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_medium)),
                        textAlign = TextAlign.Right
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onConnectCurrentBest,
                            enabled = state.bestServerName != null && !state.isCancelling,
                            modifier = Modifier.weight(1.6f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.fullColors.purple
                            )
                        ) {
                            Text(
                                text = "اتصال به بهترین فعلی",
                                fontFamily = FontFamily(Font(R.font.yekanbakh_medium))
                            )
                        }

                        OutlinedButton(
                            onClick = onCancel,
                            enabled = !state.isCancelling,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "لغو",
                                fontFamily = FontFamily(Font(R.font.yekanbakh_medium))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorDialogContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.fullColors.bgServesList
                )
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "بررسی بهترین سرور ناموفق بود",
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.fullColors.whitBlack,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        textAlign = TextAlign.Right
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.fullColors.pingServers,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                        textAlign = TextAlign.Right
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.fullColors.purple
                            )
                        ) {
                            Text(
                                text = "تلاش مجدد",
                                fontFamily = FontFamily(Font(R.font.yekanbakh_medium))
                            )
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "بستن",
                                fontFamily = FontFamily(Font(R.font.yekanbakh_medium))
                            )
                        }
                    }
                }
            }
        }
    }
}
