package com.example.fulluikotlin.ui.screens.splittunnel

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.fulluikotlin.infrastructure.vpn.splittunnel.SplitTunnelPackageResolver
import com.example.fulluikotlin.ui.commponent.LightningLoading
import com.example.fulluikotlin.ui.screens.splittunnel.hint.SplitTunnelHintBottomSheet
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.koin.androidx.compose.koinViewModel
import pw.fullvpn.android.R
import androidx.compose.material3.CircularProgressIndicator

@Composable
fun SplitTunnelScreen(
    navController: NavHostController,
    onItemClick: () -> Unit,
    viewModel: SplitTunnelViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val mode = uiState.mode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        ModeSelector(
            currentMode = mode,
            onModeSelected = { newMode -> viewModel.changeMode(newMode) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        SplitTunnelDescription(mode = mode)

        if (mode != SplitTunnelPackageResolver.MODE_OFF) {
            Spacer(modifier = Modifier.height(10.dp))
            AdvancedSearchBar(
                query = uiState.searchQuery,
                onQueryChange = { query -> viewModel.onSearchChange(query) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        uiState.errorMessage?.let { message ->
            MessageCard(message = message, isError = true)
            Spacer(modifier = Modifier.height(8.dp))
        }

        uiState.successMessage?.let { message ->
            MessageCard(message = message, isError = false)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (mode == SplitTunnelPackageResolver.MODE_OFF) {
            EmptyModeCard()
        } else if (uiState.isLoading) {
            LoadingAppsCard(modifier = Modifier.weight(1f))
        } else {
            val selectedPackages = uiState.selectedPackages
            val orderedApps = uiState.filteredApps.sortedWith(
                compareByDescending<AppItemModel> { app ->
                    if (mode == SplitTunnelPackageResolver.MODE_BLACKLIST) {
                        app.isBlocked || selectedPackages.contains(app.packageName)
                    } else {
                        selectedPackages.contains(app.packageName)
                    }
                }.thenBy { app -> app.name.lowercase() }
            )

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(orderedApps) { app ->
                    val isAdminBlocked = mode == SplitTunnelPackageResolver.MODE_BLACKLIST && app.isBlocked
                    val isChecked = if (isAdminBlocked) {
                        true
                    } else {
                        selectedPackages.contains(app.packageName)
                    }

                    ItemsAppsSplit(
                        app = app,
                        isChecked = isChecked,
                        onCheckedChange = { checked ->
                            if (!isAdminBlocked) {
                                viewModel.onAppChecked(app.packageName, checked)
                            }
                        },
                        enabled = !isAdminBlocked,
                        showAdminBadge = isAdminBlocked
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ConfirmSplitTunnelButton(
                mode = mode,
                selectedCount = selectedPackages.size,
                onClick = { viewModel.confirmSelection() }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (uiState.showHint) {
        SplitTunnelHintBottomSheet(
            onDismiss = { viewModel.dismissHint() }
        )
    }
}

@Composable
private fun LoadingAppsCard(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .wrapContentHeight()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.fullColors.borderCardAccountProfile,
                    shape = RoundedCornerShape(15.dp)
                ),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.fullColors.bgCardAccountProfile
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp) // فاصله‌ی داخلی دلخواه
            ) {
                Text(
                    text = "درحال شناسایی برنامه های موجود",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fullColors.textItemsProfile,
                    textAlign = TextAlign.Start,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                LightningLoading(
                    lightningSize = 30.dp,
                    primaryColor = Color.White,
                    secondaryColor = Color.LightGray
                )
            }
        }
    }
}


@Composable
private fun EmptyModeCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgServesList)
    ) {
        Text(
            text = "در حالت خاموش، هیچ برنامه‌ای به صورت دستی از تونل جدا یا محدود نمی‌شود.",
            color = MaterialTheme.fullColors.textItemsProfile,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Right,
            fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}

@Composable
private fun SplitTunnelDescription(mode: String) {
    val text = when (mode) {
        SplitTunnelPackageResolver.MODE_WHITELIST ->
            "وایت لیست یعنی فقط برنامه‌هایی که انتخاب می‌کنی از VPN استفاده می‌کنند. اگر هیچ برنامه‌ای انتخاب نشود، ذخیره انجام نمی‌شود."

        SplitTunnelPackageResolver.MODE_BLACKLIST ->
            "بلک لیست یعنی برنامه‌هایی که انتخاب می‌کنی از VPN استفاده نمی‌کنند. بعضی برنامه‌ها هم ممکن است به انتخاب ادمین از قبل در بلک لیست باشند و قابل تغییر نباشند."

        else ->
            "خاموش یعنی لیست برنامه‌ها اعمال نمی‌شود و VPN برای برنامه‌ها محدودیت دستی ایجاد نمی‌کند."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgServesList)
    ) {
        Text(
            text = text,
            color = MaterialTheme.fullColors.textItemsProfile,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
            textAlign = TextAlign.Right,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        )
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) Color(0x33FF4D4F) else Color(0x332ED573)
        )
    ) {
        Text(
            text = message,
            color = MaterialTheme.fullColors.whitBlack,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
            textAlign = TextAlign.Right,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )
    }
}

@Composable
private fun ConfirmSplitTunnelButton(
    mode: String,
    selectedCount: Int,
    onClick: () -> Unit
) {
    val title = when (mode) {
        SplitTunnelPackageResolver.MODE_WHITELIST -> "تأیید وایت لیست"
        SplitTunnelPackageResolver.MODE_BLACKLIST -> "تأیید بلک لیست"
        else -> "تأیید"
    }

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.fullColors.purple,
            contentColor = MaterialTheme.fullColors.whit
        )
    ) {
        Text(
            text = "$title ($selectedCount)",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
        )
    }
}

@Composable
fun AdvancedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.fullColors.borderSearchBar,
                shape = RoundedCornerShape(16.dp)
            )
            .background(MaterialTheme.fullColors.bgServesList, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = MaterialTheme.fullColors.whitBlack,
                        fontSize = 16.sp,
                        textDirection = TextDirection.Rtl
                    ),
                    cursorBrush = Brush.verticalGradient(listOf(Color.White, Color.White)),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "جستجو",
                                    color = MaterialTheme.fullColors.borderSearchBar,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = FontFamily(Font(R.font.poppins_regular)),
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = "Search Icon",
                tint = Color.Unspecified
            )
        }
    }
}

@Composable
fun ModeSelector(currentMode: String, onModeSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeChip(
            title = "خاموش",
            selected = currentMode == SplitTunnelPackageResolver.MODE_OFF,
            onClick = { onModeSelected(SplitTunnelPackageResolver.MODE_OFF) },
            modifier = Modifier.weight(1f)
        )
        ModeChip(
            title = "بلک لیست",
            selected = currentMode == SplitTunnelPackageResolver.MODE_BLACKLIST,
            onClick = { onModeSelected(SplitTunnelPackageResolver.MODE_BLACKLIST) },
            modifier = Modifier.weight(1f)
        )
        ModeChip(
            title = "وایت لیست",
            selected = currentMode == SplitTunnelPackageResolver.MODE_WHITELIST,
            onClick = { onModeSelected(SplitTunnelPackageResolver.MODE_WHITELIST) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = FontFamily(Font(R.font.yekanbakh_regular))
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.fullColors.purple,
            selectedLabelColor = MaterialTheme.fullColors.whit,
            disabledContainerColor = MaterialTheme.fullColors.buttonActiveService,
            disabledLabelColor = MaterialTheme.fullColors.buttonActiveService
        )
    )
}

@Composable
fun ItemsAppsSplit(
    app: AppItemModel,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    showAdminBadge: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.fullColors.borderItemsSplit,
                shape = RoundedCornerShape(8.dp)
            )
            .background(MaterialTheme.fullColors.bgServesList, RoundedCornerShape(8.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                modifier = Modifier.size(24.dp),
                checkmarkStroke = Stroke(width = 5f, cap = StrokeCap.Round),
                outlineStroke = Stroke(width = 4f),
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.fullColors.checkBoxItemsSplit.copy(alpha = 0.4f),
                    checkmarkColor = MaterialTheme.fullColors.checkBoxItemsSplit,
                    uncheckedColor = MaterialTheme.fullColors.uncheckBoxItemsSplit,
                    disabledCheckedColor = MaterialTheme.fullColors.checkBoxItemsSplit.copy(alpha = 0.18f),
                    disabledUncheckedColor = MaterialTheme.fullColors.uncheckBoxItemsSplit.copy(alpha = 0.35f),
                    disabledIndeterminateColor = MaterialTheme.fullColors.checkBoxItemsSplit.copy(alpha = 0.18f),
                ),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    color = MaterialTheme.fullColors.textItemSplit,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                    maxLines = 1
                )
                if (showAdminBadge) {
                    Text(
                        text = "انتخاب ادمین",
                        color = MaterialTheme.fullColors.borderSearchBar,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape
            ) {
                Image(
                    painter = rememberDrawablePainter(app.icon),
                    contentDescription = "App Icon",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

data class AppItemModel(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val isBlocked: Boolean
)

@Preview(showBackground = true)
@Composable
fun SplitTunnelPreview() {
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

            SplitTunnelScreen(
                navController = rememberNavController(),
                onItemClick = {}
            )
        }
    }
}
