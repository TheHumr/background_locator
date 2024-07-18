class InitPluggable: NSObject {
    private var isInitCallbackCalled: Bool

    override init() {
        isInitCallbackCalled = false
    }

    func onServiceDispose() {
        isInitCallbackCalled = false
    }

    func onServiceStart(initialDataDictionary: NSDictionary) {
        if !isInitCallbackCalled {
            let map: NSDictionary = [
                kArgInitCallback: PreferencesManager.getCallbackHandle(key: kInitCallbackKey),
                kArgInitDataCallback: initialDataDictionary,
            ]
            BackgroundLocatorPlugin.instance?.invokeMethod(method: kBCMInit, arguments: map)
        }
        isInitCallbackCalled = true
    }

    func setCallback(callbackHandle: Int64) {
        PreferencesManager.setCallbackHandle(handle: callbackHandle, key: kInitCallbackKey)
    }
}
