class DisposePluggable: NSObject {

    func onServiceDispose() {
        let map: NSDictionary = [
            kArgDisposeCallback: PreferencesManager.getCallbackHandle(key: kDisposeCallbackKey)
        ]
        BackgroundLocatorPlugin.instance?.invokeMethod(method: kBCMDispose, arguments: map)
    }

    func onServiceStart(initialDataDictionary: NSDictionary) {
        // nop
    }

    func setCallback(callbackHandle: Int64) {
        PreferencesManager.setCallbackHandle(handle: callbackHandle, key: kDisposeCallbackKey)
    }
}
