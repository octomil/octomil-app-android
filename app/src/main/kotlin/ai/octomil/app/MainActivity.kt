package ai.octomil.app

import ai.octomil.Octomil
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ai.octomil.app.ui.OctomilColors
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
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    // Auto-test disabled — ORT model loading crashes Samsung HWUI.
    // Speech will use separate process (SpeechService with android:process=":speech").

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
                val coroutineScope = rememberCoroutineScope()

                // Deep-link params for Pair screen
                var pendingPairCode by remember { mutableStateOf<String?>(null) }
                var pendingPairHost by remember { mutableStateOf<String?>(null) }

                val tabItems = remember {
                    listOf(
                        NavItem(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
                        NavItem(Routes.PAIR, "Pair", Icons.Outlined.QrCodeScanner, Icons.Filled.QrCodeScanner),
                        NavItem(Routes.LABS, "Labs", Icons.Outlined.Science, Icons.Filled.Science),
                        NavItem(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
                    )
                }

                val pagerState = rememberPagerState { tabItems.size }
                val isOnTabs = currentRoute == Routes.TABS || currentRoute == null

                // Sync bottom nav tap → pager
                fun navigateToTab(index: Int) {
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Hide bottom nav on detail/chat screens
                        if (isOnTabs) {
                            NavigationBar(
                                tonalElevation = 0.dp,
                                containerColor = MaterialTheme.colorScheme.surface,
                            ) {
                                tabItems.forEachIndexed { index, item ->
                                    val selected = pagerState.currentPage == index
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
                                        onClick = { navigateToTab(index) },
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
                    val chatPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = 0.dp,
                    )
                    val isChatRoute = currentRoute?.startsWith(Routes.CHAT) == true

                    Box(modifier = Modifier.fillMaxSize()) {
                    // Network status indicator — top-right, always visible
                    run {
                        val isConnected = rememberNetworkConnectivity()

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 12.dp, end = 16.dp)
                                .zIndex(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isConnected) OctomilColors.Emerald400
                                            else MaterialTheme.colorScheme.outline,
                                        ),
                                )
                                Icon(
                                    if (isConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                                    contentDescription = if (isConnected) "Connected" else "Offline",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isConnected) OctomilColors.Emerald400
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Routes.TABS,
                        modifier = Modifier.padding(if (isChatRoute) chatPadding else innerPadding),
                    ) {
                        composable(Routes.TABS) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                            ) { page ->
                                when (page) {
                                    0 -> HomeScreen(
                                        onModelClick = { modelId ->
                                            navController.navigate("${Routes.MODEL_DETAIL}/$modelId")
                                        },
                                    )
                                    1 -> PairScreen(
                                        initialCode = pendingPairCode.also { pendingPairCode = null },
                                        host = pendingPairHost.also { pendingPairHost = null },
                                        onComplete = { navigateToTab(0) },
                                        onNavigateToChat = { modelName ->
                                            val encoded = URLEncoder.encode(modelName, "UTF-8")
                                            navController.navigate("${Routes.CHAT}/$encoded")
                                        },
                                    )
                                    2 -> LabsScreen()
                                    3 -> SettingsScreen()
                                }
                            }
                        }

                        // Deep link handler — navigates to Pair tab with params
                        composable(
                            route = "${Routes.PAIR}?code={code}&token={token}&host={host}&server={server}",
                            deepLinks = listOf(
                                navDeepLink { uriPattern = "octomil://pair/{code}" },
                                navDeepLink { uriPattern = "https://octomil.com/pair/{code}" },
                                navDeepLink { uriPattern = "https://app.octomil.com/pair/{code}" },
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
                            val code = backStackEntry.arguments?.getString("code")?.takeIf { it.isNotBlank() }
                                ?: backStackEntry.arguments?.getString("token")?.takeIf { it.isNotBlank() }
                            val host = backStackEntry.arguments?.getString("host")?.takeIf { it.isNotBlank() }
                                ?: backStackEntry.arguments?.getString("server")?.takeIf { it.isNotBlank() }

                            LaunchedEffect(code, host) {
                                pendingPairCode = code
                                pendingPairHost = host
                                navController.navigate(Routes.TABS) {
                                    popUpTo(Routes.TABS) { inclusive = true }
                                }
                                pagerState.scrollToPage(1)
                            }
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
                    }
                    } // Box
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
    const val TABS = "tabs"
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

/** Observe real WiFi connectivity via ConnectivityManager, recomposing on change. */
@Composable
private fun rememberNetworkConnectivity(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val connectivityManager = remember {
        context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    }

    fun checkWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    var isConnected by remember { mutableStateOf(checkWifi()) }

    DisposableEffect(connectivityManager) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        // Use the default callback (all networks) so onLost fires for any change
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                mainHandler.post { isConnected = checkWifi() }
            }

            override fun onLost(network: android.net.Network) {
                mainHandler.post { isConnected = checkWifi() }
            }

            override fun onCapabilitiesChanged(
                network: android.net.Network,
                caps: android.net.NetworkCapabilities,
            ) {
                mainHandler.post { isConnected = checkWifi() }
            }
        }

        // Monitor all networks so we catch WiFi going away
        val request = android.net.NetworkRequest.Builder().build()
        connectivityManager.registerNetworkCallback(request, callback)

        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    return isConnected
}
