package com.example.fulluikotlin.ui.screens.splash

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.fulluikotlin.ui.main.MainViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import pw.fullvpn.android.R
import pw.fullvpn.android.BuildConfig
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.LocalDarkTheme
import com.example.fulluikotlin.ui.theme.fullColors

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: SplashViewModel? = koinViewModel(),
    mainViewModel: MainViewModel = koinInject()
) {
    val splashViewModel = viewModel ?: koinViewModel()

    val uiState by splashViewModel.uiState.collectAsState()
    val context = LocalContext.current

    // نمایش خطا با Toast (هر بار که errorMessage تغییر کند)
    LaunchedEffect(uiState.errorMessage) {
        if (!uiState.isLoading && uiState.errorMessage != null) {
            Toast.makeText(context, uiState.errorMessage, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(uiState.isLoggedIn, uiState.isLoading) {
        if (!uiState.isLoading && uiState.isLoggedIn != null) {
            if (uiState.isLoggedIn == true) {
                navController.navigate("main") {
                    popUpTo("splash") { inclusive = true }
                }
            } else {
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
    }

    SplashContent(
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        showRetry = uiState.showRetry,
        onRetry = { viewModel?.retryLoading() }
    )
}

@Composable
fun SplashContent(
    isLoading: Boolean,
    errorMessage: String?,
    showRetry: Boolean,
    onRetry: () -> Unit
) {
    val isDarkTheme = LocalDarkTheme.current

    val painterRes = if (isDarkTheme) {
        R.drawable.img_map_dark
    } else {
        R.drawable.img_map_light
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.fullColors.background)
    ) {

        Image(
            painter = painterResource(painterRes),
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

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box() {
                    Image(
                        painter = painterResource(R.drawable.img_logo),
                        contentDescription = "LogoSplash",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(82.dp)
                    )
                }

                Spacer(modifier = Modifier.height(25.dp))

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
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.fullColors.whitBlack,
                                fontSize = 8.sp
                            )
                        ) {
                            append(" v${BuildConfig.VERSION_NAME}")
                        }

                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = FontFamily(Font(R.font.poppins_bold))
                )


            }
            when {
                isLoading -> {
                    Spacer(modifier = Modifier.height(32.dp))
                }

                showRetry && errorMessage != null -> {
                    Spacer(modifier = Modifier.height(32.dp))


                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 25.dp)
                            .padding(bottom = 50.dp, top = 5.dp),
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
                            text = "تلاش مجدد",
                            modifier = Modifier.padding(vertical = 5.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.fullColors.whit,
                            textAlign = TextAlign.End,
                            fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                        )
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    FullKotlinTheme(true) {
        SplashContent(
            isLoading = false,
            errorMessage = "خطا در دریافت اطلاعات",
            showRetry = true,
            onRetry = {}
        )
    }
}