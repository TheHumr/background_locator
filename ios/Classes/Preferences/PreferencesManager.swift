class PreferencesManager: NSObject {

    class func getCallbackDispatcherHandle() -> Int64 {
        let handle = UserDefaults.standard.object(forKey: kCallbackDispatcherKey)
        if handle == nil {
            return 0
        }
        return handle as! Int64
    }

    class func setCallbackDispatcherHandle(handle: Int64) {
        UserDefaults.standard.set(handle, forKey: kCallbackDispatcherKey)
    }

    class func getCallbackHandle(key: String) -> Int64 {
        let handle = UserDefaults.standard.object(forKey: key)
        if handle == nil {
            return 0
        }
        return handle as! Int64
    }

    class func setCallbackHandle(handle: Int64, key: String) {
        UserDefaults.standard.set(handle, forKey: key)
    }
    
    class func saveAccuracy(accuracy: Double) {
        UserDefaults.standard.set(accuracy, forKey: kAccuracyKey)
    }

    class func getAccuracy() -> Double {
        return UserDefaults.standard.double(forKey: kAccuracyKey)
    }

    class func saveDistanceFilter(distance: Double) {
        UserDefaults.standard.set(distance, forKey: kDistanceFilterKey)
    }

    class func getDistanceFilter() -> Double {
        return UserDefaults.standard.double(forKey: kDistanceFilterKey)
    }

    class func setObservingRegion(observing: Bool) {
        UserDefaults.standard.set(observing, forKey: kPrefObservingRegion)
    }

    class func isObservingRegion() -> Bool {
        return UserDefaults.standard.bool(forKey: kPrefObservingRegion)
    }

    class func setServiceRunning(running: Bool) {
        UserDefaults.standard.set(running, forKey: kPrefServiceRunning)
    }

    class func isServiceRunning() -> Bool {
        return UserDefaults.standard.bool(forKey: kPrefServiceRunning)
    }

    class func setStopWithTerminate(terminate: Bool) {
        UserDefaults.standard.set(terminate, forKey: kPrefStopWithTerminate)
    }

    class func isStopWithTerminate() -> Bool {
        return UserDefaults.standard.bool(forKey: kPrefStopWithTerminate)
    }
}
