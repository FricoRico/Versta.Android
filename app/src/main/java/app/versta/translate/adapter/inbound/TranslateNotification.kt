package app.versta.translate.adapter.inbound

import android.content.Context

interface TranslateNotification {
    /**
     * Shows a notification with the given text to be translated.
     */
    fun showNotification(context: Context, text: CharSequence)
}