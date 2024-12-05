package yukams.app.background_locator_2.flutter_activity_recognition.models

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class ActivityData(
        @SerializedName("type") val type: String,
        @SerializedName("confidence") val confidence: String,
        @SerializedName("confidenceLevel") val confidenceLevel: Int
) {
    companion object Factory {
        fun unknown(): ActivityData = ActivityData("UNKNOWN", "HIGH", 100)
    }
}
