package work.temp1209.kakeibo.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomTab(
    val route: Route,
    val label: String,
    val icon: ImageVector,
)

val bottomTabs = listOf(
    BottomTab(Route.List, "一覧", Icons.Filled.Home),
    BottomTab(Route.Analysis, "分析", Icons.Filled.ShowChart),
    BottomTab(Route.Camera, "カメラ", Icons.Filled.CameraAlt),
    BottomTab(Route.Notifications, "通知", Icons.Filled.Notifications),
    BottomTab(Route.Settings, "設定", Icons.Filled.Settings),
)

