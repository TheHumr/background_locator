import 'dart:io' show Platform;

import 'keys.dart';

class LocationDto {
  final double latitude;
  final double longitude;
  final double accuracy;
  final double altitude;
  final double speed;
  final double speedAccuracy;
  final double heading;
  final double time;
  final bool isMocked;
  final String? provider;
  final bool isChargingMode;

  LocationDto._(this.latitude,
      this.longitude,
      this.accuracy,
      this.altitude,
      this.speed,
      this.speedAccuracy,
      this.heading,
      this.time,
      this.isMocked,
      this.provider,
      this.isChargingMode,);

  factory LocationDto.fromJson(Map<dynamic, dynamic> json) {
    bool isLocationMocked = Platform.isAndroid ? json[Keys.ARG_IS_MOCKED] : false;
    return LocationDto._(
      json[Keys.ARG_LATITUDE],
      json[Keys.ARG_LONGITUDE],
      json[Keys.ARG_ACCURACY],
      json[Keys.ARG_ALTITUDE],
      json[Keys.ARG_SPEED],
      json[Keys.ARG_SPEED_ACCURACY],
      json[Keys.ARG_HEADING],
      json[Keys.ARG_TIME],
      isLocationMocked,
      json[Keys.ARG_PROVIDER],
      json[Keys.ARG_IS_CHARGING] ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      Keys.ARG_LATITUDE: this.latitude,
      Keys.ARG_LONGITUDE: this.longitude,
      Keys.ARG_ACCURACY: this.accuracy,
      Keys.ARG_ALTITUDE: this.altitude,
      Keys.ARG_SPEED: this.speed,
      Keys.ARG_SPEED_ACCURACY: this.speedAccuracy,
      Keys.ARG_HEADING: this.heading,
      Keys.ARG_TIME: this.time,
      Keys.ARG_IS_MOCKED: this.isMocked,
      Keys.ARG_PROVIDER: this.provider,
      Keys.ARG_IS_CHARGING: this.isChargingMode,
    };
  }

  @override
  String toString() {
    return "latitude: ${latitude.toStringAsFixed(6)}, longitude: ${longitude.toStringAsFixed(6)}, altitude: ${altitude.round()}, accuracy: ${accuracy.round()}, bearing: ${heading
        .round()}, speed: ${speed.round()}, time: ${DateTime.fromMillisecondsSinceEpoch(time.toInt())}";
  }
}
