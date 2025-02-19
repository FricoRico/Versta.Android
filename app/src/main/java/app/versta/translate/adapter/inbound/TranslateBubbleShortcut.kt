package app.versta.translate.adapter.inbound

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.activity.ComponentActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toAdaptiveIcon
import androidx.core.graphics.drawable.toBitmap
import app.versta.translate.MainApplication.Companion.TRANSLATION_BUBBLE_SHORTCUT_ID
import app.versta.translate.core.entity.Language

object TranslateBubbleShortcut {
    private var manager: ShortcutManager? = null
    private var shortcut: ShortcutInfo.Builder? = null

    /**
     * Registers the shortcut for the translation bubble.
     */
    fun registerForActivity(activity: ComponentActivity) {
        manager = activity.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager

        val intent = Intent(activity, TranslateNotificationActivity::class.java).apply {
            setAction(Intent.ACTION_DEFAULT)
        }

        shortcut = ShortcutInfo.Builder(activity, TRANSLATION_BUBBLE_SHORTCUT_ID)
            .setShortLabel("Translate")
            .setLongLabel("Translate highlighted text in an overlay")
            .setIntent(intent)
            .setLongLived(true)

        manager?.removeAllDynamicShortcuts()
        manager?.addDynamicShortcuts(listOf(shortcut?.build()))
    }

    /**
     * Updates the shortcut icon for the translation bubble.
     */
    fun updateShortcutIcon(context: Context, language: Language) {
        val icon =
            AppCompatResources.getDrawable(context, language.getFlagDrawable(context))?.toBitmap()
                ?.toAdaptiveIcon()

        if (icon == null) {
            return
        }

        shortcut?.setIcon(icon)
        shortcut?.setShortLabel("Translate to ${language.name}")

        manager?.updateShortcuts(listOf(shortcut?.build()))
    }
}