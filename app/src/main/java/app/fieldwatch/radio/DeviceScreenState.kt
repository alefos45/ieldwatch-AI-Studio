package app.fieldwatch.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FASE 4 (Bloque 3): estado de la pantalla.
 *
 * ACTION_SCREEN_ON / ACTION_SCREEN_OFF son broadcasts de sistema; el
 * receiver se registra con RECEIVER_NOT_EXPORTED (obligatorio en API 34+,
 * seguro en todas porque son protected-broadcast). El valor inicial viene de
 * PowerManager.isInteractive() porque el receiver no dispara al registrarse.
 *
 * Vive en el proceso; no hay ciclo de vida asociado. FieldwatchApp lo arranca
 * en onCreate.
 */
class DeviceScreenState(private val context: Context) {
    private val _screenOn = MutableStateFlow(initial())
    val screenOn: StateFlow<Boolean> = _screenOn.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> _screenOn.value = true
                Intent.ACTION_SCREEN_OFF -> _screenOn.value = false
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered = true
        _screenOn.value = initial()
    }

    fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    private fun initial(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return pm.isInteractive
    }
}