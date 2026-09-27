package com.example.fulluikotlin.ui.screens.activeservice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.koin.androidx.compose.koinViewModel
import pw.fullvpn.android.R
import com.example.fulluikotlin.ui.screens.devicesactive.DevicesActiveBottomSheet
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors
import com.example.fulluikotlin.ui.utils.LinkOpener
import com.example.fulluikotlin.ui.utils.gregorianToJalali


@Composable
fun ActiveServiceScreen(
    navController: NavHostController,
    onItemClick: () -> Unit,
    viewModel: ActiveServiceViewModel = koinViewModel()
) {
    var showSheet by remember { mutableStateOf(false) }
    val devices by viewModel.devices.collectAsState()  // 👈 راکتیو
    val user = viewModel.user
    val context = LocalContext.current
    val rechargeAccLinkFlow by viewModel.rechargeAccLinkFlow.collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "اطلاعات بسته فعال",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.fullColors.whitBlack,
                textAlign = TextAlign.Start,
                fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                modifier = Modifier.weight(1f)
            )

            Icon(
                painter = painterResource(R.drawable.ic_info_account),
                contentDescription = "iconArrowItemsAccount",
                tint = MaterialTheme.fullColors.whitBlack,
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 5.dp)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.fullColors.borderCardAccountProfile,
                    shape = RoundedCornerShape(8.dp)
                ),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgCardAccountProfile)
        ) {

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = "iconArrowItemsAccount",
                    tint = MaterialTheme.fullColors.iconActiveService,
                    modifier = Modifier.size(24.dp)
                )


                Text(
                    text = user?.username ?: "test",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fullColors.textActiveService,
                    textAlign = TextAlign.End,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )

                Text(
                    text = "نام کاربری :",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fullColors.textActiveService,
                    textAlign = TextAlign.Start,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                    modifier = Modifier
                        .padding(end = 10.dp)
                )

                Icon(
                    painter = painterResource(R.drawable.ic_username_service),
                    contentDescription = "iconCardAccount",
                    tint = MaterialTheme.fullColors.iconTitleActiveService,
                    modifier = Modifier.size(24.dp)
                )
            }

            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 20.dp),
                color = MaterialTheme.fullColors.dividerActiveService,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = formatGregorianToJalali(user?.creationDate).ifEmpty { "test" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fullColors.textActiveService,
                    textAlign = TextAlign.End,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 2.dp, end = 10.dp)
                )

                Text(
                    text = "تاریخ ساخت اشتراک: ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fullColors.textActiveService,
                    textAlign = TextAlign.Start,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                    modifier = Modifier
                        .padding(end = 10.dp)
                )

                Icon(
                    painter = painterResource(R.drawable.ic_calendar),
                    contentDescription = "iconCardAccount",
                    tint = MaterialTheme.fullColors.iconTitleActiveService,
                    modifier = Modifier.size(24.dp)
                )
            }

            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 20.dp),
                color = MaterialTheme.fullColors.dividerActiveService,
            )


            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = formatGregorianToJalali(user?.expDate).ifEmpty { "test" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fullColors.textActiveService,
                    textAlign = TextAlign.End,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 2.dp, end = 10.dp)
                )

                Text(
                    text = "تاریخ اتمام اشتراک:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fullColors.textActiveService,
                    textAlign = TextAlign.Start,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                    modifier = Modifier
                        .padding(end = 10.dp)
                )

                Icon(
                    painter = painterResource(R.drawable.ic_calendar),
                    contentDescription = "iconCardAccount",
                    tint = MaterialTheme.fullColors.iconTitleActiveService,
                    modifier = Modifier.size(24.dp)
                )
            }

            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 20.dp),
                color = MaterialTheme.fullColors.dividerActiveService,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "${formatRemainDays(user?.remainDays)} روز",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fullColors.textActiveService,
                    textAlign = TextAlign.End,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 2.dp, end = 10.dp)
                )
                Text(
                    text = "روزهای باقی مانده :",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fullColors.textActiveService,
                    textAlign = TextAlign.Start,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                    modifier = Modifier
                        .padding(end = 10.dp)
                )

                Icon(
                    painter = painterResource(R.drawable.ic_remings_service),
                    contentDescription = "iconCardAccount",
                    tint = MaterialTheme.fullColors.iconTitleActiveService,
                    modifier = Modifier.size(24.dp)
                )
            }

            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 20.dp),
                color = MaterialTheme.fullColors.dividerActiveService,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 15.dp)
                    .clickable {
                        showSheet = true
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "iconArrowItemsAccount",
                    tint = MaterialTheme.fullColors.iconActiveService,
                    modifier = Modifier
                )


                Text(
                    text = "${devices.size} دستگاه",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fullColors.purple2,
                    textAlign = TextAlign.Start,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )

                Text(
                    text = "دستگاه های فعال :",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fullColors.textActiveService,
                    textAlign = TextAlign.Start,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                    modifier = Modifier
                        .padding(end = 10.dp)
                )

                Icon(
                    painter = painterResource(R.drawable.ic_divices),
                    contentDescription = "iconCardAccount",
                    tint = MaterialTheme.fullColors.iconTitleActiveService,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(
                modifier = Modifier.height(5.dp)
            )

        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedButton(
                onClick = {
                    showSheet = true
                },
                modifier = Modifier
                    .weight(1f),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = MaterialTheme.fullColors.buttonActiveService // رنگ دلخواه بردر
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "دستگاه های فعال",
                    modifier = Modifier.padding(vertical = 5.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.fullColors.buttonActiveService,
                    textAlign = TextAlign.End,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                )
            }

            Spacer(modifier = Modifier.width(15.dp))

            Button(
                onClick = {
                    if (!rechargeAccLinkFlow.isNullOrBlank()) {
                        LinkOpener.openTelegram(context, rechargeAccLinkFlow)
                    }
                },
                modifier = Modifier
                    .weight(1f),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = MaterialTheme.fullColors.purple // رنگ دلخواه بردر
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.fullColors.purple
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "تمدید اشتراک",
                    modifier = Modifier.padding(vertical = 5.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.fullColors.whit,
                    textAlign = TextAlign.End,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                )

                Spacer(modifier = Modifier.width(5.dp))

                Icon(
                    painter = painterResource(R.drawable.ic_renewal),
                    contentDescription = "iconLoginButton",
                    tint = MaterialTheme.fullColors.whit,
                    modifier = Modifier
                        .size(15.dp)
                        .offset(y = (-2).dp)
                )
            }

        }

    }

    if (showSheet) {
        DevicesActiveBottomSheet(
            devices = devices,
            onDismiss = { showSheet = false }
        )
    }

}
fun formatRemainDays(value: Float?): String {
    val safeValue = (value ?: 0f).coerceAtLeast(0f)
    return safeValue.toInt().toString()
}

fun formatGregorianToJalali(gregorianDate: String?): String {
    if (gregorianDate.isNullOrBlank()) return ""
    val parts = gregorianDate.split("-")
    if (parts.size != 3) return gregorianDate // بازگشت همان ورودی در صورت فرمت نامعتبر
    val year = parts[0].toIntOrNull() ?: return gregorianDate
    val month = parts[1].toIntOrNull() ?: return gregorianDate
    val day = parts[2].toIntOrNull() ?: return gregorianDate
    val jalali = gregorianToJalali(year, month, day)
    // فرمت نهایی با دو رقم برای ماه و روز و اسلش
    return "${jalali.year}/${jalali.month.toString().padStart(2, '0')}/${
        jalali.day.toString().padStart(2, '0')
    }"
}

@Preview(showBackground = true)
@Composable
fun ActiveServicePreview() {
    FullKotlinTheme(true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.fullColors.background)
        ) {

            Image(
                painter = painterResource(R.drawable.img_map_dark),
                contentDescription = "BgImage",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.fullColors.shadowBackground,
                                Color.Transparent
                            ),
                            center = Offset(0f, 0f),
                            radius = 1800f
                        )
                    )
            )

            ActiveServiceScreen(
                navController = rememberNavController(),
                onItemClick = {}
            )
        }
    }
}