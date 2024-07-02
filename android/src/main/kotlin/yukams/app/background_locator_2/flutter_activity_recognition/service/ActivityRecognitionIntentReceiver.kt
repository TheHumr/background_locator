package yukams.app.background_locator_2.flutter_activity_recognition.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult

class ActivityRecognitionIntentReceiver: BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (ActivityRecognitionResult.hasResult(intent)) {
			intent.setClass(context, ActivityRecognitionIntentService::class.java)
			ActivityRecognitionIntentService.enqueueWork(context, intent)
		}
	}
}
