package yukams.app.background_locator_2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BluetoothSensorScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != IsolateHolderService.ACTION_BLUETOOTH_SENSOR_SCAN_RESULT) {
            return
        }
        IsolateHolderService.handleBluetoothSensorScanIntent(intent)
    }
}
