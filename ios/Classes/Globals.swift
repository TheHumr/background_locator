let kCallbackDispatcherKey: String = "callback_dispatcher_handle_key"
let kCallbackKey: String = "callback_handle_key"
let kInitCallbackKey: String = "init_callback_handle_key"
let kInitDataCallbackKey: String = "init_data_callback_key"
let kDisposeCallbackKey: String = "dispose_callback_handle_key"
let kDistanceFilterKey: String = "distance_filter_key"
let kChannelId: String = "app.yukams/locator_plugin"
let kBackgroundChannelId: String = "app.yukams/locator_plugin_background"

let kMethodServiceInitialized: String = "LocatorService.initialized"
let kMethodPluginInitializeService: String = "LocatorPlugin.initializeService"
let kMethodPluginRegisterLocationUpdate: String = "LocatorPlugin.registerLocationUpdate"
let kMethodPluginUnRegisterLocationUpdate: String = "LocatorPlugin.unRegisterLocationUpdate"
let kMethodPluginIsRegisteredLocationUpdate: String = "LocatorPlugin.isRegisterLocationUpdate"
let kMethodPluginIsServiceRunning: String = "LocatorPlugin.isServiceRunning"
let kMethodPluginUpdateNotification: String = "LocatorPlugin.updateNotification"

let kArgLatitude: String = "latitude"
let kArgLongitude: String = "longitude"
let kArgAccuracy: String = "accuracy"
let kArgAltitude: String = "altitude"
let kArgSpeed: String = "speed"
let kArgSpeedAccuracy: String = "speed_accuracy"
let kArgHeading: String = "heading"
let kArgTime: String = "time"
let kArgCallback: String = "callback"
let kArgInitCallback: String = "initCallback"
let kArgInitDataCallback: String = "initDataCallback"
let kArgDisposeCallback: String = "disposeCallback"
let kArgLocation: String = "location"
let kArgSettings: String = "settings"
let kArgCallbackDispatcher: String = "callbackDispatcher"
let kArgActivityRecognitionMode: String = "activityRecognitionMode"

let kSettingsAccuracy: String = "settings_accuracy"
let kSettingsDistanceFilter: String = "settings_distanceFilter"
let kSettingsShowsBackgroundLocationIndicator: String = "settings_ios_showsBackgroundLocationIndicator"
let kSettingsStopWithTerminate: String = "settings_ios_stopWithTerminate"

let kBCMSendLocation: String = "BCM_SEND_LOCATION"
let kBCMInit: String = "BCM_INIT"
let kBCMDispose: String = "BCM_DISPOSE"
let kBCMActivityRecognition: String = "BCM_ACTIVITY_RECOGNITION"

let kPrefObservingRegion: String = "pref_observingRegion"
let kPrefServiceRunning: String = "pref_serviceRunning"
let kPrefStopWithTerminate: String = "pref_isStopWithTerminate"
