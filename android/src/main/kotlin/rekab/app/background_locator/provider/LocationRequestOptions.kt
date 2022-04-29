package rekab.app.background_locator.provider

class LocationRequestOptions(val interval: Long, val fastestInterval: Long, val maxWaitTime: Long, val accuracy: Int, val distanceFilter: Float)