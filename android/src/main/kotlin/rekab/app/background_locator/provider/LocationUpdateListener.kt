package rekab.app.background_locator.provider

import java.util.HashMap

interface LocationUpdateListener {
    fun onLocationUpdated(locations: List<HashMap<Any, Any>>?)
}