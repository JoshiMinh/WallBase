class Album {
  final int id;
  final String title;
  final String? description;
  final int? coverWallpaperId;
  final int sortOrder;
  final bool isPinned;
  final int createdAt;
  final int updatedAt;
  final String? syncToken;
  final int wallpaperCount;

  Album({
    required this.id,
    required this.title,
    this.description,
    this.coverWallpaperId,
    this.sortOrder = 0,
    this.isPinned = false,
    required this.createdAt,
    required this.updatedAt,
    this.syncToken,
    this.wallpaperCount = 0,
  });

  Map<String, dynamic> toMap() {
    return {
      'album_id': id == 0 ? null : id,
      'title': title,
      'description': description,
      'cover_wallpaper_id': coverWallpaperId,
      'sort_order': sortOrder,
      'is_pinned': isPinned ? 1 : 0,
      'created_at': createdAt,
      'updated_at': updatedAt,
      'sync_token': syncToken,
    };
  }

  factory Album.fromMap(Map<String, dynamic> map, {int wallpaperCount = 0}) {
    return Album(
      id: map['album_id'] ?? 0,
      title: map['title'] ?? '',
      description: map['description'],
      coverWallpaperId: map['cover_wallpaper_id'],
      sortOrder: map['sort_order'] ?? 0,
      isPinned: (map['is_pinned'] ?? 0) == 1,
      createdAt: map['created_at'] ?? DateTime.now().millisecondsSinceEpoch,
      updatedAt: map['updated_at'] ?? DateTime.now().millisecondsSinceEpoch,
      syncToken: map['sync_token'],
      wallpaperCount: wallpaperCount,
    );
  }

  Album copyWith({
    int? id,
    String? title,
    String? description,
    int? coverWallpaperId,
    int? sortOrder,
    bool? isPinned,
    int? createdAt,
    int? updatedAt,
    String? syncToken,
    int? wallpaperCount,
  }) {
    return Album(
      id: id ?? this.id,
      title: title ?? this.title,
      description: description ?? this.description,
      coverWallpaperId: coverWallpaperId ?? this.coverWallpaperId,
      sortOrder: sortOrder ?? this.sortOrder,
      isPinned: isPinned ?? this.isPinned,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      syncToken: syncToken ?? this.syncToken,
      wallpaperCount: wallpaperCount ?? this.wallpaperCount,
    );
  }
}
