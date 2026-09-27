package com.example.fulluikotlin.ui.screens.aboutus

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.koin.androidx.compose.koinViewModel
import pw.fullvpn.android.R
import pw.fullvpn.android.ui.screens.aboutus.AboutUsViewModel
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors
import com.example.fulluikotlin.ui.utils.LinkOpener


@Composable
fun AboutUsScreen(
    navController: NavHostController,
    onItemClick: () -> Unit,
    viewModel: AboutUsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // ساخت لیست شبکه‌های اجتماعی (تنها مواردی که لینک معتبر دارند)
    val socialItems = buildList {
        if (uiState.telegramLink.isNotBlank()) {
            add(SocialMedia(R.drawable.ic_telegram, uiState.telegramLink, "telegram"))
        }
        if (uiState.telegramSupportLink.isNotBlank()) {
            add(SocialMedia(R.drawable.ic_telegram, uiState.telegramSupportLink, "telegramSupport"))
        }
        if (uiState.instagramLink.isNotBlank()) {
            add(SocialMedia(R.drawable.ic_inestagram, uiState.instagramLink, "instagram"))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {


        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.fullColors.borderCardAccountProfile
                ),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgCardAccountProfile)
        ) {

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Text(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(color = MaterialTheme.fullColors.purple)
                    ) {
                        append("Teh ")
                    }
                    withStyle(
                        style = SpanStyle(color = MaterialTheme.fullColors.whitBlack)
                    ) {
                        append("VPN")
                    }
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily(Font(R.font.poppins_bold)),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Text(
                text = uiState.aboutUs.ifEmpty {
                    "ما یک سرویس VPN امن و پایدار هستیم که با تمرکز بر حریم خصوصی، سرعت بالا و دسترسی بدون محدودیت ساخته شده\u200Cایم. هدف ما اینه که کاربران بتونن با خیال راحت، بدون پیچیدگی و بدون نگرانی از امنیت اطلاعات\u200Cشون، به اینترنت آزاد متصل بشن.\n" +
                            "در توسعه این اپلیکیشن سعی کردیم:\n" +
                            "- رابط کاربری ساده و شفاف باشه\n" +
                            "- اتصال سریع و پایدار فراهم کنیم\n" +
                            "- کمترین داده\u200Cی ممکن از کاربر ذخیره بشه"
                },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.fullColors.txtDiscAboutUs,
                textAlign = TextAlign.Right,
                lineHeight = TextUnit(33f, TextUnitType.Sp),
                fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(15.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.fullColors.borderCardAccountProfile
                ),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgCardAccountProfile)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                LazyRow(
                    modifier = Modifier,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(socialItems) { socialMedia ->
                        SocialMediaItems(socialMedia, context)
                    }
                }

                Text(
                    text = "شبکه های  اجتماعی : ",
                    modifier = Modifier,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fullColors.whitBlack,
                    textAlign = TextAlign.Start,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                )
            }
        }
    }
}

@Composable
fun SocialMediaItems(socialMedia: SocialMedia, context: Context) {
    Icon(
        painter = painterResource(socialMedia.iconRes),
        contentDescription = "Social Icon",
        tint = MaterialTheme.fullColors.iconSocailMediaAboutUs,
        modifier = Modifier
            .padding(end = 10.dp)
            .size(25.dp)
            .clickable {
                when (socialMedia.type) {
                    "telegram", "telegramSupport" -> LinkOpener.openTelegram(context, socialMedia.link)
                    "instagram" -> LinkOpener.openInstagram(context, socialMedia.link)
                }
            }
    )
}


data class SocialMedia(
    val iconRes: Int,
    val link: String,
    val type: String
)

@Preview(showBackground = true)
@Composable
fun AboutUsPreview() {
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

            AboutUsScreen(
                navController = rememberNavController(),
                onItemClick = {}
            )
        }
    }
}
