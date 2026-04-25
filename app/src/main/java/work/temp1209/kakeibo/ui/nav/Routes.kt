package work.temp1209.kakeibo.ui.nav

import android.net.Uri

sealed class Route(val value: String) {
    data object Camera : Route("camera")
    data object Preview : Route("preview/{imageUri}") {
        fun create(imageUri: Uri): String = "preview/${Uri.encode(imageUri.toString())}"
    }
    data object List : Route("list")
}

