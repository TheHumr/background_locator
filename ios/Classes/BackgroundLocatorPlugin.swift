import CoreLocation
import CoreMotion
import Flutter
import UIKit

public class BackgroundLocatorPlugin: NSObject, FlutterPlugin, CLLocationManagerDelegate, MethodCallHelperDelegate {

    private var _headlessRunner: FlutterEngine!
    private var _callbackChannel: FlutterMethodChannel!
    private var _mainChannel: FlutterMethodChannel!
    private var _registrar: FlutterPluginRegistrar!
    private var _locationManager: CLLocationManager!
    private var _lastLocation: CLLocation!
    private let _activityManager = CMMotionActivityManager()
    private var activity: CMMotionActivity?
    private var locationTracking: Bool = false {
        didSet {
            sendIsLocationTrackingEvent(value: locationTracking)
        }
    }

    static var registerPlugins: FlutterPluginRegistrantCallback?
    static var instance: BackgroundLocatorPlugin?

    // MARK: FlutterPlugin Methods

    public static func register(with registrar: any FlutterPluginRegistrar) {
        if instance == nil {
            instance = BackgroundLocatorPlugin(registrar: registrar)
            registrar.addApplicationDelegate(instance!)
        }
    }

    public static func setPluginRegistrantCallback(callback: FlutterPluginRegistrantCallback) {
        registerPlugins = callback
    }

    static func getInstance() -> BackgroundLocatorPlugin? {
        return instance
    }

    func invokeMethod(method: String, arguments: AnyObject?) {
        // Return if flutter engine is not ready
        let isolateId = _headlessRunner.isolateId
        if _callbackChannel == nil || isolateId == nil {
            return
        }

        _callbackChannel.invokeMethod(method, arguments: arguments)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        let callHelper = MethodCallHelper()
        callHelper.handleMethodCall(call: call, result: result, delegate: self)
    }

    //https://medium.com/@calvinlin_96474/ios-11-continuous-background-location-update-by-swift-4-12ce3ac603e3
    // iOS will launch the app when new location received
    private func application(application: UIApplication!, didFinishLaunchingWithOptions launchOptions: NSDictionary!) -> Bool {
        // Check to see if we're being launched due to a location event.
        if launchOptions[UIApplication.LaunchOptionsKey.location] != nil {
            // Restart the headless service.
            self.startLocatorService(PreferencesManager.getCallbackDispatcherHandle())
            PreferencesManager.setObservingRegion(observing: true)
        } else if PreferencesManager.isObservingRegion() {
            self.prepareLocationManager()
            self.removeLocator()
            PreferencesManager.setObservingRegion(observing: false)
            _locationManager.startUpdatingLocation()
        }

        // Note: if we return NO, this vetos the launch of the application.
        return true
    }

    private func applicationDidEnterBackground(application: UIApplication!) {
        if PreferencesManager.isServiceRunning() {
            _locationManager.startMonitoringSignificantLocationChanges()
        }
    }

    private func applicationWillTerminate(application: UIApplication!) {
        self.observeRegionForLocation(location: _lastLocation)
        if PreferencesManager.isStopWithTerminate() {
            self.removeLocator()
        }
    }

    func observeRegionForLocation(location: CLLocation!) {
        let distanceFilter: Double = PreferencesManager.getDistanceFilter()
        let region: CLRegion! = CLCircularRegion(center: location.coordinate, radius: distanceFilter, identifier: "region")
        region.notifyOnEntry = false
        region.notifyOnExit = true
        _locationManager.startMonitoring(for: region)
    }

    func prepareLocationMap(location: CLLocation!) {
        _lastLocation = location
        let locationMap: NSDictionary = Util.getLocationMap(location: location)

        self.sendLocationEvent(location: locationMap)
    }

    // MARK: LocationManagerDelegate Methods
    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if locations.count > 0 {
            let location = locations[0]
            self.prepareLocationMap(location: location)
            if PreferencesManager.isObservingRegion() {
                self.observeRegionForLocation(location: location)
                _locationManager.stopUpdatingLocation()
            }
        }
    }

    public func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        _locationManager.stopMonitoring(for: region)
        _locationManager.startUpdatingLocation()
    }

    // MARK: LocatorPlugin Methods
    func sendLocationEvent(location: NSDictionary!) {
        let map: NSDictionary! = [
            kArgCallback: PreferencesManager.getCallbackHandle(key: kCallbackKey),
            kArgLocation: [location],
        ]
        invokeMethod(method: kBCMSendLocation, arguments: map)
    }

    init(registrar: FlutterPluginRegistrar) {
        super.init()

        setServiceRunning(false)

        _headlessRunner = FlutterEngine(name: "LocatorIsolate", project: nil, allowHeadlessExecution: true)
        _registrar = registrar
        self.prepareLocationManager()

        _mainChannel = FlutterMethodChannel(name: kChannelId, binaryMessenger: registrar.messenger())
        registrar.addMethodCallDelegate(self, channel: _mainChannel)

        _callbackChannel =
            FlutterMethodChannel(name: kBackgroundChannelId, binaryMessenger: _headlessRunner.binaryMessenger)
    }

    func prepareLocationManager() {
        _locationManager = CLLocationManager()
        _locationManager.delegate = self
        _locationManager.pausesLocationUpdatesAutomatically = false
    }

    // MARK: ActivityManager Methods
    func registerActivityRecognition() {
        _activityManager.startActivityUpdates(to: OperationQueue.init()) { (activity) in
            if let a = activity {
                self.activity = activity
                
                if !self.locationTracking, a.walking || a.running || a.automotive || a.cycling {
                    self._locationManager.desiredAccuracy = PreferencesManager.getAccuracy()
                    self.locationTracking = true
                } else if self.locationTracking, a.stationary {
                    self._locationManager.desiredAccuracy = kCLLocationAccuracyReduced
                    self.locationTracking = false
                }
                
                self.sendActivityRecognitionEvent(data: a.toJson())
            }
        }
    }
    
    func unregisterActivityRecognition() {
        _activityManager.stopActivityUpdates()
    }
    
    func sendActivityRecognitionEvent(data: NSDictionary) {
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: data)
            let jsonString = String(data: jsonData, encoding: .ascii)
            
            guard let jsonString = jsonString else {
                return
            }
            
            let map: NSDictionary! = [
                kArgCallback: PreferencesManager.getCallbackHandle(key: kCallbackKey),
                kArgActivityRecognitionMode: jsonString,
            ]
            invokeMethod(method: kBCMActivityRecognition, arguments: map)
        } catch {
            debugPrint()
        }
    }
    
    func sendIsLocationTrackingEvent(value: Bool) {
        let map: NSDictionary! = [
            kArgCallback: PreferencesManager.getCallbackHandle(key: kCallbackKey),
            kArgIsLocationTracking: value,
        ]
        invokeMethod(method: kBCMIsLocationTracking, arguments: map)
    }
    
    func getLocationMap(location: CLLocation) -> NSDictionary {
        let timeInSeconds: TimeInterval = location.timestamp.timeIntervalSince1970
        return [
            kArgLatitude: location.coordinate.latitude,
            kArgLongitude: location.coordinate.longitude,
            kArgAccuracy: location.horizontalAccuracy,
            kArgAltitude: location.altitude,
            kArgSpeed: location.speed,
            kArgSpeedAccuracy: 0.0,
            kArgHeading: location.course,
            kArgTime: timeInSeconds * 1000.0,  // in milliseconds since the epoch
        ]
    }

    // MARK: MethodCallHelperDelegate

    func startLocatorService(_ handle: Int64) {
        PreferencesManager.setCallbackDispatcherHandle(handle: handle)

        guard let info = FlutterCallbackCache.lookupCallbackInformation(handle) else {
            return
        }

        let entrypoint = info.callbackName
        let uri = info.callbackLibraryPath
        _headlessRunner.run(withEntrypoint: entrypoint, libraryURI: uri)

        // Once our headless runner has been started, we need to register the application's plugins
        // with the runner in order for them to work on the background isolate. `registerPlugins` is
        // a callback set from AppDelegate.m in the main application. This callback should register
        // all relevant plugins (excluding those which require UI).
        // TODO!
        DispatchQueue.once {
            BackgroundLocatorPlugin.registerPlugins!(_headlessRunner)
        }
        _registrar.addMethodCallDelegate(self, channel: _callbackChannel)
    }

    func registerLocator(_ callback: Int64, initCallback: Int64, initialDataDictionary: NSDictionary, disposeCallback: Int64, settings: NSDictionary) {
        _locationManager.requestAlwaysAuthorization()

        let accuracyKey = settings.object(forKey: kSettingsAccuracy) as! Int32
        let accuracy = Util.getAccuracy(key: accuracyKey)
        let distanceFilter = settings.object(forKey: kSettingsDistanceFilter) as! Double
        let showsBackgroundLocationIndicator = settings.object(forKey: kSettingsShowsBackgroundLocationIndicator) as! Bool
        let stopWithTerminate = settings.object(forKey: kSettingsStopWithTerminate) as! Bool
        let activityRecognitionEnabled = settings.object(forKey: kSettingsActivityRecognitionEnabled) as! Bool

        _locationManager.desiredAccuracy = accuracy
        _locationManager.distanceFilter = distanceFilter

        if #available(iOS 11.0, *) {
            _locationManager.showsBackgroundLocationIndicator = showsBackgroundLocationIndicator
        }

        if #available(iOS 9.0, *) {
            _locationManager.allowsBackgroundLocationUpdates = true
        }

        PreferencesManager.saveAccuracy(accuracy: accuracy)
        PreferencesManager.saveDistanceFilter(distance: distanceFilter)
        PreferencesManager.setStopWithTerminate(terminate: stopWithTerminate)

        PreferencesManager.setCallbackHandle(handle: callback, key: kCallbackKey)

        let initPluggable = InitPluggable()
        initPluggable.setCallback(callbackHandle: initCallback)
        initPluggable.onServiceStart(initialDataDictionary: initialDataDictionary)

        let disposePluggable = DisposePluggable()
        disposePluggable.setCallback(callbackHandle: disposeCallback)
        
        locationTracking = true

        _locationManager.startUpdatingLocation()
        _locationManager.startMonitoringSignificantLocationChanges()
        
        if activityRecognitionEnabled {
            registerActivityRecognition()
        }
    }

    func removeLocator() {
        if _locationManager == nil {
            return
        }

        _locationManager.stopUpdatingLocation()

        if #available(iOS 9.0, *) {
            _locationManager.allowsBackgroundLocationUpdates = false
        }

        _locationManager.stopMonitoringSignificantLocationChanges()

        for region in _locationManager.monitoredRegions {
            _locationManager.stopMonitoring(for: region)
        }

        let disposePluggable = DisposePluggable()
        disposePluggable.onServiceDispose()
        
        unregisterActivityRecognition()
    }

    func setServiceRunning(_ value: Bool) {
        PreferencesManager.setServiceRunning(running: value)
    }

    func isServiceRunning() -> Bool {
        return PreferencesManager.isServiceRunning()
    }
    
    func isLocationTracking() -> Bool {
        return locationTracking
    }
    
    func currentActivity() -> CMMotionActivity? {
        return activity
    }

    func isStopWithTerminate() -> Bool {
        return PreferencesManager.isStopWithTerminate()
    }
}

extension DispatchQueue {
    private static var _onceTracker = [String]()

    public class func once(
        file: String = #file,
        function: String = #function,
        line: Int = #line,
        block: () -> Void
    ) {
        let token = "\(file):\(function):\(line)"
        once(token: token, block: block)
    }

    /**
     Executes a block of code, associated with a unique token, only once.  The code is thread safe and will
     only execute the code once even in the presence of multithreaded calls.

     - parameter token: A unique reverse DNS style name such as com.vectorform.<name> or a GUID
     - parameter block: Block to execute once
     */
    public class func once(
        token: String,
        block: () -> Void
    ) {
        objc_sync_enter(self)
        defer { objc_sync_exit(self) }

        guard !_onceTracker.contains(token) else { return }

        _onceTracker.append(token)
        block()
    }
}

extension CMMotionActivity {
    
    public func toJson() -> NSDictionary {
        return [
            "type": extractActivityType(a: self),
            "confidence": extractActivityConfidence(a: self)
        ]
    }

    func extractActivityType(a: CMMotionActivity) -> String {
        var type = "UNKNOWN"
        switch true {
            case a.stationary:
                type = "STILL"
            case a.walking:
                type = "WALKING"
            case a.running:
                type = "RUNNING"
            case a.automotive:
                type = "IN_VEHICLE"
            case a.cycling:
                type = "ON_BICYCLE"
            default:
                type = "UNKNOWN"
        }
        return type
    }

    func extractActivityConfidence(a: CMMotionActivity) -> String {
        var conf: String

        switch a.confidence {
            case CMMotionActivityConfidence.low:
                conf = "LOW"
            case CMMotionActivityConfidence.medium:
                conf = "MEDIUM"
            case CMMotionActivityConfidence.high:
                conf = "HIGH"
            default:
                conf = "UNKNOWN"
        }
        return conf
    }
}
