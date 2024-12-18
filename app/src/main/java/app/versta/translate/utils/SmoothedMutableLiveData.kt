package app.versta.translate.utils

import android.os.Handler
import androidx.lifecycle.MutableLiveData

/**
 * A {@link MutableLiveData} that only emits change events when the underlying data has been stable
 * for the configured amount of time.
 *
 * @param duration time delay to wait in milliseconds
 */
class SmoothedMutableLiveData<T>(private val duration: Long) : MutableLiveData<T>() {
    private var pendingValue: T? = null
    private val runnable = Runnable {
        super.setValue(pendingValue)
    }

    override fun setValue(value: T) {
        if (value != pendingValue) {
            pendingValue = value
            Handler().removeCallbacks(runnable)
            Handler().postDelayed(runnable, duration)
        }
    }
}