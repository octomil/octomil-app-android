package ai.octomil.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import ai.octomil.app.screens.HomeScreen
import ai.octomil.app.screens.ModelDetailScreen
import ai.octomil.app.screens.PairScreen
import ai.octomil.app.screens.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register for pairing codes received via local HTTP server
        val app = OctomilApplication.instance
        app.onPairingCodeReceived = { code, host, _ ->
            runOnUiThread {
                // Navigate to pair screen with the received code
                val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("octomil://pair?code=$code${if (host != null) "&host=$host" else ""}")
                }
                onNewIntent(deepLinkIntent)
            }
        }

        setContent {
            OctomilTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                                selected = currentRoute == Routes.HOME,
                                onClick = {
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.HOME) { inclusive = true }
                                    }
                                },
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Pair") },
                                label = { Text("Pair") },
                                selected = currentRoute?.startsWith(Routes.PAIR) == true,
                                onClick = {
                                    navController.navigate(Routes.PAIR) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") },
                                selected = currentRoute == Routes.SETTINGS,
                                onClick = {
                                    navController.navigate(Routes.SETTINGS) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                    },
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME,
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                onModelClick = { modelId ->
                                    navController.navigate("${Routes.MODEL_DETAIL}/$modelId")
                                },
                                onPairClick = {
                                    navController.navigate(Routes.PAIR)
                                },
                            )
                        }

                        composable(
                            route = "${Routes.PAIR}?code={code}&token={token}&host={host}&server={server}",
                            deepLinks = listOf(
                                navDeepLink { uriPattern = "octomil://pair?code={code}" },
                                navDeepLink { uriPattern = "octomil://pair?token={token}&host={host}" },
                                navDeepLink { uriPattern = "https://app.octomil.com/pair?code={code}" },
                            ),
                            arguments = listOf(
                                navArgument("code") { type = NavType.StringType; defaultValue = ""; nullable = true },
                                navArgument("token") { type = NavType.StringType; defaultValue = ""; nullable = true },
                                navArgument("host") { type = NavType.StringType; defaultValue = ""; nullable = true },
                                navArgument("server") { type = NavType.StringType; defaultValue = ""; nullable = true },
                            ),
                        ) { backStackEntry ->
                            // Support both code/token and host/server param names
                            val code = backStackEntry.arguments?.getString("code")?.takeIf { it.isNotBlank() }
                                ?: backStackEntry.arguments?.getString("token")?.takeIf { it.isNotBlank() }
                            val host = backStackEntry.arguments?.getString("host")?.takeIf { it.isNotBlank() }
                                ?: backStackEntry.arguments?.getString("server")?.takeIf { it.isNotBlank() }

                            PairScreen(
                                initialCode = code,
                                host = host,
                                onComplete = {
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.HOME) { inclusive = true }
                                    }
                                },
                            )
                        }

                        composable(
                            route = "${Routes.MODEL_DETAIL}/{modelId}",
                            arguments = listOf(
                                navArgument("modelId") { type = NavType.StringType },
                            ),
                        ) { backStackEntry ->
                            val modelId = backStackEntry.arguments?.getString("modelId") ?: ""
                            ModelDetailScreen(
                                modelId = modelId,
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable(Routes.SETTINGS) {
                            SettingsScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

object Routes {
    const val HOME = "home"
    const val PAIR = "pair"
    const val MODEL_DETAIL = "model_detail"
    const val SETTINGS = "settings"
}

@Composable
fun OctomilTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = dynamicColorScheme(),
        typography = Typography(),
        content = content,
    )
}

@Composable
private fun dynamicColorScheme(): ColorScheme {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val context = androidx.compose.ui.platform.LocalContext.current
        dynamicLightColorScheme(context)
    } else {
        lightColorScheme()
    }
}
