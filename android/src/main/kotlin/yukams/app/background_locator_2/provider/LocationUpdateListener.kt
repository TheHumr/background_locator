package yukams.app.background_locator_2.provider

import java.util.HashMap

interface LocationUpdateListener {
    fun onLocationUpdated(locations: List<HashMap<Any, Any>>?)
}