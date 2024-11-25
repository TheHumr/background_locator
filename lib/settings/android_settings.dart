import 'package:background_locator_2/keys.dart';
import 'package:background_locator_2/settings/locator_settings.dart';
import 'package:background_locator_2/tracking_mode.dart';
import 'package:flutter/material.dart';

enum LocationClient { google, android }

class AndroidNotificationSettings {
  final String notificationChannelName;
  final String notificationTitle;
  final String notificationTitlePaused;
  final String notificationMsg;
  final String notificationMsgPaused;
  final String notificationBigMsg;
  final String notificationBigMsgPaused;
  final String notificationIcon;
  final Color notificationIconColor;
  final VoidCallback? notificationTapCallback;

  /// [notificationTitle] Title of the notification. Only applies for android. Default is 'Start Location Tracking'.
  ///
  /// [notificationMsg] Message of notification. Only applies for android. Default is 'Track location in background'.
  ///
  /// [notificationBigMsg] Message to be displayed in the expanded content area of the notification. Only applies for android. Default is 'Background location is on to keep the app up-tp-date with your location. This is required for main features to work properly when the app is not running.'.
  ///
  /// [notificationIcon] Icon name for notification. Only applies for android. The icon should be in 'mipmap' Directory.
  /// Default is app icon. Icon must comply to android rules to be displayed (transparent background and black/white shape)
  ///
  /// [notificationIconColor] Icon color for notification from notification drawer. Only applies for android. Default color is grey.
  ///
  /// [notificationTapCallback] callback for notification tap
  ///
  const AndroidNotificationSettings(
      {this.notificationChannelName = 'Location tracking',
      this.notificationTitle = 'Start Location Tracking',
      this.notificationTitlePaused = 'Start Location Tracking',
      this.notificationMsg = 'Track location in background',
      this.notificationMsgPaused = 'Track location in background',
      this.notificationBigMsg =
          'Background location is on to keep the app up-tp-date with your location. This is required for main features to work properly when the app is not running.',
      this.notificationBigMsgPaused =
          'Background location is on to keep the app up-tp-date with your location. This is required for main features to work properly when the app is not running.',
      this.notificationIcon = '',
      this.notificationIconColor = Colors.grey,
      this.notificationTapCallback});
}

class AndroidSettings extends LocatorSettings {
  final AndroidNotificationSettings androidNotificationSettings;
  final int wakeLockTime;
  final int interval;
  final int fastestInterval;
  final int maxWaitTime;
  final LocationClient client;

  /// [accuracy] The accuracy of location, Default is max accuracy NAVIGATION.
  ///
  /// [interval] Interval of retrieving location update in second. Only applies for android. Default is 5 second.
  ///
  /// [distanceFilter] distance in meter to trigger location update, Default is 0 meter.
  ///
  /// [androidNotificationSettings] Specific setting for android notification.
  ///
  /// [wakeLockTime] Time for living service in background in minutes. Only applies in android. Default is 60 minute.
  const AndroidSettings(
      {LocationAccuracy accuracy = LocationAccuracy.NAVIGATION,
      this.interval = 5,
      this.fastestInterval = 5,
      this.maxWaitTime = 10,
      double distanceFilter = 0,
      this.androidNotificationSettings = const AndroidNotificationSettings(),
      this.wakeLockTime = 60,
      TrackingMode trackingMode = TrackingMode.fast,
      bool chargingModeEnabled = false,
      bool activityRecognitionEnabled = false,
      this.client = LocationClient.google})
      : super(accuracy: accuracy, distanceFilter: distanceFilter, trackingMode: trackingMode, chargingModeEnabled: chargingModeEnabled, activityRecognitionEnabled: activityRecognitionEnabled);

  Map<String, dynamic> toMap() {
    return {
      Keys.SETTINGS_ACCURACY: accuracy.value,
      Keys.SETTINGS_INTERVAL: interval,
      Keys.SETTINGS_FASTEST_INTERVAL: fastestInterval,
      Keys.SETTINGS_MAX_WAIT_TIME: maxWaitTime,
      Keys.SETTINGS_DISTANCE_FILTER: distanceFilter,
      Keys.SETTINGS_TRACKING_MODE: trackingMode.id,
      Keys.SETTINGS_CHARGING_MODE_ENABLED: chargingModeEnabled,
      Keys.SETTINGS_ACTIVITY_RECOGNITION_ENABLED: activityRecognitionEnabled,
      Keys.SETTINGS_ANDROID_WAKE_LOCK_TIME: wakeLockTime,
      Keys.SETTINGS_ANDROID_NOTIFICATION_CHANNEL_NAME:
          androidNotificationSettings.notificationChannelName,
      Keys.SETTINGS_ANDROID_NOTIFICATION_TITLE:
          androidNotificationSettings.notificationTitle,
      Keys.SETTINGS_ANDROID_NOTIFICATION_TITLE_PAUSED:
          androidNotificationSettings.notificationTitlePaused,
      Keys.SETTINGS_ANDROID_NOTIFICATION_MSG:
          androidNotificationSettings.notificationMsg,
      Keys.SETTINGS_ANDROID_NOTIFICATION_MSG_PAUSED:
          androidNotificationSettings.notificationMsgPaused,
      Keys.SETTINGS_ANDROID_NOTIFICATION_BIG_MSG:
          androidNotificationSettings.notificationBigMsg,
      Keys.SETTINGS_ANDROID_NOTIFICATION_BIG_MSG_PAUSED:
          androidNotificationSettings.notificationBigMsgPaused,
      Keys.SETTINGS_ANDROID_NOTIFICATION_ICON:
          androidNotificationSettings.notificationIcon,
      Keys.SETTINGS_ANDROID_NOTIFICATION_ICON_COLOR:
          androidNotificationSettings.notificationIconColor.value,
      Keys.SETTINGS_ANDROID_LOCATION_CLIENT: client.index
    };
  }
}
