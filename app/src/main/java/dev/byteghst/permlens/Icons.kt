package dev.byteghst.permlens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Material icons that are not in material-icons-core, drawn as tiny vectors so the
 * (huge) extended icon set is not needed. If a path ever fails to parse, a plain dot is
 * used instead so the app can never crash on launch because of an icon.
 */
private const val FALLBACK_PATH = "M12,6a6,6 0 1,0 0.001,0z"

private fun build(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        for (p in paths) addPath(pathData = addPathNodes(p), fill = SolidColor(Color.Black))
    }.build()

private fun safe(name: String, vararg paths: String): ImageVector =
    try {
        build(name, *paths)
    } catch (t: Throwable) {
        build(name, FALLBACK_PATH)
    }

object AppIcons {
    val Apps: ImageVector by lazy {
        safe(
            "Apps",
            "M4,8h4V4H4V8zM10,20h4v-4h-4V20zM4,20h4v-4H4V20zM4,14h4v-4H4V14zM10,14h4v-4h-4V14zM16,4v4h4V4H16zM10,8h4V4h-4V8zM16,14h4v-4h-4V14zM16,20h4v-4h-4V20z"
        )
    }

    val Tune: ImageVector by lazy {
        safe(
            "Tune",
            "M3,17v2h6v-2L3,17zM3,5v2h10L13,5L3,5zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2L3,11v2h4v2h2L9,9L7,9zM21,13v-2L11,11v2h10zM15,9h2L17,7h4L21,5h-4L17,3h-2v6z"
        )
    }

    val History: ImageVector by lazy {
        safe(
            "History",
            "M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z"
        )
    }

    val MoreHoriz: ImageVector by lazy {
        safe(
            "MoreHoriz",
            "M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"
        )
    }

    val Block: ImageVector by lazy {
        safe(
            "Block",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM4,12c0,-4.42 3.58,-8 8,-8 1.85,0 3.55,0.63 4.9,1.69L5.69,16.9C4.63,15.55 4,13.85 4,12zM12,20c-1.85,0 -3.55,-0.63 -4.9,-1.69L18.31,7.1C19.37,8.45 20,10.15 20,12c0,4.42 -3.58,8 -8,8z"
        )
    }

    val Snooze: ImageVector by lazy {
        safe(
            "Snooze",
            "M7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85zM22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7zM9,11h2.63L9,15v2h6v-2h-2.63L15,11V9H9v2z"
        )
    }
}
