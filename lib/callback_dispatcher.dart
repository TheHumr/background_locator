import 'dart:ui';

import 'package:background_locator/tracking_mode.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import 'keys.dart';
import 'location_dto.dart';

@pragma('vm:entry-point')
void callbackDispatcher() {
  const MethodChannel _backgroundChannel = MethodChannel(Keys.BACKGROUND_CHANNEL_ID);
  WidgetsFlutterBinding.ensureInitialized();

  _backgroundChannel.setMethodCallHandler((MethodCall call) async {
    if (Keys.BCM_SEND_LOCATION == call.method) {
      final Map<dynamic, dynamic> args = call.arguments;
      final List<LocationDto> locationList = <LocationDto>[];
      args[Keys.ARG_LOCATION].forEach((dynamic e) => locationList.add(LocationDto.fromJson(e)));
      final Function callback = PluginUtilities.getCallbackFromHandle(CallbackHandle.fromRawHandle(args[Keys.ARG_CALLBACK]))!;
      callback(locationList, null);
    } else if (Keys.BCM_NOTIFICATION_CLICK == call.method) {
      final Map<dynamic, dynamic> args = call.arguments;
      final Function? notificationCallback = PluginUtilities.getCallbackFromHandle(CallbackHandle.fromRawHandle(args[Keys.ARG_NOTIFICATION_CALLBACK]));
      if (notificationCallback != null) {
        notificationCallback();
      }
    } else if (Keys.BCM_INIT == call.method) {
      final Map<dynamic, dynamic> args = call.arguments;
      final Function? initCallback = PluginUtilities.getCallbackFromHandle(CallbackHandle.fromRawHandle(args[Keys.ARG_INIT_CALLBACK]));
      Map<dynamic, dynamic>? data = args[Keys.ARG_INIT_DATA_CALLBACK];
      if (initCallback != null) {
        initCallback(data);
      }
    } else if (Keys.BCM_TRACKING_MODE == call.method) {
      final Map<dynamic, dynamic> args = call.arguments;
      final int trackingMode = args[Keys.ARG_TRACKING_MODE];
      final Function callback = PluginUtilities.getCallbackFromHandle(CallbackHandle.fromRawHandle(args[Keys.ARG_CALLBACK]))!;
      callback(null, TrackingMode.values[trackingMode]);
    }
  });
  _backgroundChannel.invokeMethod(Keys.METHOD_SERVICE_INITIALIZED);
}
