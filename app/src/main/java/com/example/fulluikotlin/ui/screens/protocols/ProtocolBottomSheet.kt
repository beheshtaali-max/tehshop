package com.example.fulluikotlin.ui.screens.protocols

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors
import pw.fullvpn.android.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolBottomSheet(
    protocols: List<String>,
    currentProtocol: String,
    onProtocolSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "انتخاب پروتکل",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.fullColors.whitBlack,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
            )

            LazyColumn {
                items(
                    items = protocols,
                    key = { it }
                ) { protocol ->
                    ProtocolItem(
                        protocolName = protocol,
                        isSelected = protocol == currentProtocol,
                        onClick = {
                            onProtocolSelected(protocol)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtocolItem(
    protocolName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(vertical = 5.dp, horizontal = 20.dp)
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.fullColors.borderServesList),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgServesList)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = protocolName,
                color = if (isSelected) MaterialTheme.fullColors.purple else MaterialTheme.fullColors.whitBlack,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily(Font(R.font.opensans_semibold))
            )

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonColors(
                    selectedColor = MaterialTheme.fullColors.purple,
                    disabledSelectedColor = MaterialTheme.fullColors.purple,
                    unselectedColor = MaterialTheme.fullColors.unCheckRadioServer,
                    disabledUnselectedColor = MaterialTheme.fullColors.unCheckRadioServer
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ProtocolBottomSheetPreview() {
    FullKotlinTheme(true) {
        var showSheet by remember { mutableStateOf(true) }
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF101010))
        ) {
            if (showSheet) {
                ProtocolBottomSheet(
                    protocols = listOf("V2RAY", "TROJAN", "VLESS"),
                    currentProtocol = "V2RAY",
                    onProtocolSelected = { showSheet = false },
                    onDismiss = { showSheet = false }
                )
            }
        }
    }
}