package ai.octomil.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import ai.octomil.app.chat.ChatScreen
import ai.octomil.app.chat.ChatViewModel
import ai.octomil.app.screens.HomeScreen
import ai.octomil.app.screens.ModelDetailScreen
import ai.octomil.app.screens.PairScreen
import ai.octomil.app.screens.LabsScreen
import ai.octomil.app.screens.SettingsScreen
import ai.octomil.app.ui.OctomilTheme
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
                        // Hide bottom nav on chat screen
                        if (currentRoute?.startsWith(Routes.CHAT) != true) {
                            NavigationBar(
                                tonalElevation = 0.dp,
                                containerColor = MaterialTheme.colorScheme.surface,
                            ) {
                                val items = listOf(
                                    NavItem(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
                                    NavItem(Routes.PAIR, "Pair", Icons.Outlined.QrCodeScanner, Icons.Filled.QrCodeScanner),
                                    NavItem(Routes.LABS, "Labs", Icons.Outlined.Science, Icons.Filled.Science),
                                    NavItem(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
                                )
                                items.forEach { item ->
                                    val selected = if (item.route == Routes.PAIR) {
                                        currentRoute?.startsWith(Routes.PAIR) == true
                                    } else {
                                        currentRoute == item.route
                                    }
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                if (selected) item.selectedIcon else item.icon,
                                                contentDescription = item.label,
                                            )
                                        },
                                        label = {
                                            Text(
                                                item.label,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(item.route) {
                                                if (item.route == Routes.HOME) {
                                                    popUpTo(Routes.HOME) { inclusive = true }
                                                } else {
                                                    launchSingleTop = true
                                                }
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        ),
                                    )
                                }
                            }
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
                            )
                        }

                        composable(
                            route = "${Routes.PAIR}?code={code}&token={token}&host={host}&server={server}",
                            deepLinks = listOf(
                                // Path-based: /pair/CODE (new short format)
                                navDeepLink { uriPattern = "octomil://pair/{code}" },
                                navDeepLink { uriPattern = "https://octomil.com/pair/{code}" },
                                navDeepLink { uriPattern = "https://app.octomil.com/pair/{code}" },
                                // Query-based: /pair?token=CODE (legacy)
                                navDeepLink { uriPattern = "octomil://pair?code={code}" },
                                navDeepLink { uriPattern = "octomil://pair?token={token}&host={host}" },
                                navDeepLink { uriPattern = "https://octomil.com/pair?token={token}" },
                                navDeepLink { uriPattern = "https://octomil.com/pair?token={token}&host={host}" },
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
                                onNavigateToChat = { modelName ->
                                    val encoded = URLEncoder.encode(modelName, "UTF-8")
                                    navController.navigate("${Routes.CHAT}/$encoded")
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
                            val model = OctomilApplication.instance.pairedModels
                                .firstOrNull { it.name == modelId }
                            if (model != null) {
                                ModelDetailScreen(
                                    model = model,
                                    onTryModel = { modelName ->
                                        val encoded = URLEncoder.encode(modelName, "UTF-8")
                                        navController.navigate("${Routes.CHAT}/$encoded")
                                    },
                                )
                            }
                        }

                        composable(
                            route = "${Routes.CHAT}/{modelName}",
                            arguments = listOf(
                                navArgument("modelName") { type = NavType.StringType },
                            ),
                        ) { backStackEntry ->
                            val modelName = URLDecoder.decode(
                                backStackEntry.arguments?.getString("modelName") ?: "",
                                "UTF-8",
                            )
                            val viewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                factory = ChatViewModelFactory(
                                    application = navController.context.applicationContext as android.app.Application,
                                    modelName = modelName,
                                ),
                            )
                            ChatScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable(Routes.LABS) {
                            LabsScreen()
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
    const val CHAT = "chat"
    const val LABS = "labs"
    const val SETTINGS = "settings"
}

class ChatViewModelFactory(
    private val application: android.app.Application,
    private val modelName: String,
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChatViewModel(application, modelName) as T
    }
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)
