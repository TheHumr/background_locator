enum TrackingMode { slow, fast }

extension TrackingModeExtension on TrackingMode {
  int get id {
    switch (this) {
      case TrackingMode.slow:
        return 0;
      case TrackingMode.fast:
        return 1;
    }
  }
}
