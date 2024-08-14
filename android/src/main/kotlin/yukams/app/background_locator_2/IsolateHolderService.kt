package yukams.app.background_locator_2

import android.app.*
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import android.os.BatteryManager
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import yukams.app.background_locator_2.pluggables.DisposePluggable
import yukams.app.background_locator_2.pluggables.InitPluggable
import yukams.app.background_locator_2.pluggables.Pluggable
import yukams.app.background_locator_2.provider.*
import java.util.HashMap
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import yukams.app.background_locator_2.flutter_activity_recognition.service.ActivityRecognitionManager

class IsolateHolderService : MethodChannel.MethodCallHandler, LocationUpdateListener, Service() {
    companion object {
        @JvmStatic
        val ACTION_SHUTDOWN = "SHUTDOWN"

        @JvmStatic
        val ACTION_START = "START"

        @JvmStatic
        val ACTION_UPDATE_NOTIFICATION = "UPDATE_NOTIFICATION"

        @JvmStatic
        private val WAKELOCK_TAG = "IsolateHolderService::WAKE_LOCK"

        @JvmStatic
        var backgroundEngine: FlutterEngine? = null

        @JvmStatic
        private val notificationId = 1

        @JvmStatic
        var isServiceRunning = false

        @JvmStatic
        var trackingMode: TrackingMode = TrackingMode.Slow

        @JvmStatic
        var isServiceInitialized = false

        fun getBinaryMessenger(context: Context?): BinaryMessenger? {
            val messenger = backgroundEngine?.dartExecutor?.binaryMessenger
            return messenger
                ?: if (context != null) {
                    backgroundEngine = FlutterEngine(context)
                    backgroundEngine?.dartExecutor?.binaryMessenger
                }else{
                    messenger
                }
        }
    }

    private var notificationChannelName = "Flutter Locator Plugin"
    private var notificationTitle = "Start Location Tracking"
    private var notificationMsg = "Track location in background"
    private var notificationBigMsg =
        "Background location is on to keep the app up-tp-date with your location. This is required for main features to work properly when the app is not running."
    private var notificationIconColor = 0
    private var icon = 0
    private var wakeLockTime = 60 * 60 * 1000L // 1 hour default wake lock time
    private var locatorClient: BLLocationProvider? = null
    private var chargingStateChangeReceiver: BroadcastReceiver? = null
    internal lateinit var backgroundChannel: MethodChannel
    internal var context: Context? = null
    private var pluggables: ArrayList<Pluggable> = ArrayList()
    private val activityRecognitionManager: ActivityRecognitionManager = ActivityRecognitionManager()

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startLocatorService(this)
        startForeground(notificationId, getNotification())
    }

    private fun start() {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(wakeLockTime)
            }
        }

        // Starting Service as foreground with a notification prevent service from closing
        val notification = getNotification()
        startForeground(notificationId, notification)

        pluggables.forEach {
            context?.let { it1 -> it.onServiceStart(it1) }
        }
    }

    private fun getNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Notification channel is available in Android O and up
            val channel = NotificationChannel(
                Keys.CHANNEL_ID, notificationChannelName,
                NotificationManager.IMPORTANCE_LOW
            )

            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val intent = Intent(this, getMainActivityClass(this))
        intent.action = Keys.NOTIFICATION_ACTION

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, Keys.CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(notificationMsg)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(notificationBigMsg)
            )
            .setSmallIcon(icon)
            .setColor(notificationIconColor)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true) // so when data is updated don't make sound and alert in android 8.0+
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e("IsolateHolderService", "onStartCommand => intent.action : ${intent?.action}")
        if(intent == null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e("IsolateHolderService", "app has crashed, stopping it")
                stopSelf()
            }
            else {
                return super.onStartCommand(intent, flags, startId)
            }
        }

        when {
            ACTION_SHUTDOWN == intent?.action -> {
                isServiceRunning = false
                shutdownHolderService()
            }
            ACTION_START == intent?.action -> {
                if (isServiceRunning) {
                    isServiceRunning = false
                    shutdownHolderService()
                }

                if (!isServiceRunning) {
                    isServiceRunning = true
                    startHolderService(intent)
                }
            }
            ACTION_UPDATE_NOTIFICATION == intent?.action -> {
                if (isServiceRunning) {
                    updateNotification(intent)
                }
            }
        }

        return START_STICKY
    }

    private fun startHolderService(intent: Intent) {
        Log.e("IsolateHolderService", "startHolderService")
        notificationChannelName =
            intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_CHANNEL_NAME).toString()
        notificationTitle =
            intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_TITLE).toString()
        notificationMsg = intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_MSG).toString()
        notificationBigMsg =
            intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_BIG_MSG).toString()
        val iconNameDefault = "ic_launcher"
        var iconName = intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_ICON)
        if (iconName == null || iconName.isEmpty()) {
            iconName = iconNameDefault
        }
        icon = resources.getIdentifier(iconName, "mipmap", packageName)
        notificationIconColor =
            intent.getLongExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_ICON_COLOR, 0).toInt()
        wakeLockTime = intent.getIntExtra(Keys.SETTINGS_ANDROID_WAKE_LOCK_TIME, 60) * 60 * 1000L

        trackingMode = TrackingMode.fromInt(intent.getIntExtra(Keys.SETTINGS_TRACKING_MODE, 0)) ?: TrackingMode.Fast

        locatorClient = context?.let { getLocationClient(it) }
        locatorClient?.requestLocationUpdates(getLocationRequest(trackingMode))

        intent.getBooleanExtra(Keys.SETTINGS_CHARGING_MODE_ENABLED, false).let { enabled ->
            if (enabled) {
                runBlocking {
                    registerChargingStateReceiver()
                }
            }
        }

        intent.getBooleanExtra(Keys.SETTINGS_ACTIVITY_RECOGNITION_ENABLED, false).let { enabled ->
            if (enabled) {
                context?.let { registerActivityRecognition(it) }
            }
        }

        // Fill pluggable list
        if (intent.hasExtra(Keys.SETTINGS_INIT_PLUGGABLE)) {
            pluggables.add(InitPluggable())
        }

        if (intent.hasExtra(Keys.SETTINGS_DISPOSABLE_PLUGGABLE)) {
            pluggables.add(DisposePluggable())
        }

        start()
    }

    private fun shutdownHolderService() {
        Log.e("IsolateHolderService", "shutdownHolderService")
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                if (isHeld) {
                    release()
                }
            }
        }

        unregisterChargingStateReceiver()

        context?.let { unregisterActivityRecognition(it) }

        locatorClient?.removeLocationUpdates()
        stopForeground(true)
        stopSelf()

        pluggables.forEach {
            context?.let { it1 -> it.onServiceDispose(it1) }
        }
    }

    private fun updateNotification(intent: Intent) {
        Log.e("IsolateHolderService", "updateNotification")
        if (intent.hasExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_TITLE)) {
            notificationTitle =
                intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_TITLE).toString()
        }

        if (intent.hasExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_MSG)) {
            notificationMsg =
                intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_MSG).toString()
        }

        if (intent.hasExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_BIG_MSG)) {
            notificationBigMsg =
                intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_BIG_MSG).toString()
        }

        val notification = getNotification()
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    private fun getMainActivityClass(context: Context): Class<*>? {
        val packageName = context.packageName
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        val className = launchIntent?.component?.className ?: return null

        return try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            null
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                Keys.METHOD_SERVICE_INITIALIZED -> {
                    isServiceRunning = true
                }
                else -> result.notImplemented()
            }

            result.success(null)
        } catch (e: Exception) {

        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        super.onDestroy()
    }


    private fun getLocationClient(context: Context): BLLocationProvider {
        return when (PreferencesManager.getLocationClient(context)) {
            LocationClient.Google -> GoogleLocationProviderClient(context, this)
            LocationClient.Android -> AndroidLocationProviderClient(context, this)
        }
    }

    override fun onLocationUpdated(locations: List<HashMap<Any, Any>>?) {
        try {
            context?.let {
                FlutterInjector.instance().flutterLoader().ensureInitializationComplete(
                    it, null
                )
            }

            //https://github.com/flutter/plugins/pull/1641
            //https://github.com/flutter/flutter/issues/36059
            //https://github.com/flutter/plugins/pull/1641/commits/4358fbba3327f1fa75bc40df503ca5341fdbb77d
            // new version of flutter can not invoke method from background thread
            if (locations != null) {
                val callback =
                    context?.let {
                        PreferencesManager.getCallbackHandle(
                            it,
                            Keys.CALLBACK_HANDLE_KEY
                        )
                    } as Long

                val result: HashMap<Any, Any> =
                    hashMapOf(
                        Keys.ARG_CALLBACK to callback,
                        Keys.ARG_LOCATION to locations
                    )

                sendLocationEvent(result)
            }
        } catch (e: Exception) {

        }
    }

    private fun sendLocationEvent(result: HashMap<Any, Any>) {
        invokeBackgroundChannelMethod(Keys.BCM_SEND_LOCATION, result)
    }

    private fun onTrackingModeUpdated(trackingMode: TrackingMode) {
        context?.let {
            FlutterInjector.instance().flutterLoader().ensureInitializationComplete(it, null)

            val callback = PreferencesManager.getCallbackHandle(it, Keys.CALLBACK_HANDLE_KEY) as Long

            val result: HashMap<Any, Any> =
                hashMapOf(
                    Keys.ARG_CALLBACK to callback,
                    Keys.ARG_TRACKING_MODE to trackingMode.value
                )

            sendTrackingModeEvent(result)

        }
    }

    private fun sendTrackingModeEvent(result: HashMap<Any, Any>) {
        invokeBackgroundChannelMethod(Keys.BCM_TRACKING_MODE, result)
    }

    private fun sendActivityRecognitionEvent(result: HashMap<Any, Any>) {
        invokeBackgroundChannelMethod(Keys.BCM_ACTIVITY_RECOGNITION, result)
    }

    private suspend fun registerChargingStateReceiver() {
        chargingStateChangeReceiver = createChargingStateChangeReceiver()
        context?.let {
            it.registerReceiver(chargingStateChangeReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { intent ->
                intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { batteryStatus ->
                    val isChargingMode = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == BatteryManager.BATTERY_STATUS_FULL
                    val trackingMode = if (isChargingMode || PreferencesManager.getTrackingMode(it) == TrackingMode.Fast) TrackingMode.Fast else TrackingMode.Slow
                    delay(2000)
                    reloadLocationUpdates(it, trackingMode)
                }
            }
        }
    }

    private fun unregisterChargingStateReceiver() {
        if (chargingStateChangeReceiver != null) {
            context?.let {
                it.unregisterReceiver(chargingStateChangeReceiver)
                chargingStateChangeReceiver = null
            }
        }
    }

    private fun createChargingStateChangeReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                    BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> {
                        reloadLocationUpdates(context, TrackingMode.Fast)
                    }
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> {
                        reloadLocationUpdates(context, PreferencesManager.getTrackingMode(context))
                    }
                }
            }
        }
    }

    private fun registerActivityRecognition(context: Context) {
        activityRecognitionManager.startService(
                context = context,
                onSuccess = { },
                onError = {
                    Log.e("ACTIVITY RECOGNITION", it.toString())
                },
                updatesListener = {
                    val callback = PreferencesManager.getCallbackHandle(context, Keys.CALLBACK_HANDLE_KEY) as Long

                    val map = hashMapOf<Any, Any>(
                            Keys.ARG_CALLBACK to callback,
                            Keys.ARG_ACTIVITY_RECOGNITION_MODE to it
                    )

                    sendActivityRecognitionEvent(map)
                }
        )
    }

    private fun unregisterActivityRecognition(context: Context) {
        activityRecognitionManager.stopService(context)
    }

    private fun reloadLocationUpdates(context: Context, trackingMode: TrackingMode) {
        if (IsolateHolderService.trackingMode != trackingMode) {
            IsolateHolderService.trackingMode = trackingMode
            onTrackingModeUpdated(trackingMode)

            locatorClient?.removeLocationUpdates()

            locatorClient?.requestLocationUpdates(
                getLocationRequest(
                    trackingMode = trackingMode
                )
            )
        }

    }

    private fun invokeBackgroundChannelMethod(method: String, result: HashMap<Any, Any>) {
        //https://github.com/flutter/plugins/pull/1641
        //https://github.com/flutter/flutter/issues/36059
        //https://github.com/flutter/plugins/pull/1641/commits/4358fbba3327f1fa75bc40df503ca5341fdbb77d
        // new version of flutter can not invoke method from background thread

        if (backgroundEngine != null) {
            context?.let {
                val backgroundChannel =
                    MethodChannel(
                        getBinaryMessenger(it)!!,
                        Keys.BACKGROUND_CHANNEL_ID
                    )
                Handler(it.mainLooper)
                    .post {
                        backgroundChannel.invokeMethod(method, result)
                    }
            }
        }
    }
}
