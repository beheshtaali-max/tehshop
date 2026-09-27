package com.example.fulluikotlin.ui.screens.servers

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fulluikotlin.R
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.ui.components.ProtocolBottomSheet
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors
import com.example.fulluikotlin.ui.utils.World
import kotlinx.coroutines.launch

@Composable
fun ServersScreen(
    navController: NavController,
    viewModel: ServersViewModel
) {
    val organizedServers by
        viewModel.organizedServers.collectAsState()

    val activeServer by
        viewModel.activeServer.collectAsState()

    val selectedProtocol by
        viewModel.selectedProtocol.collectAsState()

    val availableProtocols =
        organizedServers
            ?.flatMap { it.servers }
            ?.map { it.protocol }
            ?.distinct()
            ?: emptyList()

    /*
     * انتخاب خودکار اولین پروتکل موجود
     * این بخش عمداً حفظ شده است.
     */
    LaunchedEffect(
        availableProtocols,
        selectedProtocol
    ) {
        if (
            selectedProtocol == null &&
            availableProtocols.isNotEmpty()
        ) {
            viewModel.saveSelectedProtocol(
                availableProtocols.first()
            )
        }
    }

    val nationalServers =
        organizedServers
            ?.filter {
                selectedProtocol == null ||
                    it.protocol == selectedProtocol
            }
            ?.groupBy {
                it.country
            }
            ?.map { (country, servers) ->

                NationaltyServer(
                    name = country,
                    numServer = servers.size,
                    bestPing = "",
                    flag = World.getFlag(country),
                    servers = servers.map {

                        ServerItem(
                            id = it.serverid,
                            name = it.name,
                            defaultPing = "",
                            ip = it.address,
                            config = it.config,
                            protocol = it.protocol
                        )
                    }
                )
            }
            ?: emptyList()

    val scope =
        rememberCoroutineScope()

    var showProtocolSheet =
        false

    FullKotlinTheme {

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.fullColors.background
                    )
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
            ) {

                /*
                 * پروتکل‌ها
                 */
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 20.dp,
                                vertical = 12.dp
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    availableProtocols.forEach { protocol ->

                        val selected =
                            protocol ==
                                selectedProtocol

                        Box(
                            modifier =
                                Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            12.dp
                                        )
                                    )
                                    .background(
                                        if (selected) {
                                            MaterialTheme
                                                .fullColors
                                                .purple
                                        } else {
                                            MaterialTheme
                                                .fullColors
                                                .serverItemBackground
                                        }
                                    )
                                    .clickable {

                                        scope.launch {

                                            viewModel
                                                .saveSelectedProtocol(
                                                    protocol
                                                )
                                        }
                                    }
                                    .padding(
                                        horizontal = 14.dp,
                                        vertical = 8.dp
                                    )
                        ) {

                            Text(
                                text =
                                    protocol.name,
                                color =
                                    if (selected) {
                                        Color.White
                                    } else {
                                        MaterialTheme
                                            .fullColors
                                            .whitBlack
                                    },
                                fontFamily =
                                    FontFamily(
                                        Font(
                                            R.font.yekanbakh_medium
                                        )
                                    )
                            )
                        }
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                /*
                 * لیست کشورها و سرورها
                 */
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                ) {

                    items(
                        items = nationalServers,
                        key = {
                            it.name
                        }
                    ) { nationalServer ->

                        ItemsNationalServer(
                            nationalServer =
                                nationalServer,
                            selectedServer =
                                activeServer,
                            onServerSelected = { selectedItem ->

                                /*
                                 * سرور اصلی از organizedServers
                                 * پیدا می‌شود تا همان آبجکت واقعی
                                 * به activateAndSelectServer داده شود.
                                 */
                                val originalServer =
                                    organizedServers
                                        ?.firstOrNull {
                                            it.country ==
                                                nationalServer.name
                                        }
                                        ?.servers
                                        ?.find {
                                            it.serverid ==
                                                selectedItem.id
                                        }

                                if (
                                    originalServer != null
                                ) {

                                    scope.launch {

                                        /*
                                         * بسیار مهم:
                                         * این قسمت انتخاب خودکار/فعال‌سازی
                                         * سرور را حفظ می‌کند.
                                         */
                                        viewModel
                                            .activateAndSelectServer(
                                                originalServer
                                            )

                                        navController
                                            .popBackStack()
                                    }
                                }
                            },
                            initiallyExpanded =
                                nationalServer.servers.any {
                                    it.id ==
                                        activeServer?.serverid
                                },
                            viewModel =
                                viewModel
                        )
                    }
                }
            }
        }

        if (showProtocolSheet) {

            ProtocolBottomSheet(
                protocols =
                    availableProtocols,
                selectedProtocol =
                    selectedProtocol,
                onProtocolSelected = { protocol ->

                    scope.launch {

                        viewModel
                            .saveSelectedProtocol(
                                protocol
                            )
                    }

                    showProtocolSheet = false
                },
                onDismiss = {
                    showProtocolSheet = false
                }
            )
        }
    }
}

@Composable
fun ItemsNationalServer(
    nationalServer: NationaltyServer,
    selectedServer: Server?,
    onServerSelected: (ServerItem) -> Unit,
    initiallyExpanded: Boolean,
    viewModel: ServersViewModel
) {
    androidx.compose.runtime.key(
        nationalServer.name
    ) {

        var expanded =
            androidx.compose.runtime.remember(
                initiallyExpanded
            ) {
                androidx.compose.runtime.mutableStateOf(
                    initiallyExpanded
                )
            }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 5.dp
                    )
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(14.dp)
                        )
                        .background(
                            MaterialTheme
                                .fullColors
                                .serverItemBackground
                        )
                        .clickable {
                            expanded.value =
                                !expanded.value
                        }
                        .padding(
                            horizontal = 14.dp,
                            vertical = 12.dp
                        ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Image(
                    painter =
                        painterResource(
                            nationalServer.flag
                        ),
                    contentDescription =
                        nationalServer.name,
                    modifier =
                        Modifier
                            .size(34.dp)
                            .clip(
                                RoundedCornerShape(8.dp)
                            )
                )

                Spacer(
                    modifier =
                        Modifier.width(12.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            nationalServer.name,
                        color =
                            MaterialTheme
                                .fullColors
                                .whitBlack,
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge,
                        fontFamily =
                            FontFamily(
                                Font(
                                    R.font.yekanbakh_medium
                                )
                            )
                    )

                    Text(
                        text =
                            "${nationalServer.numServer} Server",
                        color =
                            MaterialTheme
                                .fullColors
                                .gray,
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        fontFamily =
                            FontFamily(
                                Font(
                                    R.font.yekanbakh_medium
                                )
                            )
                    )
                }

                Text(
                    text =
                        if (expanded.value) {
                            "▲"
                        } else {
                            "▼"
                        },
                    color =
                        MaterialTheme
                            .fullColors
                            .gray
                )
            }

            if (expanded.value) {

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                SubServer(
                    servers =
                        nationalServer.servers,
                    selectedServer =
                        selectedServer,
                    onServerSelected =
                        onServerSelected,
                    viewModel =
                        viewModel
                )
            }
        }
    }
}

@Composable
fun SubServer(
    servers: List<ServerItem>,
    selectedServer: Server?,
    onServerSelected: (ServerItem) -> Unit,
    viewModel: ServersViewModel
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    end = 12.dp
                )
    ) {

        servers.forEach { server ->

            ItemsServer(
                server = server,
                isSelected =
                    selectedServer?.serverid ==
                        server.id,
                onSelect = {
                    onServerSelected(server)
                },
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(
                    horizontal = 20.dp
                )
                .clickable {
                    onSelect()
                },
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        /*
         * نام سرور
         */
        Text(
            text =
                server.name,
            style =
                MaterialTheme
                    .typography
                    .bodyMedium,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(
                        end = 10.dp
                    ),
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis,
            color =
                MaterialTheme
                    .fullColors
                    .whitBlack,
            fontFamily =
                FontFamily(
                    Font(
                        R.font.yekanbakh_medium
                    )
                )
        )

        /*
         * فقط آیکون سیگنال
         *
         * هیچ Ping / ms / N/A / ... نمایش داده نمی‌شود.
         */
        Icon(
            painter =
                painterResource(
                    R.drawable.ic_signal_good
                ),
            contentDescription =
                "Signal",
            modifier =
                Modifier
                    .size(20.dp)
                    .padding(
                        end = 2.dp
                    ),
            tint =
                Color.Unspecified
        )

        /*
         * انتخاب سرور
         */
        RadioButton(
            selected =
                isSelected,
            onClick =
                onSelect,
            colors =
                RadioButtonColors(
                    selectedColor =
                        MaterialTheme
                            .fullColors
                            .purple,
                    disabledSelectedColor =
                        MaterialTheme
                            .fullColors
                            .purple,
                    unselectedColor =
                        MaterialTheme
                            .fullColors
                            .unCheckRadioServer,
                    disabledUnselectedColor =
                        MaterialTheme
                            .fullColors
                            .unCheckRadioServer
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

