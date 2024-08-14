import 'package:background_locator_2/tracking_mode.dart';

class LocationAccuracy {
  const LocationAccuracy._internal(this.value);

  final int value;

  static const POWERSAVE = LocationAccuracy._internal(0);
  static const LOW = LocationAccuracy._internal(1);
  static const BALANCED = LocationAccuracy._internal(2);
  static const HIGH = LocationAccuracy._internal(3);
  static const NAVIGATION = LocationAccuracy._internal(4);
}

class LocatorSettings {
  final LocationAccuracy accuracy;
  final double distanceFilter;
  final TrackingMode trackingMode;
  final bool chargingModeEnabled;
  final bool activityRecognitionEnabled;

  /// [accuracy] The accuracy of location, Default is max accuracy NAVIGATION.
  ///
  /// [distanceFilter] distance in meter to trigger location update, Default is 0 meter.
  const LocatorSettings({required this.accuracy, required this.distanceFilter, required this.trackingMode, required this.chargingModeEnabled, required this.activityRecognitionEnabled});
}
