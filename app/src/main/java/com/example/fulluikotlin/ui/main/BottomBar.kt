package com.example.fulluikotlin.ui.main


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pw.fullvpn.android.R
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors

@Composable
fun BottomBar(
    navController: NavController
) {

    val items = listOf(
        BottomBarItem.Profile,
        BottomBarItem.Apps,
        BottomBarItem.Home,
        BottomBarItem.Servers,
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }

    Box {

        NavigationBar(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            containerColor = MaterialTheme.fullColors.bgBottomBar
        ) {

            items.forEach { item ->
                val selected = currentRoute == item.route

                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        navController.navigateToBottomRoot(item.route)
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = item.icon),
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp),   // 👈 این خط اضافه شود
                            tint = if (selected)
                                MaterialTheme.fullColors.purple
                            else
                                MaterialTheme.fullColors.itemBottomBarUnslected
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                            color = if (selected)
                                MaterialTheme.fullColors.purple
                            else
                                MaterialTheme.fullColors.itemBottomBarUnslected
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }

        if (selectedIndex != -1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.TopStart)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(1f / items.size)
                        .offset(x = (selectedIndex * (1f / items.size)) * 0.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.TopStart)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(1f)
                )
            }
        }

        if (selectedIndex != -1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.TopStart)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        }

        if (selectedIndex != -1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.TopStart)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            if (index == selectedIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(4.dp)
                                        .align(Alignment.TopCenter)
                                        .shadow(
                                            elevation = 12.dp,
                                            shape = RoundedCornerShape(
                                                bottomStart = 50.dp,
                                                bottomEnd = 50.dp
                                            ),
                                            clip = false
                                        )
                                        .background(
                                            color = MaterialTheme.fullColors.purple,
                                            shape = RoundedCornerShape(
                                                bottomStart = 50.dp,
                                                bottomEnd = 50.dp
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun NavController.navigateToBottomRoot(route: String) {
    val currentRoute = currentBackStackEntry?.destination?.route

    if (currentRoute == route) {
        return
    }

    val poppedToExistingRoot = popBackStack(
        route = route,
        inclusive = false
    )

    if (!poppedToExistingRoot) {
        navigate(route) {
            popUpTo(graph.startDestinationId) {
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }
}

sealed class BottomBarItem(
    val route: String,
    val icon: Int,
    val label: String
) {
    object Home : BottomBarItem(
        route = "home",
        icon = R.drawable.ic_home_screen,
        label = "خانه"
    )

    object Profile : BottomBarItem(
        route = "profile",
        icon = R.drawable.ic_profile_screen,
        label = "پروفایل"
    )

    object Servers : BottomBarItem(
        route = "servers",
        icon = R.drawable.ic_servres_screen,
        label = "سرورها"
    )

    object Apps : BottomBarItem(
        route = "splitTunnel",
        icon = R.drawable.ic_apps_screen,
        label = "برنامه‌ها"
    )
}


@Preview(showBackground = true)
@Composable
fun BottomBarPreview() {
    FullKotlinTheme(true) {
        BottomBar(
            navController = rememberNavController()
        )
    }
}

