package com.example.fulluikotlin.ui.screens.devicesactive

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import pw.fullvpn.android.R
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesActiveBottomSheet(
    devices: List<String>,
    onDismiss: () -> Unit
) {

    val scope = rememberCoroutineScope()
    val userDataStore: UserDataStore = koinInject()
    val viewModel: DevicesViewModel = koinViewModel()

    var currentDevices by remember(devices) { mutableStateOf(devices) }
    var pendingDevice by remember { mutableStateOf<String?>(null) }
    val deleteState by viewModel.deleteState.collectAsState()



    LaunchedEffect(deleteState) {
        if (deleteState is DeleteState.Success) {
            val deletedDevice = (deleteState as DeleteState.Success).deviceId
            // ✅ بازنشانی لیست با یک لیست جدید
            currentDevices = currentDevices.filterNot { it == deletedDevice }
            pendingDevice = null
            val afterDeleteList = userDataStore.getMultiLoginDevices()
            android.util.Log.d(
                "DevicesActiveBottomSheet",
                "After deletion, devices in DataStore: $afterDeleteList"
            )
            viewModel.resetDeleteState()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)


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
                            text = "دستگاه های فعال شما :",
                            modifier = Modifier,
                            color = MaterialTheme.fullColors.textTitleDevicesActive,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Start,
                            fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                        )

                        IconButton(
                            onClick = { }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_divices),
                                contentDescription = "closeHintSplit",
                                tint = MaterialTheme.fullColors.textTitleDevicesActive,
                                modifier = Modifier
                            )
                        }

                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(currentDevices) { deviceName ->
                        ItemsDevices(
                            deviceName = deviceName,
                            onDeleteClick = { pendingDevice = deviceName }
                        )
                    }
                }

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
    // دیالوگ تأیید حذف
    pendingDevice?.let { deviceName ->
        val isLoading =
            deleteState is DeleteState.Loading && (deleteState as DeleteState.Loading).deviceId == deviceName
        val error =
            (deleteState as? DeleteState.Error)?.takeIf { it.deviceId == deviceName }?.message

        ExitDeviceDialog(
            device = Devices(id = deviceName, deviceName = deviceName, lastActive = ""),
            isLoading = isLoading,
            errorMessage = error,
            onDismiss = { pendingDevice = null },
            onConfirm = {
                // نام کاربری را از DataStore بگیریم
                scope.launch {
                    val (username, _, _) = userDataStore.getMultiLoginCredentials()
                    if (username != null) {
                        // لاگ قبل از حذف
                        val beforeList = userDataStore.getMultiLoginDevices()
                        android.util.Log.d(
                            "DevicesActiveBottomSheet",
                            "Before deletion, devices in DataStore: $beforeList"
                        )
                        android.util.Log.d(
                            "DevicesActiveBottomSheet",
                            "Requesting delete for device: $deviceName"
                        )

                        viewModel.deleteDevice(username, deviceName)
                    } else {
                        pendingDevice = null
                    }
                }
            }
        )
    }
}

@Composable
fun ItemsDevices(
    deviceName: String,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = "خروج",
            modifier = Modifier
                .padding(vertical = 5.dp)
                .clickable { onDeleteClick() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.fullColors.red3,
            textAlign = TextAlign.End,
            fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
        )

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = deviceName.substringAfter('_', deviceName).replace("-", " "),
                modifier = Modifier.padding(start = 5.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.fullColors.textTitleDevicesActive,
                textAlign = TextAlign.End,
                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
            )
            Text(
                text = "آخرین فعالیت: ",
                modifier = Modifier,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.fullColors.textLastActiveDevicesActive,
                textAlign = TextAlign.End,
                fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
            )
        }
    }
}


data class Devices(
    val id: String,
    val deviceName: String,
    val lastActive: String
)


@OptIn(ExperimentalMaterial3Api::class)
@Preview(
    showBackground = true,
    showSystemUi = true
)
@Composable
fun DevicesActiveBottomSheetPreview() {

    FullKotlinTheme(true) {   // 👈 تم اصلی پروژه‌ات را بگذار

        var showSheet by remember { mutableStateOf(true) }
        /*val devices = listOf<Devices>(
            Devices("1", "Redmi note 10 pro", "1404/09/10 ، 18:45"),
            Devices("2", "Samsung Galaxy A33", "1404/10/20 ، 17:42"),
            Devices("3", "Samsung Galaxy A07", "1404/12/01 ، 22:30"),
        )*/
        val devices = listOf<String>(
            "Redmi note 10 pro",
            "Samsung Galaxy A33",
            "Samsung Galaxy A07"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010))
        ) {

            if (showSheet) {
                DevicesActiveBottomSheet(
                    devices = devices,
                    onDismiss = { showSheet = false }
                )
            }
        }
    }
}
