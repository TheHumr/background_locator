import 'package:background_locator_2/keys.dart';
import 'package:background_locator_2/tracking_mode.dart';

import 'locator_settings.dart';

class IOSSettings extends LocatorSettings {
  /// [accuracy] The accuracy of location, Default is max accuracy NAVIGATION.
  ///
  /// [distanceFilter] distance in meter to trigger location update, Default is 0 meter.
  ///
  /// [showsBackgroundLocationIndicator] The background location usage indicator is a blue bar or a blue pill in the status bar on iOS. Default is false.
  ///
  /// [stopWithTerminate] stops the location usage when the app is on background

  final bool showsBackgroundLocationIndicator;
  final bool stopWithTerminate;

  const IOSSettings({
    LocationAccuracy accuracy = LocationAccuracy.NAVIGATION,
    double distanceFilter = 0,
    TrackingMode trackingMode = TrackingMode.fast,
    this.showsBackgroundLocationIndicator = false,
    this.stopWithTerminate = false,
    bool activityRecognitionEnabled = false,
  }) : super(accuracy: accuracy, distanceFilter: distanceFilter, trackingMode: trackingMode, chargingModeEnabled: false, activityRecognitionEnabled: activityRecognitionEnabled); //minutes

  Map<String, dynamic> toMap() {
    return {
      Keys.SETTINGS_ACCURACY: accuracy.value,
      Keys.SETTINGS_DISTANCE_FILTER: distanceFilter,
      Keys.SETTINGS_ACTIVITY_RECOGNITION_ENABLED: activityRecognitionEnabled,
      Keys.SETTINGS_IOS_SHOWS_BACKGROUND_LOCATION_INDICATOR: showsBackgroundLocationIndicator,
      Keys.SETTINGS_IOS_STOP_WITH_TERMINATE: stopWithTerminate,
    };
  }
}
