enum TrackingMode { slow, fast }

extension TrackingModeExtension on TrackingMode {
  String get name {
    switch (this) {
      case TrackingMode.slow:
        return "Slow";
      case TrackingMode.fast:
        return "Fast";
    }
  }
  int get id {
    switch (this) {
      case TrackingMode.slow:
        return 0;
      case TrackingMode.fast:
        return 1;
    }
  }
}
