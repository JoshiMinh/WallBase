enum WallpaperFilter {
  none,
  grayscale,
  sepia,
}

abstract class WallpaperCrop {
  static const auto = WallpaperCropAuto();
  static const original = WallpaperCropOriginal();
  static const square = WallpaperCropSquare();

  const WallpaperCrop();

  String get displayName;
}

class WallpaperCropAuto extends WallpaperCrop {
  const WallpaperCropAuto();
  @override
  String get displayName => 'Auto';
}

class WallpaperCropOriginal extends WallpaperCrop {
  const WallpaperCropOriginal();
  @override
  String get displayName => 'Original';
}

class WallpaperCropSquare extends WallpaperCrop {
  const WallpaperCropSquare();
  @override
  String get displayName => 'Square';
}

class WallpaperCropCustom extends WallpaperCrop {
  final WallpaperCropSettings settings;
  const WallpaperCropCustom(this.settings);
  @override
  String get displayName => 'Custom';
}

class WallpaperCropSettings {
  final double left;
  final double top;
  final double right;
  final double bottom;

  const WallpaperCropSettings({
    this.left = 0.0,
    this.top = 0.0,
    this.right = 1.0,
    this.bottom = 1.0,
  });

  double get widthFraction => (right - left).clamp(0.0, 1.0);
  double get heightFraction => (bottom - top).clamp(0.0, 1.0);

  double get aspectRatio {
    final w = widthFraction;
    final h = heightFraction;
    if (w <= 0 || h <= 0) return 1.0;
    return w / h;
  }

  WallpaperCropSettings sanitized({double minSize = 0.05}) {
    double l = left.clamp(0.0, 1.0);
    double r = right.clamp(0.0, 1.0);
    double t = top.clamp(0.0, 1.0);
    double b = bottom.clamp(0.0, 1.0);

    if (r < l) {
      final tmp = l;
      l = r;
      r = tmp;
    }
    if (b < t) {
      final tmp = t;
      t = b;
      b = tmp;
    }

    if ((r - l).abs() < minSize) {
      final mid = (l + r) / 2.0;
      l = (mid - minSize / 2.0).clamp(0.0, 1.0);
      r = (mid + minSize / 2.0).clamp(0.0, 1.0);
    }

    if ((b - t).abs() < minSize) {
      final mid = (t + b) / 2.0;
      t = (mid - minSize / 2.0).clamp(0.0, 1.0);
      b = (mid + minSize / 2.0).clamp(0.0, 1.0);
    }

    return WallpaperCropSettings(
      left: l,
      top: t,
      right: r,
      bottom: b,
    );
  }

  String encode() => '$left,$top,$right,$bottom';

  static WallpaperCropSettings? decode(String? value) {
    if (value == null || value.isEmpty) return null;
    final parts = value.split(',');
    if (parts.length != 4) return null;
    try {
      return WallpaperCropSettings(
        left: double.parse(parts[0]),
        top: double.parse(parts[1]),
        right: double.parse(parts[2]),
        bottom: double.parse(parts[3]),
      ).sanitized();
    } catch (_) {
      return null;
    }
  }
}

class WallpaperAdjustments {
  final double brightness;
  final double hue;
  final WallpaperFilter filter;
  final WallpaperCrop crop;

  const WallpaperAdjustments({
    this.brightness = 0.0,
    this.hue = 0.0,
    this.filter = WallpaperFilter.none,
    this.crop = WallpaperCrop.auto,
  });

  bool get isIdentity =>
      brightness == 0.0 &&
      hue == 0.0 &&
      filter == WallpaperFilter.none &&
      crop == WallpaperCrop.auto;

  WallpaperCropSettings? get cropSettings =>
      crop is WallpaperCropCustom ? (crop as WallpaperCropCustom).settings : null;

  WallpaperAdjustments copyWith({
    double? brightness,
    double? hue,
    WallpaperFilter? filter,
    WallpaperCrop? crop,
  }) {
    return WallpaperAdjustments(
      brightness: brightness ?? this.brightness,
      hue: hue ?? this.hue,
      filter: filter ?? this.filter,
      crop: crop ?? this.crop,
    );
  }
}
