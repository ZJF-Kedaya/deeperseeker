package com.deeperseeker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deeperseeker.app.ui.chat.ChatScreen
import com.deeperseeker.app.ui.manage.ManageScreen
import com.deeperseeker.app.ui.settings.SettingsScreen
import com.deeperseeker.app.ui.theme.DeeperSeekerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeeperSeekerTheme {
                DeeperSeekerNavHost()
            }
        }
    }
}

/** Routes: chat is the start destination; the other two are pushed on top. */
private object Routes {
    const val CHAT = "chat"
    const val MANAGE = "manage"
    const val SETTINGS = "settings"
}

@Composable
private fun DeeperSeekerNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CHAT) {
        composable(Routes.CHAT) {
            ChatScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenManage = { navController.navigate(Routes.MANAGE) },
            )
        }
        composable(Routes.MANAGE) {
            ManageScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}