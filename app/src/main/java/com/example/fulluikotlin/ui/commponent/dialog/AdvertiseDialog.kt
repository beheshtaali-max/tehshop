package com.example.fulluikotlin.ui.commponent.dialog


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import pw.fullvpn.android.R
import com.example.fulluikotlin.domain.model.PopupAd
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors

@Composable
fun AdvertiseDialog(
    popupAd: PopupAd,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit
) {

    var secondsLeft by remember { mutableIntStateOf(10) }
    var isActionTriggered by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {
        while (secondsLeft > 0 && !isActionTriggered) {
            delay(1000)
            secondsLeft--
        }
        if (secondsLeft == 0 && !isActionTriggered) {
            isActionTriggered = true
            onOpenLink(popupAd.url)
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
                                text = "تبلیغات ${popupAd.type}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.fullColors.textTitleDevicesActive,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                            )
                        }
                    }

                    Text(
                        text = "در $secondsLeft ثانیه دیگر به صفحه تبلیغات هدایت می‌شوید.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.fullColors.whitBlack,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        modifier = Modifier
                            .padding(top = 20.dp, bottom = 25.dp)
                            .fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (!isActionTriggered) {
                                    isActionTriggered = true
                                    onOpenLink(popupAd.url)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 25.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.fullColors.buttonDismissDialog
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "همین حالا برو",
                                modifier = Modifier.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fullColors.textDismissDialog,
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
fun CustomAdvertiseDialogPreview() {
    FullKotlinTheme(true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE0E0E0))
        ) {

            AdvertiseDialog(
                popupAd = PopupAd(
                    type = "",
                    url = "",
                    startTime = 9L,
                    expTime = 10L,
                    id = ""
                ),
                onDismiss = {},
                onOpenLink = {}
            )
        }
    }
}
