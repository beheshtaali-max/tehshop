package com.example.fulluikotlin.ui.screens.login

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fulluikotlin.ui.commponent.LightningLoading
import com.example.fulluikotlin.ui.screens.devicesactive.DevicesActiveBottomSheet
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors
import com.example.fulluikotlin.ui.utils.LinkOpener
import org.koin.androidx.compose.koinViewModel
import pw.fullvpn.android.R

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel? = null
) {
    val context = LocalContext.current
    val device = remember { getUniqueKey(context) }
    val loginViewModel = viewModel ?: koinViewModel()
    val uiState by loginViewModel.uiState.collectAsState()
    val devices by loginViewModel.devicesFlow.collectAsState()
    val telegramSupportId by loginViewModel.telegramSupportIdFlow.collectAsState(initial = null)

    LaunchedEffect(uiState.isLoginSuccess) {
        if (uiState.isLoginSuccess) {
            navController.navigate("main") {
                popUpTo(0) { inclusive = true }
            }
            loginViewModel.resetLoginState()
        }
    }

    if (uiState.showMultiLoginSheet && devices.isNotEmpty()) {
        DevicesActiveBottomSheet(
            devices = devices,
            onDismiss = {
                loginViewModel.closeMultiLoginSheet()
            }
        )
    }

    LoginContent(
        context = context,
        uiState = uiState,
        onUsernameChange = loginViewModel::onUsernameChange,
        onPasswordChange = loginViewModel::onPasswordChange,
        onLoginClick = {
            Log.e("device", device)
            loginViewModel.login(device)
        },
        telegramSupportId = telegramSupportId
    )
}

@Composable
fun LoginContent(
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    telegramSupportId: String?,
    context: Context
) {
    val context = LocalContext.current


    LaunchedEffect(uiState.errorMessage) {
        if (!uiState.isLoading && uiState.errorMessage != null) {
            Toast.makeText(context, uiState.errorMessage, Toast.LENGTH_LONG).show()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.fullColors.background)
    ) {

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(
                modifier = Modifier.height(30.dp)
            )

            SelectionLogo()

            Spacer(
                modifier = Modifier.height(40.dp)
            )

            Text(
                text = "خوش آمدید :)",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.fullColors.textWelcomeLogin,
                fontFamily = FontFamily(Font(R.font.peyda_semibold))
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = "اتصالی امن ، راحت و بدون دردسر",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.fullColors.textDiscriptLogin,
                textAlign = TextAlign.End,
                fontFamily = FontFamily(Font(R.font.peyda_medium))
            )


            Spacer(
                modifier = Modifier.height(40.dp)
            )

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = { onUsernameChange(it) },
                    singleLine = true,
                    maxLines = 1,
                    label = {
                        Text(
                            text = "نام کاربری :",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.fullColors.textLabelFieldLogin,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.background(Color.Transparent),
                            fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text
                    ),
                    supportingText = {
                        Box(
                            modifier = Modifier.height(20.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            uiState.usernameError?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.fullColors.red2,
                                    textAlign = TextAlign.Right,
                                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_username),
                            contentDescription = "iconFieldUsername",
                            tint = MaterialTheme.fullColors.whit,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.fullColors.whitBlack,
                        unfocusedTextColor = MaterialTheme.fullColors.whitBlack,
                        focusedContainerColor = MaterialTheme.fullColors.bgFieldLogin,
                        unfocusedContainerColor = MaterialTheme.fullColors.bgFieldLogin,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                    )
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { onPasswordChange(it) },
                    singleLine = true,
                    maxLines = 1,
                    label = {
                        Text(
                            text = "رمز عبور :",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.fullColors.textLabelFieldLogin,
                            textAlign = TextAlign.Right,
                            fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
                        )
                    },
                    supportingText = {
                        Box(
                            modifier = Modifier.height(20.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            uiState.passwordError?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.fullColors.red2,
                                    textAlign = TextAlign.Right,
                                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
                                )
                            }
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),

                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.fullColors.whitBlack,
                        unfocusedTextColor = MaterialTheme.fullColors.whitBlack,
                        focusedContainerColor = MaterialTheme.fullColors.bgFieldLogin,
                        unfocusedContainerColor = MaterialTheme.fullColors.bgFieldLogin,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                    )
                )
            }


            Spacer(modifier = Modifier.height(12.dp))


            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onLoginClick,
                modifier = Modifier
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.fullColors.purple
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(
                    modifier = Modifier.height(35.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (uiState.isLoading) {
                        LightningLoading()
                    } else {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "ورود",
                                modifier = Modifier.padding(vertical = 5.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.fullColors.whit,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                            )

                            Spacer(modifier = Modifier.width(5.dp))

                            Icon(
                                painter = painterResource(R.drawable.ic_login),
                                contentDescription = "iconLoginButton",
                                tint = MaterialTheme.fullColors.whit,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            uiState.errorMessage?.let {
                Log.e("error", it)
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            val annotatedText = buildAnnotatedString {

                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.fullColors.textLabelFieldLogin,
                        fontFamily = FontFamily(Font(R.font.peyda_medium))

                    )
                ) {
                    append("اکانتی ندارید ؟ ")
                }
                pushStringAnnotation(
                    tag = "REGISTER",
                    annotation = "register"
                )
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.fullColors.purple,
                        fontFamily = FontFamily(Font(R.font.peyda_medium))
                    )
                ) {
                    append("پشتیبانی")
                }
                pop()
            }


            ClickableText(
                text = annotatedText,
                style = MaterialTheme.typography.labelLarge,
                onClick = { offset ->
                    annotatedText.getStringAnnotations(
                        tag = "REGISTER",
                        start = offset,
                        end = offset
                    ).firstOrNull()?.let {
                        if (!telegramSupportId.isNullOrBlank()) {
                            LinkOpener.openTelegram(context, telegramSupportId)
                        }
                    }
                }
            )

        }

        Image(
            painter = painterResource(R.drawable.img_map_login),
            contentDescription = "imgMapLogin",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun SelectionLogo() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(

        ) {
            Image(
                painter = painterResource(R.drawable.img_logo),
                contentDescription = "LogoSplash",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(53.dp)
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
            },
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily(Font(R.font.poppins_bold))
        )
    }
}

fun getUniqueKey(context: Context): String {
    val uniqueKey =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    val strManufacturer = Build.MANUFACTURER.toString()
    val strModel = Build.MODEL.toString()
    return "${uniqueKey}_${strManufacturer}-${strModel}"
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    FullKotlinTheme(true) {
        LoginContent(
            uiState = LoginUiState(),
            onUsernameChange = {},
            onPasswordChange = {},
            onLoginClick = {},
            telegramSupportId = "",
            context = TODO(),
        )
    }
}
