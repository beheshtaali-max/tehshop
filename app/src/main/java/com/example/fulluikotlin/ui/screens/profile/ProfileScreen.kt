package com.example.fulluikotlin.ui.screens.profile

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.fulluikotlin.domain.model.User
import com.example.fulluikotlin.domain.utils.Resource
import com.example.fulluikotlin.ui.screens.activeservice.formatGregorianToJalali
import com.example.fulluikotlin.ui.screens.devicesactive.Devices
import com.example.fulluikotlin.ui.screens.devicesactive.DevicesActiveBottomSheet
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors
import org.koin.androidx.compose.koinViewModel
import pw.fullvpn.android.BuildConfig
import pw.fullvpn.android.R

@Composable
fun ProfileScreen(
    navController: NavHostController,
    onItemClick: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
    onLogoutNavigate: () -> Unit
) {
    val context = LocalContext.current
    val profileUiState by viewModel.profileUiState.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val logoutState by viewModel.logoutState.collectAsState()
    var showKillSwitchDialog by remember { mutableStateOf<Boolean?>(null) }
    val currentDevice by remember { derivedStateOf { profileUiState.user?.device ?: "" } }
    var showExitAccountDialog by remember { mutableStateOf(false) }
    var showDevicesSheet by remember { mutableStateOf(false) }

    ProfileContent(
        profileUiState = profileUiState,
        devices = devices,
        onChangeDarkMode = viewModel::onIsDarkModeChange,
        onAboutUs = { navController.navigate("aboutUs") },
        onShowDevicesSheet = { showDevicesSheet = true },
        onChangeKillSwitch = { enabled ->
            viewModel.onKillSwitchChange(enabled)
            showKillSwitchDialog = enabled
        },
        onShowExitDialog = { showExitAccountDialog = true }
    )

    showKillSwitchDialog?.let { enabled ->
        KillSwitchSettingsDialog(
            enabled = enabled,
            onDismiss = { showKillSwitchDialog = null },
            onOpenSettings = {
                showKillSwitchDialog = null
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Intent(Settings.ACTION_VPN_SETTINGS)
                } else {
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                }
                context.startActivity(intent)
            }
        )
    }


    if (showDevicesSheet) {
        DevicesActiveBottomSheet(
            devices = devices,
            onDismiss = { showDevicesSheet = false }
        )
    }

    if (showExitAccountDialog) {
        ExitAccountDialog(
            device = Devices("0", currentDevice, ""),
            accountName = profileUiState.user?.username,
            isLoading = logoutState is Resource.Loading,
            errorMessage = (logoutState as? Resource.Error)?.message,
            onDismiss = { showExitAccountDialog = false },
            onConfirm = {
                viewModel.logout(currentDevice)
            }
        )
    }

    LaunchedEffect(logoutState) {
        when (logoutState) {
            is Resource.Success -> {
                showExitAccountDialog = false
                onLogoutNavigate()
                viewModel.resetLogoutState()
            }

            is Resource.Error -> {
                Toast.makeText(context, (logoutState as Resource.Error).message, Toast.LENGTH_SHORT).show()
            }

            else -> Unit
        }
    }
}

@Composable
fun ProfileContent(
    profileUiState: ProfileUiState,
    devices: List<String>,
    onChangeDarkMode: (Boolean) -> Unit,
    onChangeKillSwitch: (Boolean) -> Unit,
    onAboutUs: () -> Unit,
    onShowDevicesSheet: () -> Unit,
    onShowExitDialog: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(74.dp)
                .background(color = MaterialTheme.fullColors.bgProfile, shape = CircleShape)
                .padding(2.5.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.img_prof_defult),
                contentDescription = "imageProfile",
                contentScale = ContentScale.Crop,
                modifier = Modifier
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        ProfilePackageInfoCard(
            user = profileUiState.user,
            devicesCount = devices.size,
            onDevicesClick = onShowDevicesSheet
        )

        Spacer(modifier = Modifier.height(15.dp))

        ItemsBySpinner(
            R.drawable.ic_dark_mood,
            "دارک مود",
            checked = profileUiState.isDarkMode,
            onChangeDarkMode
        )

        Spacer(
            modifier = Modifier.height(15.dp)
        )

        ItemsBySpinner(
            R.drawable.ic_kill_switch,
            "کیل سوییچ",
            checked = profileUiState.isKillSwitchEnabled,
            onChangeKillSwitch
        )


        Spacer(modifier = Modifier.height(15.dp))

        ItemsByArrow(R.drawable.ic_about_us, "درباره ما", onAboutUs)

        Spacer(modifier = Modifier.height(15.dp))

        ItemsByArrow(R.drawable.ic_logout, "خروج از اپلیکیشن", onShowExitDialog)

        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = "version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.fullColors.whitBlack.copy(alpha = 0.67f),
            textAlign = TextAlign.End,
            fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
        )
    }
}

@Composable
fun ProfilePackageInfoCard(
    user: User?,
    devicesCount: Int,
    onDevicesClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.fullColors.borderCardAccountProfile,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgCardAccountProfile)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        ProfileInfoRow(
            label = "نام کاربری :",
            value = user?.username.orDash(),
            icon = R.drawable.ic_username_service
        )

        ProfileDivider()

        ProfileInfoRow(
            label = "تاریخ اتمام اشتراک:",
            value = formatGregorianToJalali(user?.expDate).orDash(),
            icon = R.drawable.ic_calendar
        )

        ProfileDivider()

        ProfileInfoRow(
            label = "روزهای باقی مانده :",
            value = user?.remainDays?.let { remainDays ->
                "${formatRemainDays(remainDays)} روز"
            }.orDash(),
            icon = R.drawable.ic_remings_service
        )
        ProfileDivider()

        ProfileInfoRow(
            label = "حجم باقی مانده :",
            value = "نامحدود",
            icon = R.drawable.ic_download
        )

        ProfileDivider()

        ProfileInfoRow(
            label = "دستگاه های فعال :",
            value = "$devicesCount دستگاه",
            icon = R.drawable.ic_divices,
            showArrow = true,
            onClick = onDevicesClick
        )

        Spacer(modifier = Modifier.height(5.dp))
    }
}

private fun formatRemainDays(value: Float?): String {
    return (value ?: 0f)
        .coerceAtLeast(0f)
        .toInt()
        .toString()
}
@Composable
fun ProfileInfoRow(
    label: String,
    value: String,
    icon: Int,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArrow) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "iconArrowItemsAccount",
                tint = MaterialTheme.fullColors.iconActiveService,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = if (showArrow) MaterialTheme.fullColors.purple2 else MaterialTheme.fullColors.textActiveService,
            textAlign = TextAlign.End,
            fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp, end = 10.dp)
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.fullColors.textActiveService,
            textAlign = TextAlign.Start,
            fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
            modifier = Modifier.padding(end = 10.dp)
        )

        Icon(
            painter = painterResource(icon),
            contentDescription = "iconCardAccount",
            tint = MaterialTheme.fullColors.iconTitleActiveService,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ProfileDivider() {
    Divider(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 20.dp),
        color = MaterialTheme.fullColors.dividerActiveService,
    )
}

@Composable
fun SectionAccount(
    modifier: Modifier,
    icon: Int,
    text: String,
    onItemClick: () -> Unit
) {
    Card(
        onClick = onItemClick,
        modifier = modifier
            .wrapContentHeight()
            .border(
                width = 1.dp,
                color = MaterialTheme.fullColors.borderCardAccountProfile,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgCardAccountProfile)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 15.dp)
                .background(Color.Transparent),
            horizontalAlignment = Alignment.End
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = "iconCardAccount",
                tint = Color.Unspecified,
                modifier = Modifier
                    .padding(bottom = 8.dp)
            )

            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.fullColors.textItemsProfile,
                textAlign = TextAlign.End,
                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
            )

        }
    }
}

@Composable
fun ItemsBySpinner(
    icon: Int,
    text: String,
    checked: Boolean,
    onChangeClick: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.fullColors.borderCardAccountProfile,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgCardAccountProfile)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = checked,
                onCheckedChange = { onChangeClick(!checked) },
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        scaleY = 0.8f
                        scaleX = 0.8f
                    },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.fullColors.thumbCheckProfile,
                    checkedTrackColor = MaterialTheme.fullColors.purple,
                    uncheckedThumbColor = MaterialTheme.fullColors.thumbUnCheckProfile,
                    uncheckedTrackColor = MaterialTheme.fullColors.trackUnCheckProfile,
                    uncheckedBorderColor = Color.Transparent
                )
            )


            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.fullColors.textItemsProfile,
                textAlign = TextAlign.Start,
                fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            Icon(
                painter = painterResource(icon),
                contentDescription = "iconCardAccount",
                tint = MaterialTheme.fullColors.iconTitleActiveService,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ItemsByArrow(
    icon: Int,
    text: String,
    onLogout: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable { onLogout() }
            .border(
                width = 1.dp,
                color = MaterialTheme.fullColors.borderCardAccountProfile,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgCardAccountProfile)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "iconArrowItemsAccount",
                tint = MaterialTheme.fullColors.arrowProfile,
                modifier = Modifier.size(20.dp)
            )


            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.fullColors.textItemsProfile,
                textAlign = TextAlign.Start,
                fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            Icon(
                painter = painterResource(icon),
                contentDescription = "iconCardAccount",
                tint = MaterialTheme.fullColors.iconTitleActiveService,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun String?.orDash(): String = if (this.isNullOrBlank()) "-" else this
@Composable
private fun KillSwitchSettingsDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (enabled) "فعال‌سازی کیل سوییچ" else "غیرفعال‌سازی کیل سوییچ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.fullColors.whitBlack,
                textAlign = TextAlign.Right,
                fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = if (enabled) {
                    "برای فعال‌سازی کامل، در صفحه تنظیمات VPN روی چرخ‌دنده کنار این برنامه بزن و گزینه Always-on VPN و Block connections without VPN را فعال کن."
                } else {
                    "برای غیرفعال‌سازی کامل، در صفحه تنظیمات VPN روی چرخ‌دنده کنار این برنامه بزن و گزینه Always-on VPN و Block connections without VPN را غیرفعال کن."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.fullColors.whitBlack,
                textAlign = TextAlign.Right,
                fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = "تنظیمات",
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "بازگشت",
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ProfilePreview() {
    FullKotlinTheme(false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.fullColors.background)

        ) {
            ProfileContent(
                profileUiState = ProfileUiState(),
                devices = emptyList(),
                onChangeDarkMode = {},
                onAboutUs = {},
                onShowDevicesSheet = {},
                onChangeKillSwitch = {},
                onShowExitDialog = {}
            )
        }
    }
}
