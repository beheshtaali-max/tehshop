package com.example.fulluikotlin.ui.screens.servers

import android.util.Log
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.blongho.country_data.World
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.ui.screens.protocols.ProtocolBottomSheet
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import pw.fullvpn.android.R
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors

@Composable
fun ServersScreen(
    navController: NavController,
    onItemClick: (Int) -> Unit,
    viewModel: ServersViewModel = koinViewModel()
) {
    val organized by viewModel.organizedServers.collectAsStateWithLifecycle()
    val activeServer by viewModel.activeServer.collectAsStateWithLifecycle()
    val selectedProtocol by viewModel.selectedProtocol.collectAsStateWithLifecycle() // نوع: ProtocolType?
    val scope = rememberCoroutineScope()
    var showProtocolSheet by remember { mutableStateOf(false) }

    val availableProtocols = remember(organized) {
        organized?.data?.keys?.mapNotNull { protocolName ->
            try {
                ProtocolType.valueOf(protocolName.uppercase())
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }

    LaunchedEffect(availableProtocols, selectedProtocol) {
        if (selectedProtocol == null && availableProtocols.isNotEmpty()) {
            viewModel.saveSelectedProtocol(availableProtocols.first())
        }
    }

    val nationalServers = remember(organized, selectedProtocol) {
        if (organized == null || selectedProtocol == null) emptyList()
        else {
            val protocolName = selectedProtocol!!.name
            val groups = organized!!.getGroups(protocolName)
            groups.map { groupName ->
                val serversInGroup = organized!!.getServers(protocolName, groupName)
                NationaltyServer(
                    name = groupName,
                    numServer = serversInGroup.size,
                    bestPing = if (selectedProtocol == ProtocolType.V2RAY) {
                        serversInGroup.minOfOrNull {
                            it.serversignal.toIntOrNull() ?: Int.MAX_VALUE
                        }?.let { "$it ms" } ?: "N/A"
                    } else {
                        ""
                    },
                    flag = getFlagResIdForGroup(groupName),
                    servers = serversInGroup.map { server ->
                        ServerItem(
                            id = server.serverid,
                            name = server.servername,
                            defaultPing = when (server.protocol) {
                                ProtocolType.V2RAY -> "${server.serversignal} ms"
                                ProtocolType.SSH -> "..."
                                else -> ""
                            },
                            ip = server.ip,
                            config = server.config,
                            protocol = server.protocol
                        )
                    }
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable { showProtocolSheet = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgServesList),
            border = BorderStroke(1.dp, MaterialTheme.fullColors.borderServesList)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // آیکون سمت چپ
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_circle_down),
                    contentDescription = "انتخاب پروتکل",
                    tint = MaterialTheme.fullColors.iconOpenServer
                )

                // فاصله‌گذار کشسان سمت چپ متن
                Spacer(modifier = Modifier.weight(1f))

                // متن وسط
                Text(
                    text = "پروتکل: ${selectedProtocol?.name ?: "..."}",
                    color = MaterialTheme.fullColors.whitBlack,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_medium))
                )

                // فاصله‌گذار کشسان سمت راست متن (برای حفظ تقارن)
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        if (showProtocolSheet) {
            ProtocolBottomSheet(
                protocols = availableProtocols.map { it.name },
                currentProtocol = selectedProtocol?.name ?: availableProtocols.firstOrNull()?.name ?: "",
                onProtocolSelected = { protocolName ->
                    val protocol = ProtocolType.valueOf(protocolName.uppercase())
                    scope.launch {
                        viewModel.saveSelectedProtocol(protocol)
                        showProtocolSheet = false
                    }
                },
                onDismiss = { showProtocolSheet = false }
            )
        }


        nationalServers.forEach { nationalServer ->
            val containsActive = activeServer?.let { active ->
                nationalServer.servers.any { it.id == active.serverid }
            } ?: false

            ItemsNationalServer(
                nationaltyServer = nationalServer,
                selectedServer = activeServer?.let { server ->
                    ServerItem(
                        id = server.serverid,
                        name = server.servername,
                        defaultPing = when (server.protocol) {
                            ProtocolType.V2RAY -> "${server.serversignal} ms"
                            ProtocolType.SSH -> "..."
                            else -> ""
                        },
                        ip = server.ip,
                        config = server.config,
                        protocol = server.protocol
                    )
                },
                onServerSelected = { selectedItem ->
                    val originalServer = organized?.getServers(selectedItem.protocol.name, nationalServer.name)
                        ?.find { it.serverid == selectedItem.id }
                    if (originalServer != null) {
                        scope.launch {
                            viewModel.activateAndSelectServer(originalServer)
                            navController.popBackStack()
                        }
                    }
                },
                initiallyExpanded = containsActive,
                viewModel = viewModel
            )
        }
    }
}

fun getFlagResIdForGroup(groupName: String): Int {
    return World.getFlagOf(groupName.lowercase())
}

@Composable
fun ItemsNationalServer(
    nationaltyServer: NationaltyServer,
    selectedServer: ServerItem?,
    onServerSelected: (ServerItem) -> Unit,
    initiallyExpanded: Boolean = false,
    viewModel: ServersViewModel
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier
            .padding(vertical = 5.dp)
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.fullColors.borderServesList),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgServesList)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Card(
                modifier = Modifier.size(62.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgFlagServesList)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        shape = CircleShape
                    ) {
                        Image(
                            painter = painterResource(nationaltyServer.flag),
                            contentDescription = "FlagServer",
                            modifier = Modifier
                                .size(34.dp)
                                .border(1.5.dp, MaterialTheme.fullColors.whit, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.5.dp)
            ) {
                Text(
                    text = nationaltyServer.name,
                    color = MaterialTheme.fullColors.whitBlack,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily(Font(R.font.opensans_bold))
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_location),
                        contentDescription = "Location",
                        modifier = Modifier.size(20.dp),
                        tint = Color.Unspecified
                    )
                    Text(
                        text = "${nationaltyServer.numServer} Location",
                        modifier = Modifier.padding(start = 2.dp),
                        color = MaterialTheme.fullColors.pingServers,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily(Font(R.font.gilroy_regular))
                    )
                }
            }

            Icon(
                painter = painterResource(if (isExpanded) R.drawable.ic_arrow_circle_up else R.drawable.ic_arrow_circle_down),
                contentDescription = "Expand",
                tint = MaterialTheme.fullColors.iconOpenServer
            )
        }

        if (isExpanded) {
            SubServer(
                nationaltyServer = nationaltyServer,
                selectedServer = selectedServer,
                onServerSelected = onServerSelected,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun SubServer(
    nationaltyServer: NationaltyServer,
    selectedServer: ServerItem?,
    onServerSelected: (ServerItem) -> Unit,
    viewModel: ServersViewModel
) {
    Divider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        color = MaterialTheme.fullColors.dividerServers
    )
    Column {
        nationaltyServer.servers.forEach { server ->
            ItemsServer(
                server = server,
                isSelected = server.id == selectedServer?.id,
                onSelect = { onServerSelected(server) },
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun ItemsServer(
    server: ServerItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    viewModel: ServersViewModel
) {
    val shouldShowPing = server.protocol == ProtocolType.V2RAY || server.protocol == ProtocolType.SSH
    var currentPing by remember(server.id, server.protocol) {
        mutableStateOf(if (shouldShowPing) server.defaultPing else "")
    }

    LaunchedEffect(server.id, server.protocol, server.config, server.ip) {
        if (!shouldShowPing) {
            currentPing = ""
            return@LaunchedEffect
        }

        currentPing = if (server.protocol == ProtocolType.SSH) "..." else server.defaultPing

        val realPing = viewModel.getRealPing(
            serverId = server.id,
            config = server.config,
            protocol = server.protocol,
            fallbackIp = server.ip
        )
        currentPing = if (realPing > 0) "${realPing} ms" else "N/A"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 20.dp)
            .clickable { onSelect() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = server.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.fullColors.whitBlack,
            fontFamily = FontFamily(Font(R.font.yekanbakh_medium))
        )

        if (shouldShowPing) {
            Row(
                modifier = Modifier.weight(0.5f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_signal_good),
                    contentDescription = "Ping",
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )
                Text(
                    text = currentPing,
                    modifier = Modifier.padding(start = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fullColors.pingServers,
                    fontFamily = FontFamily(Font(R.font.opensans_bold))
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(0.5f))
        }

        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonColors(
                selectedColor = MaterialTheme.fullColors.purple,
                disabledSelectedColor = MaterialTheme.fullColors.purple,
                unselectedColor = MaterialTheme.fullColors.unCheckRadioServer,
                disabledUnselectedColor = MaterialTheme.fullColors.unCheckRadioServer
            )
        )
    }
}

data class NationaltyServer(
    val name: String,
    val numServer: Int,
    val bestPing: String,
    val flag: Int,
    val servers: List<ServerItem>
)

data class ServerItem(
    val id: String,
    val name: String,
    val defaultPing: String,
    val ip: String,
    val config: String,
    val protocol: ProtocolType
)

@Preview(showBackground = true)
@Composable
fun ServerPreview() {
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
            val servers = listOf(
                NationaltyServer(
                    name = "FRANCE",
                    numServer = 3,
                    bestPing = "12 ms",
                    flag = R.drawable.img_flag_fr,  // مطمئن شوید این drawable وجود دارد
                    servers = listOf(
                        ServerItem("12", "Paris", "12 ms", "185.165.116.2", "", ProtocolType.OPENVPN),
                        ServerItem("13", "Monoco", "18 ms", "185.165.116.5", "",ProtocolType.OPENVPN)
                    )
                ),
                NationaltyServer(
                    name = "USA",
                    numServer = 2,
                    bestPing = "35 ms",
                    flag = R.drawable.img_flag_us,
                    servers = listOf(
                        ServerItem("1", "New York", "25 ms", "89.163.211.1", "", ProtocolType.OPENVPN),
                        ServerItem("2", "California", "85 ms", "89.163.211.1", "", ProtocolType.OPENVPN),
                        ServerItem("3", "Oklahoma", "85 ms", "89.163.211.1", "", ProtocolType.OPENVPN),
                        ServerItem("4", "Arizona", "85 ms", "89.163.211.1", "", ProtocolType.OPENVPN),
                        ServerItem("5", "Idaho", "85 ms", "89.163.211.1", "", ProtocolType.OPENVPN),
                        ServerItem("6", "Montana", "85 ms", "89.163.211.1", "", ProtocolType.OPENVPN),
                        ServerItem("7", "Oregon", "85 ms", "89.163.211.1", "", ProtocolType.OPENVPN),
                        ServerItem("8", "Texas", "35 ms", "89.163.211.1", "", ProtocolType.OPENVPN),
                        ServerItem("9", "New Mexico", "40 ms", "89.163.211.4", "", ProtocolType.OPENVPN)
                    )
                ),
                NationaltyServer(
                    name = "NETHERLANDS",
                    numServer = 3,
                    bestPing = "12 ms",
                    flag = R.drawable.img_flag_fr,
                    servers = listOf(
                        ServerItem("10", "Amsterdam", "12 ms", "185.165.116.2", "", ProtocolType.OPENVPN),
                        ServerItem("11", "Rotterdam", "18 ms", "185.165.116.5", "", ProtocolType.OPENVPN)
                    )
                )
            )

            var selectedServer by remember { mutableStateOf<ServerItem?>(null) }

            ServersScreen(
                navController = rememberNavController(),
                onItemClick = {},
            )
        }
    }
}