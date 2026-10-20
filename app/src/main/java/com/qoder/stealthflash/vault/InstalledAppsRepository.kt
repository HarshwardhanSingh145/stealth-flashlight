package com.qoder.stealthflash.vault

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ManagedApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
)

/**
 * Discovers installed apps through the QUERY_LAUNCHER_ACTIVITY-style visibility granted by the
 * `<queries>` block in the manifest. Only apps that actually expose a launcher entry appear,
 * which is precisely the set this screen can start.
 *
 * Everything is read from the local [PackageManager]. Nothing is uploaded.
 */
class InstalledAppsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    private val iconCache = LruCache<String, ImageBitmap>(MAX_CACHED_ICONS)

    suspend fun load(
        includeSelf: Boolean = false,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): List<ManagedApp> = withContext(dispatcher) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries = queryLaunchers(intent)

        entries.asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { includeSelf || it != appContext.packageName }
            .mapNotNull { pkg -> toManagedApp(pkg) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** @return false when the package has no launcher entry any more (uninstalled mid-session). */
    suspend fun launch(packageName: String): Boolean = withContext(Dispatchers.Main) {
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ?: return@withContext false
        runCatching { appContext.startActivity(intent) }.isSuccess
    }

    fun invalidate(packageName: String) {
        iconCache.remove(packageName)
    }

    private fun queryLaunchers(intent: Intent): List<android.content.pm.ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private fun toManagedApp(packageName: String): ManagedApp? = runCatching {
        val appInfo: ApplicationInfo = pm.getApplicationInfo(packageName, 0) ?: return null
        val label = pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }
        ManagedApp(
            packageName = packageName,
            label = label,
            icon = cachedIcon(packageName, appInfo),
            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            isEnabled = appInfo.enabled,
        )
    }.getOrNull()

    private fun cachedIcon(packageName: String, appInfo: ApplicationInfo): ImageBitmap {
        iconCache.get(packageName)?.let { return it }
        val bitmap = runCatching { pm.getApplicationIcon(appInfo).toSquarerBitmap() }
            .getOrElse { fallbackIcon() }
        iconCache.put(packageName, bitmap)
        return bitmap
    }

    /**
     * Adaptive icons are 108dp with a 72dp visible safe zone; flattening them without that
     * inset makes legacy launchers look oversized next to the rest of the list.
     */
    private fun Drawable.toSquarerBitmap(): ImageBitmap {
        val size = 96
        val source = if (this is AdaptiveIconDrawable) {
            val inset = size * 0.185f
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                setBounds(inset.toInt(), inset.toInt(), (size - inset).toInt(), (size - inset).toInt())
                draw(canvas)
            }
        } else {
            val side = maxOf(intrinsicWidth, intrinsicHeight).coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            Canvas(bmp).also { canvas ->
                val dx = ((side - intrinsicWidth) / 2f)
                val dy = ((side - intrinsicHeight) / 2f)
                setBounds(dx.toInt(), dy.toInt(), (dx + intrinsicWidth).toInt(), (dy + intrinsicHeight).toInt())
                draw(canvas)
            }
            bmp
        }
        return source.asImageBitmap()
    }

    private fun fallbackIcon(): ImageBitmap {
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(0xFF35294A.toInt())
        return bmp.asImageBitmap()
    }

    private companion object {
        /** 96x96 ARGB_8888 is ~36 KB each, so this caps the cache near 18 MB. */
        const val MAX_CACHED_ICONS = 512
    }
}
