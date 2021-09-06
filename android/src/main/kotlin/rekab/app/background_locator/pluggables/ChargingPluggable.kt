package rekab.app.background_locator.pluggables

import android.content.Context
import android.os.Handler
import io.flutter.plugin.common.MethodChannel
import rekab.app.background_locator.IsolateHolderService
import rekab.app.background_locator.Keys
import rekab.app.background_locator.PreferencesManager

class ChargingPluggable : Pluggable {
    override fun setCallback(context: Context, callbackHandle: Long) {
        PreferencesManager.setCallbackHandle(context, Keys.CHARGING_CALLBACK_HANDLE_KEY, callbackHandle)
    }

    override fun onServiceDispose(context: Context) {
        (PreferencesManager.getCallbackHandle(context, Keys.DISPOSE_CALLBACK_HANDLE_KEY))?.let { chargingCallback ->
            val backgroundChannel = MethodChannel(IsolateHolderService.backgroundEngine?.dartExecutor?.binaryMessenger, Keys.BACKGROUND_CHANNEL_ID)
            Handler(context.mainLooper)
                .post {
                    backgroundChannel.invokeMethod(
                        Keys.BCM_CHARGING,
                        hashMapOf(Keys.ARG_CHARGING_CALLBACK to chargingCallback)
                    )
                }
        }
    }
}