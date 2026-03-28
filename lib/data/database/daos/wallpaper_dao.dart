import 'package:sqflite/sqflite.dart';
import '../../database/app_database.dart';
import '../../models/wallpaper_item.dart';

class WallpaperDao {
  Future<Database> get _db async => await AppDatabase().database;

  Future<List<WallpaperItem>> getAllWallpapers() async {
    final db = await _db;
    final List<Map<String, dynamic>> maps = await db.query('wallpapers');
    return maps.map((map) => WallpaperItem.fromMap(map)).toList();
  }

  Future<WallpaperItem?> getWallpaperByRemoteId(String remoteId) async {
    final db = await _db;
    final List<Map<String, dynamic>> maps = await db.query(
      'wallpapers',
      where: 'remote_id = ?',
      whereArgs: [remoteId],
    );
    if (maps.isEmpty) return null;
    return WallpaperItem.fromMap(maps.first);
  }

  Future<WallpaperItem?> getWallpaperByImageUrl(String imageUrl) async {
    final db = await _db;
    final List<Map<String, dynamic>> maps = await db.query(
      'wallpapers',
      where: 'image_url = ?',
      whereArgs: [imageUrl],
    );
    if (maps.isEmpty) return null;
    return WallpaperItem.fromMap(maps.first);
  }

  Future<int> insertWallpaper(WallpaperItem wallpaper) async {
    final db = await _db;
    return await db.insert('wallpapers', wallpaper.toMap(), conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<int> deleteWallpaperByImageUrl(String imageUrl) async {
    final db = await _db;
    return await db.delete('wallpapers', where: 'image_url = ?', whereArgs: [imageUrl]);
  }

  Future<void> setFavorite(String imageUrl, bool isFavorite) async {
    final db = await _db;
    await db.update(
      'wallpapers',
      {'is_favorite': isFavorite ? 1 : 0},
      where: 'image_url = ?',
      whereArgs: [imageUrl],
    );
  }
}
