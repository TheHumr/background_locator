enum TrackingMode {
  slow(0),
  fast(1);

  final int id;

  const TrackingMode(this.id);

  static TrackingMode? getById(int id) {
    return TrackingMode.values.firstWhere((e) => e.id == id);
  }
}
