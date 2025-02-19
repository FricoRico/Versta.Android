package app.versta.translate.adapter.inbound

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TranslateNotificationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CoroutineScope(Dispatchers.Main).launch {
            if (intent.action == Intent.ACTION_PROCESS_TEXT) {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                if (text != null) {
                    TranslateBubbleNotification.showNotification(applicationContext, text)
                }
            }
        }

        super.finish()
    }
}