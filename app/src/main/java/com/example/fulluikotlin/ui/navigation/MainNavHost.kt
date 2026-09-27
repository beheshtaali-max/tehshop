package com.example.fulluikotlin.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.fulluikotlin.ui.screens.aboutus.AboutUsScreen
import com.example.fulluikotlin.ui.screens.splittunnel.SplitTunnelScreen
import com.example.fulluikotlin.ui.screens.activeservice.ActiveServiceScreen
import com.example.fulluikotlin.ui.screens.home.HomeScreen
import com.example.fulluikotlin.ui.screens.profile.ProfileScreen
import com.example.fulluikotlin.ui.screens.servers.ServersScreen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onLogoutNavigate: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                navController = navController,
                onItemClick = {  }
            )
        }
        composable("servers") {
            ServersScreen(
                navController = navController,
                onItemClick = {  }
            )
        }
        composable("splitTunnel") {
            SplitTunnelScreen(
                navController = navController,
                onItemClick = {  }
            )
        }
        composable("profile") {
            ProfileScreen(
                navController = navController,
                onItemClick = {  },
                onLogoutNavigate = onLogoutNavigate
            )
        }
        composable("notifications") {
            HomeScreen(
                navController = navController,
                onItemClick = {  }
            )
        }
        composable("details") {
            HomeScreen(
                navController = navController,
                onItemClick = {  }
            )
        }
        composable("activeService") {
            ActiveServiceScreen(
                navController = navController,
                onItemClick = {  }
            )
        }
        composable("aboutUs") {
            AboutUsScreen(
                navController = navController,
                onItemClick = {  }
            )
        }
    }
}