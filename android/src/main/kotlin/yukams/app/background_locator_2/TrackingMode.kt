package yukams.app.background_locator_2

enum class TrackingMode(val value: Int) {
    Slow(0), Fast(1);

    companion object {
        fun fromInt(value: Int) = values().firstOrNull { it.value == value }
    }
}