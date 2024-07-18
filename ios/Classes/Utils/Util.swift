import CoreLocation

class Util: NSObject {

    class func getAccuracy(key: Int32) -> CLLocationAccuracy {
        switch key {
            case 0:
                return kCLLocationAccuracyKilometer
            case 1:
                return kCLLocationAccuracyHundredMeters
            case 2:
                return kCLLocationAccuracyNearestTenMeters
            case 3:
                return kCLLocationAccuracyBest
            case 4:
                return kCLLocationAccuracyBestForNavigation
            default:
                return kCLLocationAccuracyBestForNavigation
        }
    }

    class func getLocationMap(location: CLLocation) -> NSDictionary {
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
}
