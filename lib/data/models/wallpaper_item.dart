import 'wallpaper_adjustments.dart';

class WallpaperItem {
  final String id;
  final String title;
  final String imageUrl;
  final String sourceUrl;
  final String? sourceName;
  final String? sourceKey;
  final int? width;
  final int? height;
  final int? addedAt;
  final String? localUri;
  final bool isDownloaded;
  final WallpaperCropSettings? cropSettings;

  WallpaperItem({
    required this.id,
    required this.title,
    required this.imageUrl,
    required this.sourceUrl,
    this.sourceName,
    this.sourceKey,
    this.width,
    this.height,
    this.addedAt,
    this.localUri,
    this.isDownloaded = false,
    this.cropSettings,
  });

  Map<String, dynamic> toMap() {
    return {
      'remote_id': id,
      'title': title,
      'image_url': imageUrl,
      'source_url': sourceUrl,
      'source': sourceName,
      'source_key': sourceKey,
      'width': width,
      'height': height,
      'added_at': addedAt ?? DateTime.now().millisecondsSinceEpoch,
      'local_uri': localUri,
      'is_downloaded': isDownloaded ? 1 : 0,
      'updated_at': DateTime.now().millisecondsSinceEpoch,
    };
  }

  factory WallpaperItem.fromMap(Map<String, dynamic> map) {
    return WallpaperItem(
      id: map['remote_id'] ?? map['id']?.toString() ?? '',
      title: map['title'] ?? '',
      imageUrl: map['image_url'] ?? '',
      sourceUrl: map['source_url'] ?? '',
      sourceName: map['source'],
      sourceKey: map['source_key'],
      width: map['width'],
      height: map['height'],
      addedAt: map['added_at'],
      localUri: map['local_uri'],
      isDownloaded: (map['is_downloaded'] ?? 0) == 1,
    );
  }

  WallpaperItem copyWith({
    String? id,
    String? title,
    String? imageUrl,
    String? sourceUrl,
    String? sourceName,
    String? sourceKey,
    int? width,
    int? height,
    int? addedAt,
    String? localUri,
    bool? isDownloaded,
    WallpaperCropSettings? cropSettings,
  }) {
    return WallpaperItem(
      id: id ?? this.id,
      title: title ?? this.title,
      imageUrl: imageUrl ?? this.imageUrl,
      sourceUrl: sourceUrl ?? this.sourceUrl,
      sourceName: sourceName ?? this.sourceName,
      sourceKey: sourceKey ?? this.sourceKey,
      width: width ?? this.width,
      height: height ?? this.height,
      addedAt: addedAt ?? this.addedAt,
      localUri: localUri ?? this.localUri,
      isDownloaded: isDownloaded ?? this.isDownloaded,
      cropSettings: cropSettings ?? this.cropSettings,
    );
  }

  double? get aspectRatio {
    if (width != null && height != null && width! > 0 && height! > 0) {
      return width! / height!;
    }
    return null;
  }

  String? get libraryKey {
    final key = sourceKey;
    if (key == null) return null;
    final normalizedId = id.startsWith('$key:') ? id.substring(key.length + 1) : id;
    return '$key:$normalizedId';
  }

  String? get providerKey => sourceKey?.split(':').first;

  String get transitionKey => 'wallpaper-$id';

  Object get previewModel => (isDownloaded && localUri != null && localUri!.isNotEmpty) ? localUri! : imageUrl;
}
