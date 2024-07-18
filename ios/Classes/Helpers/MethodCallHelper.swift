import Flutter

class MethodCallHelper: NSObject {

    func handleMethodCall(call: FlutterMethodCall, result: FlutterResult, delegate: MethodCallHelperDelegate) {
        let arguments = call.arguments as? NSDictionary ?? [:]

        if kMethodPluginInitializeService == call.method {
            let callbackDispatcher = arguments.object(forKey: kArgCallbackDispatcher) as! Int64
            delegate.startLocatorService(callbackDispatcher)
            result(true)
        } else if kMethodServiceInitialized == call.method {
            result(nil)
        } else if kMethodPluginRegisterLocationUpdate == call.method {
            let callbackHandle = arguments.object(forKey: kArgCallback) as! Int64
            let initCallbackHandle = arguments.object(forKey: kArgInitCallback) as! Int64
            let initialDataDictionary = arguments.object(forKey: kArgInitDataCallback) as! NSDictionary
            let disposeCallbackHandle = arguments.object(forKey: kArgInitCallback) as! Int64
            BackgroundLocatorPlugin.instance?.setServiceRunning(true)
            delegate.registerLocator(
                callbackHandle,
                initCallback: initCallbackHandle,
                initialDataDictionary: initialDataDictionary,
                disposeCallback: disposeCallbackHandle,
                settings: arguments
            )
            result(true)
        } else if kMethodPluginUnRegisterLocationUpdate == call.method {
            delegate.removeLocator()
            BackgroundLocatorPlugin.instance?.setServiceRunning(false)
            result(true)
        } else if kMethodPluginIsRegisteredLocationUpdate == call.method {
            let val = delegate.isServiceRunning()
            result(val)
        } else if kMethodPluginIsServiceRunning == call.method {
            let val = delegate.isServiceRunning()
            result(val)
        } else if kMethodPluginUpdateNotification == call.method {
            // updating notification's text is just for android
            result(nil)
        } else {
            result(FlutterMethodNotImplemented)
        }
    }
}

protocol MethodCallHelperDelegate {
    func startLocatorService(_ callbackDispatcher: Int64)
    func registerLocator(
        _ callback: Int64,
        initCallback: Int64,
        initialDataDictionary: NSDictionary,
        disposeCallback: Int64,
        settings: NSDictionary)
    func removeLocator()
    func isServiceRunning() -> Bool
    func setServiceRunning(_ value: Bool)
}
