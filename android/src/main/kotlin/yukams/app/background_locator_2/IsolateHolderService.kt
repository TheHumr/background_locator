package yukams.app.background_locator_2

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import yukams.app.background_locator_2.flutter_activity_recognition.models.ActivityData
import yukams.app.background_locator_2.flutter_activity_recognition.service.ActivityRecognitionManager
import yukams.app.background_locator_2.pluggables.DisposePluggable
import yukams.app.background_locator_2.pluggables.InitPluggable
import yukams.app.background_locator_2.pluggables.Pluggable
import yukams.app.background_locator_2.provider.*

class IsolateHolderService : MethodChannel.MethodCallHandler, LocationUpdateListener, Service() {
    companion object {
        @JvmStatic
        val ACTION_SHUTDOWN = "SHUTDOWN"

        @JvmStatic
        val ACTION_START = "START"

        @JvmStatic
        val ACTION_UPDATE_NOTIFICATION = "UPDATE_NOTIFICATION"

        @JvmStatic
        val ACTION_BLUETOOTH_SENSOR_SCAN_RESULT = "yukams.app.background_locator_2.BLUETOOTH_SENSOR_SCAN_RESULT"

        @JvmStatic
        private val WAKELOCK_TAG = "IsolateHolderService::WAKE_LOCK"

        @JvmStatic
        private val DEFAULT_LOCATION_TRACKING_ACTIVITY_TYPES = setOf("IN_VEHICLE", "ON_BICYCLE", "RUNNING", "WALKING", "ON_FOOT")

        @JvmStatic
        private val BLUETOOTH_SENSOR_SCAN_WINDOW_MS = 10_000L

        @JvmStatic
        private val BLUETOOTH_SENSOR_SCAN_INTERVAL_MS = 15_000L

        @JvmStatic
        private val BLUETOOTH_SENSOR_SCAN_PENDING_INTENT_REQUEST_CODE = 43

        @JvmStatic
        private var locationTrackingActivityTypes = DEFAULT_LOCATION_TRACKING_ACTIVITY_TYPES

        @JvmStatic
        var backgroundEngine: FlutterEngine? = null

        @JvmStatic
        private val notificationId = 1

        @JvmStatic
        var isServiceRunning = false

        @JvmStatic
        var isLocationTracking = false

        @JvmStatic
        var isCharging = false

        @JvmStatic
        var trackingMode: TrackingMode = TrackingMode.Slow

        @JvmStatic
        var activityData: ActivityData = ActivityData.unknown()

        @JvmStatic
        var activityRecognitionEnabled = false

        @JvmStatic
        var bluetoothSensorTrackingEnabled = false

        @JvmStatic
        var bluetoothSensorMac: String? = null

        @JvmStatic
        var bluetoothSensorMissingTimeoutMillis = 120_000L

        @JvmStatic
        private var bluetoothSensorLastSeenAt: Long? = null

        @JvmStatic
        private var activeService: IsolateHolderService? = null

        @JvmStatic
        var isServiceInitialized = false

        fun getBinaryMessenger(context: Context?): BinaryMessenger? {
            val messenger = backgroundEngine?.dartExecutor?.binaryMessenger
            return messenger
                    ?: if (context != null) {
                        backgroundEngine = FlutterEngine(context)
                        backgroundEngine?.dartExecutor?.binaryMessenger
                    } else {
                        messenger
                    }
        }

        fun handleBluetoothSensorScanIntent(intent: Intent) {
            val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
            if (errorCode != 0) {
                return
            }

            val results = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
            }

            if (results.isNullOrEmpty()) {
                return
            }

            val service = activeService ?: return
            results.forEach { service.onBluetoothSensorScanResult(it) }
        }
    }

    private var notificationChannelName = "Flutter Locator Plugin"
    private var notificationTitle = "Start Location Tracking"
    private var notificationTitlePaused = "Start Location Tracking"
    private var notificationMsg = "Track location in background"
    private var notificationMsgPaused = "Track location in background"
    private var notificationBigMsg =
            "Background location is on to keep the app up-tp-date with your location. This is required for main features to work properly when the app is not running."
    private var notificationBigMsgPaused =
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
    private var bluetoothSensorScanCallback: ScanCallback? = null
    private var bluetoothSensorScanPendingIntent: PendingIntent? = null
    private var bluetoothSensorScanSchedulerActive = false
    private var bluetoothSensorScanStartRunnable: Runnable? = null
    private var bluetoothSensorScanWindowStopRunnable: Runnable? = null
    private var bluetoothSensorTimeoutRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        startLocatorService(this)
        startServiceForeground()
    }

    private fun start() {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(wakeLockTime)
            }
        }

        startServiceForeground()

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

        val title = if (isLocationTracking) notificationTitle else notificationTitlePaused
        val msg = if (isLocationTracking) notificationMsg else notificationMsgPaused
        val bigMsg = if (isLocationTracking) notificationBigMsg else notificationBigMsgPaused

        return NotificationCompat.Builder(this, Keys.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(msg)
                .setStyle(
                        NotificationCompat.BigTextStyle()
                                .bigText(bigMsg)
                )
                .setSmallIcon(icon)
                .setColor(notificationIconColor)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true) // so when data is updated don't make sound and alert in android 8.0+
                .setOngoing(true)
                .build()
    }

    private fun startServiceForeground() {
        val notification = getNotification()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification)
        } else {
            val foregroundServiceType = if (bluetoothSensorTrackingEnabled) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(notificationId, notification, foregroundServiceType)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e("IsolateHolderService", "onStartCommand => intent.action : ${intent?.action}")
        if (intent == null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e("IsolateHolderService", "app has crashed, stopping it")
                stopSelf()
            } else {
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

        return START_REDELIVER_INTENT
    }

    private fun startHolderService(intent: Intent) {
        Log.e("IsolateHolderService", "startHolderService")
        notificationChannelName =
                intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_CHANNEL_NAME).toString()
        notificationTitle =
                intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_TITLE).toString()
        notificationTitlePaused =
                intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_TITLE_PAUSED).toString()
        notificationMsg = intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_MSG).toString()
        notificationMsgPaused = intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_MSG_PAUSED).toString()
        notificationBigMsg =
                intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_BIG_MSG).toString()
        notificationBigMsgPaused =
                intent.getStringExtra(Keys.SETTINGS_ANDROID_NOTIFICATION_BIG_MSG_PAUSED).toString()
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

        activityRecognitionEnabled = intent.getBooleanExtra(Keys.SETTINGS_ACTIVITY_RECOGNITION_ENABLED, false)
        locationTrackingActivityTypes = intent.getStringArrayListExtra(Keys.SETTINGS_LOCATION_TRACKING_ACTIVITY_TYPES)?.toSet() ?: DEFAULT_LOCATION_TRACKING_ACTIVITY_TYPES
        bluetoothSensorTrackingEnabled = intent.getBooleanExtra(Keys.SETTINGS_BLUETOOTH_SENSOR_TRACKING_ENABLED, false)
        bluetoothSensorMac = intent.getStringExtra(Keys.SETTINGS_BLUETOOTH_SENSOR_MAC)
        bluetoothSensorMissingTimeoutMillis = intent.getIntExtra(Keys.SETTINGS_BLUETOOTH_SENSOR_MISSING_TIMEOUT_SECONDS, 120) * 1000L
        bluetoothSensorLastSeenAt = null

        if (shouldLocationTrackingBeActive()) {
            registerLocationUpdates(trackingMode)
        } else {
            isLocationTracking = false
            updateNotification()
        }

        intent.getBooleanExtra(Keys.SETTINGS_CHARGING_MODE_ENABLED, false).let { enabled ->
            if (enabled) {
                runBlocking { registerChargingStateReceiver() }
            }
        }

        if (activityRecognitionEnabled) {
            context?.let { registerActivityRecognition(it) }
        }

        if (bluetoothSensorTrackingEnabled && !bluetoothSensorMac.isNullOrBlank()) {
            registerBluetoothSensorScan()
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
        unregisterBluetoothSensorScan()

        unregisterLocationUpdates()
        stopForeground(true)
        stopSelf()

        pluggables.forEach {
            context?.let { it1 -> it.onServiceDispose(it1) }
        }
    }

    private fun updateNotification() {
        val notification = getNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
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
        if (activeService == this) {
            activeService = null
        }
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

    private fun sendActivityRecognitionEvent(context: Context) {
        val callback = PreferencesManager.getCallbackHandle(context, Keys.CALLBACK_HANDLE_KEY) as Long

        val map = hashMapOf<Any, Any>(
                Keys.ARG_CALLBACK to callback,
                Keys.ARG_ACTIVITY_RECOGNITION_MODE to Json.encodeToString(activityData)
        )

        invokeBackgroundChannelMethod(Keys.BCM_ACTIVITY_RECOGNITION, map)
    }

    private fun sendIsLocationTrackingEvent(context: Context) {
        val callback = PreferencesManager.getCallbackHandle(context, Keys.CALLBACK_HANDLE_KEY) as Long

        val map = hashMapOf<Any, Any>(
                Keys.ARG_CALLBACK to callback,
                Keys.ARG_IS_LOCATION_TRACKING to isLocationTracking
        )

        invokeBackgroundChannelMethod(Keys.BCM_IS_LOCATION_TRACKING, map)
    }

    private fun registerLocationUpdates(trackingMode: TrackingMode, sendIsLocationTrackingEvent: Boolean = false) {
        locatorClient?.requestLocationUpdates(getLocationRequest(trackingMode))
        isLocationTracking = true
        updateNotification()
        if (sendIsLocationTrackingEvent) {
            context?.let {
                sendIsLocationTrackingEvent(it)
            }
        }
    }

    private fun unregisterLocationUpdates(sendIsLocationTrackingEvent: Boolean = false) {
        locatorClient?.removeLocationUpdates()
        isLocationTracking = false
        updateNotification()
        if (sendIsLocationTrackingEvent) {
            context?.let {
                sendIsLocationTrackingEvent(it)
            }
        }
    }

    private suspend fun registerChargingStateReceiver() {
        isCharging = false
        chargingStateChangeReceiver = createChargingStateChangeReceiver()
        context?.let {
            it.registerReceiver(chargingStateChangeReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { intent ->
                intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { batteryStatus ->
                    isCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == BatteryManager.BATTERY_STATUS_FULL
                    delay(2000)
                    reloadLocationUpdates(it)
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
                        isCharging = true
                        reloadLocationUpdates(context)
                    }

                    BatteryManager.BATTERY_STATUS_DISCHARGING -> {
                        isCharging = false
                        reloadLocationUpdates(context)
                    }
                }
            }
        }
    }

    private fun registerActivityRecognition(context: Context) {
        activityData = ActivityData.unknown()
        activityRecognitionManager.startService(
                context = context,
                onSuccess = {
                    sendActivityRecognitionEvent(context)
                },
                onError = {
                    Log.e("ACTIVITY RECOGNITION", it.toString())
                },
                updatesListener = {
                    activityData = Json.decodeFromString<ActivityData>(it)

                    sendActivityRecognitionEvent(context)

                    if (isServiceRunning) {
                        if (!isLocationTracking && shouldLocationTrackingBeActive()) {
                            registerLocationUpdates(trackingMode, sendIsLocationTrackingEvent = true)
                        } else if (isLocationTracking && !shouldLocationTrackingBeActive()) {
                            unregisterLocationUpdates(sendIsLocationTrackingEvent = true)
                        }
                    }
                }
        )
    }

    private fun unregisterActivityRecognition(context: Context) {
        activityRecognitionManager.stopService(context)
    }

    private fun registerBluetoothSensorScan() {
        if (bluetoothSensorScanSchedulerActive) {
            return
        }
        bluetoothSensorScanSchedulerActive = true
        scheduleBluetoothSensorScanWindow(0L)
    }

    private fun startBluetoothSensorScanWindow() {
        bluetoothSensorScanStartRunnable = null
        if (!bluetoothSensorScanSchedulerActive) {
            return
        }
        if (bluetoothSensorScanCallback != null || bluetoothSensorScanPendingIntent != null) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            scheduleBluetoothSensorScanWindow(BLUETOOTH_SENSOR_SCAN_INTERVAL_MS)
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null) {
            scheduleBluetoothSensorScanWindow(BLUETOOTH_SENSOR_SCAN_INTERVAL_MS)
            return
        }
        if (!adapter.isEnabled) {
            scheduleBluetoothSensorScanWindow(BLUETOOTH_SENSOR_SCAN_INTERVAL_MS)
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            scheduleBluetoothSensorScanWindow(BLUETOOTH_SENSOR_SCAN_INTERVAL_MS)
            return
        }
        val selectedMac = bluetoothSensorMac
        if (selectedMac == null) {
            scheduleBluetoothSensorScanWindow(BLUETOOTH_SENSOR_SCAN_INTERVAL_MS)
            return
        }

        val filters = try {
            listOf(ScanFilter.Builder().setDeviceAddress(selectedMac).build())
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
        val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setReportDelay(0)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pendingIntent = createBluetoothSensorScanPendingIntent()
            bluetoothSensorScanPendingIntent = pendingIntent
            val errorCode = scanner.startScan(filters, settings, pendingIntent)
            if (errorCode != 0) {
                bluetoothSensorScanPendingIntent = null
                scheduleBluetoothSensorScanWindow(BLUETOOTH_SENSOR_SCAN_INTERVAL_MS)
                return
            }
        } else {
            bluetoothSensorScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    onBluetoothSensorScanResult(result)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { onBluetoothSensorScanResult(it) }
                }
            }
            scanner.startScan(filters, settings, bluetoothSensorScanCallback)
        }
        scheduleBluetoothSensorScanWindowStop()
    }

    private fun unregisterBluetoothSensorScan() {
        bluetoothSensorScanSchedulerActive = false
        bluetoothSensorScanStartRunnable?.let { Handler(mainLooper).removeCallbacks(it) }
        bluetoothSensorScanStartRunnable = null
        bluetoothSensorScanWindowStopRunnable?.let { Handler(mainLooper).removeCallbacks(it) }
        bluetoothSensorScanWindowStopRunnable = null
        bluetoothSensorTimeoutRunnable?.let { Handler(mainLooper).removeCallbacks(it) }
        bluetoothSensorTimeoutRunnable = null

        stopBluetoothSensorScanWindow(scheduleNext = false)
    }

    private fun stopBluetoothSensorScanWindow(scheduleNext: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        bluetoothSensorScanCallback?.let { callback ->
            scanner?.stopScan(callback)
            bluetoothSensorScanCallback = null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            bluetoothSensorScanPendingIntent?.let { pendingIntent ->
                scanner?.stopScan(pendingIntent)
                pendingIntent.cancel()
                bluetoothSensorScanPendingIntent = null
            }
        }
        if (scheduleNext) {
            scheduleBluetoothSensorScanWindow(BLUETOOTH_SENSOR_SCAN_INTERVAL_MS)
        }
    }

    private fun createBluetoothSensorScanPendingIntent(): PendingIntent {
        val intent = Intent(this, BluetoothSensorScanReceiver::class.java).apply {
            action = ACTION_BLUETOOTH_SENSOR_SCAN_RESULT
            setPackage(packageName)
        }
        return PendingIntent.getBroadcast(
                this,
                BLUETOOTH_SENSOR_SCAN_PENDING_INTENT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
        )
    }

    private fun scheduleBluetoothSensorScanWindow(delayMillis: Long) {
        bluetoothSensorScanStartRunnable?.let { Handler(mainLooper).removeCallbacks(it) }
        bluetoothSensorScanStartRunnable = Runnable {
            startBluetoothSensorScanWindow()
        }
        Handler(mainLooper).postDelayed(bluetoothSensorScanStartRunnable!!, delayMillis)
    }

    private fun scheduleBluetoothSensorScanWindowStop() {
        bluetoothSensorScanWindowStopRunnable?.let { Handler(mainLooper).removeCallbacks(it) }
        bluetoothSensorScanWindowStopRunnable = Runnable {
            bluetoothSensorScanWindowStopRunnable = null
            stopBluetoothSensorScanWindow(scheduleNext = bluetoothSensorScanSchedulerActive)
        }
        Handler(mainLooper).postDelayed(bluetoothSensorScanWindowStopRunnable!!, BLUETOOTH_SENSOR_SCAN_WINDOW_MS)
    }

    private fun onBluetoothSensorScanResult(result: ScanResult) {
        val selectedMac = bluetoothSensorMac ?: return
        if (normalizeMac(result.device.address) != normalizeMac(selectedMac)) {
            return
        }

        bluetoothSensorLastSeenAt = SystemClock.elapsedRealtime()
        scheduleBluetoothSensorTimeoutCheck()
        reloadLocationUpdatesForCurrentConditions()
    }

    private fun scheduleBluetoothSensorTimeoutCheck() {
        bluetoothSensorTimeoutRunnable?.let { Handler(mainLooper).removeCallbacks(it) }
        bluetoothSensorTimeoutRunnable = Runnable {
            reloadLocationUpdatesForCurrentConditions()
        }
        Handler(mainLooper).postDelayed(bluetoothSensorTimeoutRunnable!!, bluetoothSensorMissingTimeoutMillis + 1000L)
    }

    private fun normalizeMac(mac: String): String {
        return mac.replace(":", "").uppercase(Locale.US)
    }

    private fun reloadLocationUpdates(context: Context) {
        val trackingMode = if (isCharging) TrackingMode.Fast else PreferencesManager.getTrackingMode(context)
        if (IsolateHolderService.trackingMode != trackingMode) {
            IsolateHolderService.trackingMode = trackingMode
            onTrackingModeUpdated(trackingMode)
            if (isLocationTracking) {
                unregisterLocationUpdates(sendIsLocationTrackingEvent = true)
            }
            if (shouldLocationTrackingBeActive()) {
                registerLocationUpdates(trackingMode, sendIsLocationTrackingEvent = true)
            }
        }
    }

    private fun reloadLocationUpdatesForCurrentConditions() {
        if (!isServiceRunning) return

        val shouldTrackLocation = shouldLocationTrackingBeActive()
        if (!isLocationTracking && shouldTrackLocation) {
            registerLocationUpdates(trackingMode, sendIsLocationTrackingEvent = true)
        } else if (isLocationTracking && !shouldTrackLocation) {
            unregisterLocationUpdates(sendIsLocationTrackingEvent = true)
        }
    }

    private fun shouldLocationTrackingBeActive(): Boolean {
        if (activityRecognitionEnabled) {
            return locationTrackingActivityTypes.contains(activityData.type)
        }

        if (bluetoothSensorTrackingEnabled) {
            val lastSeenAt = bluetoothSensorLastSeenAt ?: return false
            return SystemClock.elapsedRealtime() - lastSeenAt <= bluetoothSensorMissingTimeoutMillis
        }

        return true
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
