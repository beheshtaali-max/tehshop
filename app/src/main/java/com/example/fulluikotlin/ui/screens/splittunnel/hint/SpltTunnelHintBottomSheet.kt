package com.example.fulluikotlin.ui.screens.splittunnel.hint

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pw.fullvpn.android.R
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelHintBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp
        ),
        containerColor = MaterialTheme.fullColors.bgBottomSheet,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(90.dp)
                    .height(8.dp)
                    .background(
                        color = MaterialTheme.fullColors.dragHandleBottomSheet,
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(
                    color = MaterialTheme.fullColors.bgBottomSheet
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    IconButton(
                        onClick = {
                            onDismiss()
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "closeHintSplit",
                            tint = MaterialTheme.fullColors.GrayDark7,
                            modifier = Modifier
                        )
                    }

                    Row(
                        modifier = Modifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "توجه",
                            modifier = Modifier,
                            color = MaterialTheme.fullColors.red2,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Start,
                            fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                        )

                        IconButton(
                            onClick = { }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_warning_split),
                                contentDescription = "closeHintSplit",
                                tint = Color.Unspecified,
                                modifier = Modifier
                            )
                        }

                    }
                }

                Text(
                    text = "برای اعمال تغییرات پس از انتخاب برنامه و سایت باید یک بار اتصال خود را قطع کنید و دوباره متصل شوید .",
                    modifier = Modifier,
                    color = MaterialTheme.fullColors.textDiscriptionHintSplit,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Start,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                )

                Spacer(
                    modifier = Modifier.height(30.dp)
                )

                Button(
                    onClick = {
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.fullColors.purple
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "متوجه شدم",
                        modifier = Modifier.padding(vertical = 5.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.fullColors.whit,
                        textAlign = TextAlign.End,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                    )
                }

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview(
    showBackground = true,
    showSystemUi = true
)
@Composable
fun SplitTunnelHintBottomSheetPreview() {

    FullKotlinTheme(true) {   // 👈 تم اصلی پروژه‌ات را بگذار

        var showSheet by remember { mutableStateOf(true) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010))
        ) {

            if (showSheet) {
                SplitTunnelHintBottomSheet(
                    onDismiss = { showSheet = false }
                )
            }
        }
    }
}

