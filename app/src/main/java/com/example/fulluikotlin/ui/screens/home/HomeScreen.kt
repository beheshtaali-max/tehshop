package com.example.fulluikotlin.ui.screens.home


import android.os.Build
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.blongho.country_data.World
import org.koin.androidx.compose.koinViewModel
import pw.fullvpn.android.R
import com.example.fulluikotlin.domain.model.ConnectionState
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen(
    navController: NavController,
    onItemClick: (Int) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val connectionState by viewModel.connectionState.collectAsState()
    val elapsedTimeFormatted by remember { derivedStateOf { viewModel.formatElapsedTime() } }
    val selectedServer = viewModel.serverSelected
    val publicIp by viewModel.publicIp.collectAsState()
    val isIpLoading by viewModel.isIpLoading.collectAsState()
    val totalDownloadBytes by viewModel.totalDownloadBytes.collectAsState()
    val totalUploadBytes by viewModel.totalUploadBytes.collectAsState()

    DisposableEffect(Unit) {
        viewModel.registerStatsReceiver(context)
        onDispose {
            viewModel.unregisterStatsReceiver(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {


            Spacer(
                modifier = Modifier.height(15.dp)
            )

            ServerSelectedUi(
                server = selectedServer,
                ip = publicIp,
                isIpLoading = isIpLoading,
                isConnected = connectionState is ConnectionState.Connected,
                onClick = { navController.navigate("servers") }
            )

            Spacer(
                modifier = Modifier.height(30.dp)
            )

            ConsumptionAmount(
                download = viewModel.formatBytes(totalDownloadBytes),
                upload = viewModel.formatBytes(totalUploadBytes)
            )

            Spacer(
                modifier = Modifier.height(35.dp)
            )

            ButtonConnect(
                state = connectionState,
                onClick = {
                    if (connectionState is ConnectionState.Connected ||
                        connectionState is ConnectionState.Connecting
                    ) {
                        viewModel.disconnect(context)
                    } else {
                        activity?.let {
                            viewModel.connect(
                                activity = it,
                                server = selectedServer,
                            )
                        } ?: run {
                            Toast.makeText(context, "Activity not available", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(35.dp))

            TimeAndStatus(
                state = connectionState,
                elapsedTime = elapsedTimeFormatted,
            )
        }
    }
}

@Composable
fun ServerSelectedUi(
    server: Server?,
    ip: String?,
    isIpLoading: Boolean,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(65.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(92.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgServerSelected)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_circle_left),
                contentDescription = "IconGoChangeServers",
                modifier = Modifier,
                tint = MaterialTheme.fullColors.iconColor
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.5.dp)
            ) {
                Text(
                    text = server?.servername ?: "هیچ سروری انتخاب نشده",
                    modifier = Modifier,
                    color = MaterialTheme.fullColors.whitBlack,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily(Font(R.font.opensans_bold))
                )

                Row(
                    modifier = Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isConnected) {
                        when {
                            isIpLoading -> {
                                Text(
                                    text = "...",
                                    color = MaterialTheme.fullColors.pingText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily(Font(R.font.gilroy_medium))
                                )
                            }

                            !ip.isNullOrBlank() -> {
                                Text(
                                    text = ip,
                                    color = MaterialTheme.fullColors.pingText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily(Font(R.font.gilroy_medium))
                                )
                            }

                            else -> {
                                Spacer(modifier = Modifier.width(1.dp))
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                }

            }

            val flagResId = remember(server?.serverimagename) {
                val countryName = server?.serverimagename ?: "unknown"
                World.getFlagOf(countryName)
            }

            Surface(
                color = MaterialTheme.fullColors.transparent,
                shape = CircleShape
            ) {
                Image(
                    painter = painterResource(id = flagResId),
                    contentDescription = "FlagServer",
                    modifier = Modifier
                        .size(32.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun ConsumptionAmount(download: String, upload: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = download,
                modifier = Modifier,
                color = MaterialTheme.fullColors.textValueAmount,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
            )

            Row(
                modifier = Modifier
                    .wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "دانلود",
                    modifier = Modifier,
                    color = MaterialTheme.fullColors.textAmount,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
                )

                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.fullColors.red
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = "IconDownload",
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                    }
                }


            }

        }

        Column(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = upload,
                modifier = Modifier,
                color = MaterialTheme.fullColors.whitBlack,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
            )

            Row(
                modifier = Modifier.wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "آپلود",
                    modifier = Modifier,
                    color = MaterialTheme.fullColors.textAmount,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
                )

                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.fullColors.green
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_upload),
                        contentDescription = "IconUpload",
                        modifier = Modifier
                            .size(20.dp)
                            .padding(2.dp),
                        tint = Color.White
                    )
                }

            }

        }
    }
}

@Composable
fun ButtonConnect(
    state: ConnectionState,
    onClick: () -> Unit
) {
    val outerCardColor = when (state) {
        ConnectionState.Disconnected -> MaterialTheme.fullColors.transparent
        is ConnectionState.Error -> MaterialTheme.fullColors.transparent
        ConnectionState.Connecting -> MaterialTheme.fullColors.bgButtonL1Connecting
        ConnectionState.Connected -> MaterialTheme.fullColors.bgButtonL1Connected
    }

    val middleBorderColor = when (state) {
        ConnectionState.Disconnected -> MaterialTheme.fullColors.bgButtonL1DisConnect
        is ConnectionState.Error -> MaterialTheme.fullColors.bgButtonL1DisConnect
        ConnectionState.Connecting -> MaterialTheme.fullColors.transparent
        ConnectionState.Connected -> MaterialTheme.fullColors.bgButtonL1Connected
    }

    val middleCardColor = when (state) {
        ConnectionState.Disconnected -> MaterialTheme.fullColors.bgButtonL2DisConnect
        is ConnectionState.Error -> MaterialTheme.fullColors.bgButtonL2DisConnect
        ConnectionState.Connecting -> MaterialTheme.fullColors.bgButtonL2Connecting
        ConnectionState.Connected -> MaterialTheme.fullColors.bgButtonL2Connected
    }

    val innerCardColor = when (state) {
        ConnectionState.Disconnected -> MaterialTheme.fullColors.bgButtonL3DisConnect
        is ConnectionState.Error -> MaterialTheme.fullColors.bgButtonL3DisConnect
        ConnectionState.Connecting -> MaterialTheme.fullColors.bgButtonL3Connecting
        ConnectionState.Connected -> MaterialTheme.fullColors.bgButtonL2Connected  // همانطور که در کد شما بود
    }

    // The button must stay clickable while Connecting so the user can cancel a stuck connection.
    val enabled = true

    Card(
        modifier = Modifier
            .size(184.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick() }
            ),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = outerCardColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .size(148.dp)
                    .border(
                        width = 6.dp,
                        color = middleBorderColor,
                        shape = CircleShape
                    )
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onClick() }
                    ),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = middleCardColor)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.size(120.dp),
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = innerCardColor)
                    ) {

                        Icon(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = "IconLogoButton",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 20.dp, horizontal = 40.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeAndStatus(state: ConnectionState, elapsedTime: String) {

    val statusText = when (state) {
        ConnectionState.Connected -> "شما متصل هستید"
        ConnectionState.Connecting -> "در حال اتصال"
        ConnectionState.Disconnected -> "شما متصل نیستید"
        is ConnectionState.Error -> "خطا در اتصال"
    }

    val timeText = when (state) {
        ConnectionState.Connected -> elapsedTime
        else -> "00:00:00"
    }

    val statusIcon = when (state) {
        ConnectionState.Connected -> R.drawable.ic_connected
        ConnectionState.Connecting -> R.drawable.ic_connecting
        ConnectionState.Disconnected -> R.drawable.ic_disconnect
        is ConnectionState.Error -> R.drawable.ic_disconnect
    }

    val bgCardColor = when (state) {
        ConnectionState.Disconnected -> MaterialTheme.fullColors.bgStatusDisconnect
        is ConnectionState.Error -> MaterialTheme.fullColors.bgStatusDisconnect
        ConnectionState.Connecting -> MaterialTheme.fullColors.bgStatusConnecting
        ConnectionState.Connected -> MaterialTheme.fullColors.bgStatusConnected  // همانطور که در کد شما بود
    }

    val statusTextColor = when (state) {
        ConnectionState.Disconnected -> MaterialTheme.fullColors.textStatusDisconnect
        is ConnectionState.Error -> MaterialTheme.fullColors.textStatusDisconnect
        ConnectionState.Connecting -> MaterialTheme.fullColors.textStatusConnecting
        ConnectionState.Connected -> MaterialTheme.fullColors.textStatusConnected  // همانطور که در کد شما بود
    }

    Text(
        text = timeText,
        modifier = Modifier,
        color = MaterialTheme.fullColors.timeText,
        style = MaterialTheme.typography.headlineLarge,
        fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
    )

    Spacer(
        modifier = Modifier.height(5.dp)
    )

    Card(
        modifier = Modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = bgCardColor)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 15.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusText,
                modifier = Modifier,
                color = statusTextColor,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily(Font(R.font.peyda_medium))
            )

            Spacer(
                modifier = Modifier.width(5.dp)
            )

            Icon(
                painter = painterResource(statusIcon),
                contentDescription = "IconLogoButton",
                modifier = Modifier.size(22.dp),
                tint = statusTextColor
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun HomePreview() {
    FullKotlinTheme(true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.fullColors.background)
        ) {
            // تصویر بک‌گراند برای Preview
            Image(
                painter = painterResource(R.drawable.img_map_dark),
                contentDescription = "BgImage",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // overlay سایه / gradient اگر می‌خوای
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

            // HomeScreen واقعی
            HomeScreen(
                navController = rememberNavController(),
                onItemClick = {}
            )
        }
    }
}
