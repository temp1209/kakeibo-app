package work.temp1209.kakeibo

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.net.toUri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import work.temp1209.kakeibo.ui.camera.CameraScreen
import work.temp1209.kakeibo.ui.detail.ReceiptDetailScreen
import work.temp1209.kakeibo.ui.nav.bottomTabs
import work.temp1209.kakeibo.ui.nav.Route
import work.temp1209.kakeibo.ui.permissions.requireCameraPermission
import work.temp1209.kakeibo.ui.preview.PreviewScreen
import work.temp1209.kakeibo.ui.theme.KakeiboappTheme
import androidx.compose.ui.unit.dp
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.ui.list.ReceiptsListScreen
import work.temp1209.kakeibo.ui.settings.SettingsScreen
import work.temp1209.kakeibo.ui.notifications.NotificationsScreen
import work.temp1209.kakeibo.ui.notifications.AnalysisNotifications
import work.temp1209.kakeibo.ui.analysis.AnalysisScreen
import work.temp1209.kakeibo.ui.review.ReceiptReviewScreen

class MainActivity : ComponentActivity() {
    private val deepLinkReceiptId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AnalysisNotifications.ensureChannel(this)
        deepLinkReceiptId.value = intent.getStringExtra(AnalysisNotifications.EXTRA_RECEIPT_ID)
        setContent {
            KakeiboappTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNav(
                        innerPadding = innerPadding,
                        deepLinkReceiptId = deepLinkReceiptId.value,
                        onConsumeDeepLink = { deepLinkReceiptId.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        deepLinkReceiptId.value = intent.getStringExtra(AnalysisNotifications.EXTRA_RECEIPT_ID)
    }
}

@Composable
private fun AppNav(
    innerPadding: PaddingValues,
    deepLinkReceiptId: String?,
    onConsumeDeepLink: () -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repo = remember { ReceiptRepository(context) }

    // 要件: 起動時に40日超過画像を掃除（冪等）
    LaunchedEffect(Unit) {
        repo.cleanupExpiredImages()
    }

    LaunchedEffect(deepLinkReceiptId) {
        val id = deepLinkReceiptId ?: return@LaunchedEffect
        navController.navigate(Route.ReceiptDetail.create(id))
        onConsumeDeepLink()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                bottomTabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route.value } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route.value) {
                                popUpTo(Route.Camera.value) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { androidx.compose.material3.Icon(tab.icon, contentDescription = tab.label) },
                        label = { androidx.compose.material3.Text(tab.label) },
                    )
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = Route.Camera.value,
            modifier = Modifier.padding(inner),
        ) {
            composable(Route.Camera.value) {
                requireCameraPermission(
                    onGranted = {
                        CameraScreen(
                            contentPadding = PaddingValues(0.dp),
                            onCaptured = { uri ->
                                navController.navigate(Route.Preview.create(uri))
                            },
                            onOpenList = { navController.navigate(Route.List.value) },
                        )
                    },
                    onDenied = {
                        androidx.compose.material3.Text(
                            modifier = Modifier.padding(16.dp),
                            text = "カメラ権限が必要です。設定から許可してください。",
                        )
                    },
                )
            }

            composable(
                route = Route.Preview.value,
                arguments = listOf(navArgument("imageUri") { type = NavType.StringType }),
            ) {
                val encodedUri = it.arguments?.getString("imageUri") ?: ""
                val imageUri = Uri.decode(encodedUri).toUri()
                var saving by remember(imageUri) { mutableStateOf(false) }

                PreviewScreen(
                    contentPadding = PaddingValues(0.dp),
                    imageUri = imageUri,
                    onCancel = { navController.popBackStack() },
                    onConfirm = {
                        // 二重保存防止: 1回押したら無視（UI側の無効化は後で追加してもOK）
                        if (!saving) saving = true
                    },
                )

                if (saving) {
                    LaunchedEffect(imageUri) {
                        val receiptId = repo.savePendingReceipt(imageUri)
                        repo.enqueueAnalysis(receiptId)
                        navController.navigate(Route.Camera.value) {
                            popUpTo(Route.Camera.value) { inclusive = true }
                        }
                    }
                }
            }

            composable(Route.List.value) {
                ReceiptsListScreen(
                    contentPadding = PaddingValues(0.dp),
                    loadReceiptRows = { ym -> repo.listReceiptRowsForMonth(ym) },
                    onOpenReceipt = { id -> navController.navigate(Route.ReceiptDetail.create(id)) },
                )
            }

            composable(Route.Analysis.value) {
                AnalysisScreen(contentPadding = PaddingValues(0.dp), repo = repo)
            }
            composable(Route.Notifications.value) {
                NotificationsScreen(
                    contentPadding = PaddingValues(0.dp),
                    repo = repo,
                    onOpenReceipt = { id -> navController.navigate(Route.ReceiptDetail.create(id)) },
                    onOpenReceiptReview = { id -> navController.navigate(Route.ReceiptReview.create(id)) },
                )
            }
            composable(Route.Settings.value) {
                SettingsScreen(contentPadding = PaddingValues(0.dp))
            }

            composable(
                route = Route.ReceiptReview.value,
                arguments = listOf(navArgument("receiptId") { type = NavType.StringType }),
            ) {
                val receiptId = it.arguments?.getString("receiptId") ?: return@composable
                ReceiptReviewScreen(
                    contentPadding = PaddingValues(0.dp),
                    receiptId = receiptId,
                    repo = repo,
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        navController.popBackStack(Route.List.value, inclusive = false)
                    },
                )
            }

            composable(
                route = Route.ReceiptDetail.value,
                arguments = listOf(navArgument("receiptId") { type = NavType.StringType }),
            ) {
                val receiptId = it.arguments?.getString("receiptId") ?: return@composable
                ReceiptDetailScreen(
                    contentPadding = PaddingValues(0.dp),
                    receiptId = receiptId,
                    loadReceipt = { id -> repo.getReceiptOrNull(id) },
                    loadVisibleItems = { id -> repo.listVisibleReceiptItems(id) },
                    loadImage = { id -> repo.getReceiptImage(id) },
                    loadGeminiJson = { id -> repo.getLatestGeminiJsonOrNull(id) },
                    onBack = { navController.popBackStack() },
                    onOpenReview = { navController.navigate(Route.ReceiptReview.create(receiptId)) },
                    onDeleteReceipt = { id, reason -> repo.softDeleteReceipt(id, reason) },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    KakeiboappTheme {
        androidx.compose.material3.Text("Preview")
    }
}