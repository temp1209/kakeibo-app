package work.temp1209.kakeibo

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
import work.temp1209.kakeibo.ui.camera.CameraScreen
import work.temp1209.kakeibo.ui.nav.Route
import work.temp1209.kakeibo.ui.permissions.requireCameraPermission
import work.temp1209.kakeibo.ui.preview.PreviewScreen
import work.temp1209.kakeibo.ui.theme.KakeiboappTheme
import androidx.compose.ui.unit.dp
import work.temp1209.kakeibo.data.ReceiptRepository
import work.temp1209.kakeibo.ui.list.ReceiptsListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KakeiboappTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNav(innerPadding = innerPadding)
                }
            }
        }
    }
}

@Composable
private fun AppNav(innerPadding: PaddingValues) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repo = remember { ReceiptRepository(context) }

    NavHost(
        navController = navController,
        startDestination = Route.Camera.value,
    ) {
        composable(Route.Camera.value) {
            requireCameraPermission(
                onGranted = {
                    CameraScreen(
                        contentPadding = innerPadding,
                        onCaptured = { uri ->
                            navController.navigate(Route.Preview.create(uri))
                        },
                        onOpenList = { navController.navigate(Route.List.value) },
                    )
                },
                onDenied = {
                    // 最小実装: 拒否されたら説明テキストだけ表示（設定誘導は後で追加）
                    androidx.compose.material3.Text(
                        modifier = Modifier.padding(innerPadding).padding(16.dp),
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
            var confirmSave by remember(imageUri) { mutableStateOf(false) }
            PreviewScreen(
                contentPadding = innerPadding,
                imageUri = imageUri,
                onCancel = { navController.popBackStack() },
                onConfirm = {
                    confirmSave = true
                },
            )

            if (confirmSave) {
                LaunchedEffect(imageUri) {
                    repo.savePendingReceipt(imageUri)
                    navController.navigate(Route.Camera.value) {
                        popUpTo(Route.Camera.value) { inclusive = true }
                    }
                }
            }
        }

        composable(Route.List.value) {
            ReceiptsListScreen(
                contentPadding = innerPadding,
                loadReceipts = { repo.listReceipts() },
                onBackToCamera = { navController.popBackStack() },
            )
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