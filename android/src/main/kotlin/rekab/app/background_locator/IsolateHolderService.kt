package rekab.app.background_locator

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import androidx.core.app.NotificationCompat
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import rekab.app.background_locator.pluggables.DisposePluggable
import rekab.app.background_locator.pluggables.InitPluggable
import rekab.app.background_locator.pluggables.Pluggable
import rekab.app.background_locator.provider.*

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
    }

    private var notificationChannelName = "Flutter Locator Plugin"
    private var notificationTitle = "Start Location Tracking"
    private var notificationMsg = "Track location in background"
    private var notificationBigMsg = "Background location is on to keep the app up-tp-date with your location. This is required for main features to work properly when the app is not running."
    private var notificationIconColor = 0
    private var icon = 0
    private var wakeLockTime = 60 * 60 * 1000L // 1 hour default wake lock time
    private var locatorClient: BLLocationProvider? = null
    private var chargingStateChangeReceiver: BroadcastReceiver? = null
    internal lateinit var backgroundChannel: MethodChannel
    internal lateinit var context: Context
    private var pluggables: ArrayList<Pluggable> = ArrayList()

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
            it.onServiceStart(context)
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
        if (intent == null) {
            return super.onStartCommand(intent, flags, startId)
        }

        when {
            ACTION_SHUTDOWN == intent.action -> {
                isServiceRunning = false
                shutdownHolderService()
            }
            ACTION_START == intent.action -> {
                if (!isServiceRunning) {
                    isServiceRunning = true
                    startHolderService(intent)
                }
            }
            ACTION_UPDATE_NOTIFICATION == intent.action -> {
                if (isServiceRunning) {
                    updateNotification(intent)
                }
            }
        }

        return START_STICKY
    }

    private fun startHolderService(intent: Intent) {
        notificationChannelName = intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_CHANNEL_NAME).toString()
        notificationTitle = intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_TITLE).toString()
        notificationMsg = intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_MSG).toString()
        notificationBigMsg = intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_BIG_MSG).toString()
        val iconNameDefault = "ic_launcher"
        var iconName = intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_ICON)
        if (iconName == null || iconName.isEmpty()) {
            iconName = iconNameDefault
        }
        icon = resources.getIdentifier(iconName, "mipmap", packageName)
        notificationIconColor = intent.getLongExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_ICON_COLOR, 0).toInt()
        wakeLockTime = intent.getIntExtra(Keys.SETTINGS_ANDROID_WAKE_LOCK_TIME, 60) * 60 * 1000L

        trackingMode = if (trackingMode == TrackingMode.Fast || intent.getIntExtra(Keys.SETTINGS_TRACKING_MODE, 0) == 0) TrackingMode.Fast else TrackingMode.Slow

        locatorClient = getLocationClient(context)
        locatorClient?.requestLocationUpdates(
            getLocationRequest(
                trackingMode = trackingMode
            )
        )

        intent.getBooleanExtra(Keys.SETTINGS_CHARGING_MODE_ENABLED, false).let { enabled ->
            if (enabled) {
                registerChargingStateReceiver()
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
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                if (isHeld) {
                    release()
                }
            }
        }

        unregisterChargingStateReceiver()

        locatorClient?.removeLocationUpdates()
        stopForeground(true)
        stopSelf()

        pluggables.forEach {
            it.onServiceDispose(context)
        }
    }

    private fun updateNotification(intent: Intent) {
        if (intent.hasExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_TITLE)) {
            notificationTitle = intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_TITLE).toString()
        }

        if (intent.hasExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_MSG)) {
            notificationMsg = intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_MSG).toString()
        }

        if (intent.hasExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_BIG_MSG)) {
            notificationBigMsg = intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_BIG_MSG).toString()
        }

        val notification = getNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        when (call.method) {
            Keys.METHOD_SERVICE_INITIALIZED -> {
                isServiceRunning = true
            }
            else -> result.notImplemented()
        }

        result.success(null)
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
        FlutterInjector.instance().flutterLoader().ensureInitializationComplete(context, null)

        if (locations != null) {
            val callback = PreferencesManager.getCallbackHandle(context, Keys.CALLBACK_HANDLE_KEY) as Long

            val result: HashMap<Any, Any> =
                hashMapOf(
                    Keys.ARG_CALLBACK to callback,
                    Keys.ARG_LOCATION to locations
                )

            sendLocationEvent(result)
        }
    }

    private fun sendLocationEvent(result: HashMap<Any, Any>) {
        invokeBackgroundChannelMethod(Keys.BCM_SEND_LOCATION, result)
    }

    private fun onTrackingModeUpdated(trackingMode: TrackingMode) {
        FlutterInjector.instance().flutterLoader().ensureInitializationComplete(context, null)

        val callback = PreferencesManager.getCallbackHandle(context, Keys.CALLBACK_HANDLE_KEY) as Long

        val result: HashMap<Any, Any> =
            hashMapOf(
                Keys.ARG_CALLBACK to callback,
                Keys.ARG_TRACKING_MODE to trackingMode.value
            )

        sendTrackingModeEvent(result)
    }

    private fun sendTrackingModeEvent(result: HashMap<Any, Any>) {
        invokeBackgroundChannelMethod(Keys.BCM_TRACKING_MODE, result)
    }

    private fun registerChargingStateReceiver() {
        chargingStateChangeReceiver = createChargingStateChangeReceiver()
        context.registerReceiver(chargingStateChangeReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { intent ->
            intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { batteryStatus ->
                val isChargingMode = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == BatteryManager.BATTERY_STATUS_FULL
                val trackingMode = if (isChargingMode || PreferencesManager.getTrackingMode(context) == TrackingMode.Fast) TrackingMode.Fast else TrackingMode.Slow
                reloadLocationUpdates(context, trackingMode)
            }
        }
    }

    private fun unregisterChargingStateReceiver() {
        if (chargingStateChangeReceiver != null) {
            context.unregisterReceiver(chargingStateChangeReceiver)
            chargingStateChangeReceiver = null
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

    private fun invokeBackgroundChannelMethod(method: String, arguments: Any) {
        //https://github.com/flutter/plugins/pull/1641
        //https://github.com/flutter/flutter/issues/36059
        //https://github.com/flutter/plugins/pull/1641/commits/4358fbba3327f1fa75bc40df503ca5341fdbb77d
        // new version of flutter can not invoke method from background thread
        if (backgroundEngine != null) {
            val backgroundChannel = MethodChannel(backgroundEngine!!.dartExecutor!!.binaryMessenger!!, Keys.BACKGROUND_CHANNEL_ID)
            Handler(context.mainLooper).post {
                backgroundChannel.invokeMethod(method, arguments)
            }
        }
    }

}