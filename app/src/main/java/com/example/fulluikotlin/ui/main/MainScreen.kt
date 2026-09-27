package com.example.fulluikotlin.ui.main

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.ui.commponent.dialog.AdvertiseDialog
import com.example.fulluikotlin.ui.commponent.dialog.CustomInfoDialog
import com.example.fulluikotlin.ui.commponent.dialog.ExpiryDialog
import com.example.fulluikotlin.ui.commponent.dialog.UpdateDialog
import com.example.fulluikotlin.ui.commponent.dialog.UpdateReadyDialog
import com.example.fulluikotlin.ui.commponent.dialog.UpdateFailedDialog
import com.example.fulluikotlin.ui.commponent.dialog.UpdateDownloadingDialog
import org.koin.compose.koinInject
import pw.fullvpn.android.R
import com.example.fulluikotlin.domain.model.DialogState
import com.example.fulluikotlin.ui.navigation.MainNavHost
import com.example.fulluikotlin.ui.screens.questions.QuestionsBottomSheet
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.LocalDarkTheme
import com.example.fulluikotlin.ui.theme.fullColors

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainScreen(
    onLogoutNavigate: () -> Unit,
    mainViewModel: MainViewModel = koinInject(),
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val isDarkTheme = LocalDarkTheme.current
    val currentDialog by mainViewModel.currentDialog.collectAsState()
    val userDataStore: UserDataStore
    LaunchedEffect(Unit) {
        mainViewModel.loadExpiryLinks()
    }
    Log.d("MainScreen", "currentDialog = $currentDialog")

    var showSheet by remember { mutableStateOf(false) }

    val painterRes = if (isDarkTheme) {
        R.drawable.img_map_dark
    } else {
        R.drawable.img_map_light
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(enabled = true) {
        (context as? Activity)?.finishAndRemoveTask()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainViewModel.onAppForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    when (val dialog = currentDialog) {
        is DialogState.Update -> UpdateDialog(
            model = dialog.data,
            onUpdateClick = { url -> mainViewModel.onUpdateClicked(url) },
            onDismiss = { mainViewModel.dismissCurrentDialog() }
        )

        is DialogState.UpdateDownloading -> UpdateDownloadingDialog(
            model = dialog.data,
            progressPercent = dialog.progressPercent,
            downloadedBytes = dialog.downloadedBytes,
            totalBytes = dialog.totalBytes,
            onLaterClick = { mainViewModel.hideUpdateDownloadDialog() }
        )

        is DialogState.UpdateReady -> UpdateReadyDialog(
            model = dialog.data,
            onInstallClick = { mainViewModel.installDownloadedUpdate() },
            onLaterClick = { mainViewModel.dismissCurrentDialog() }
        )

        is DialogState.UpdateFailed -> UpdateFailedDialog(
            model = dialog.data,
            message = dialog.message,
            onRetryClick = { mainViewModel.retryUpdateDownload() },
            onLaterClick = { mainViewModel.dismissCurrentDialog() }
        )

        is DialogState.CustomInfo -> CustomInfoDialog(
            model = dialog.data,
            onButtonClick = { link -> mainViewModel.onCustomInfoClicked(link) },
            onDismiss = { mainViewModel.dismissCurrentDialog() }
        )

        is DialogState.PopupAds -> AdvertiseDialog(
            popupAd = dialog.data,
            onDismiss = { mainViewModel.dismissCurrentDialog() },
            onOpenLink = { url -> mainViewModel.onPopupAdsClicked(url) }
        )
        is DialogState.Expiry -> ExpiryDialog(
            type = dialog.type,
            username = dialog.username,
            onPrimaryClick = { mainViewModel.onExpiryPrimaryClick() },
            onSecondaryClick = { mainViewModel.onExpirySecondary(onLogoutNavigate) },
            onDismiss = { mainViewModel.dismissCurrentDialog() }
        )

        DialogState.None -> Unit
    }

    if (showSheet) {
        QuestionsBottomSheet(
            onDismiss = { showSheet = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.fullColors.background)
    ) {

        Image(
            painter = painterResource(painterRes),
            contentDescription = "BgImage",
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
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

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                MainTopBar(
                    onNotificationClick = {
                        navController.navigate("notifications")
                    },
                    onQuestionsClick = {
                        showSheet = true
                    }
                )
            },
            bottomBar = {
                BottomBar(navController)
            }
        ) { padding ->
            MainNavHost(
                navController = navController,
                modifier = Modifier.padding(padding),
                onLogoutNavigate = onLogoutNavigate
            )

        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    FullKotlinTheme(true) {
        MainScreen(
            onLogoutNavigate = { }
        )
    }
}



