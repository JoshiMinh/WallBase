import 'package:flutter/foundation.dart';
import '../data/database/app_database.dart';
import '../data/database/daos/wallpaper_dao.dart';
import '../data/models/wallpaper_item.dart';
import '../data/models/album.dart';

class LibraryProvider extends ChangeNotifier {
  final WallpaperDao _wallpaperDao = WallpaperDao();
  List<WallpaperItem> _libraryWallpapers = [];
  List<Album> _albums = [];
  bool _isLoading = false;

  List<WallpaperItem> get libraryWallpapers => _libraryWallpapers;
  List<Album> get albums => _albums;
  bool get isLoading => _isLoading;

  LibraryProvider() {
    loadLibrary();
  }

  Future<void> loadLibrary() async {
    _isLoading = true;
    notifyListeners();

    _libraryWallpapers = await _wallpaperDao.getAllWallpapers();

    final db = await AppDatabase().database;
    final List<Map<String, dynamic>> albumMaps = await db.query('albums');
    _albums = albumMaps.map((map) => Album.fromMap(map)).toList();

    _isLoading = false;
    notifyListeners();
  }

  Future<void> addToLibrary(WallpaperItem item) async {
    await _wallpaperDao.insertWallpaper(item);
    await loadLibrary();
  }

  Future<void> removeFromLibrary(String imageUrl) async {
    await _wallpaperDao.deleteWallpaperByImageUrl(imageUrl);
    await loadLibrary();
  }

  Future<bool> isWallpaperInLibrary(String imageUrl) async {
    final wallpaper = await _wallpaperDao.getWallpaperByImageUrl(imageUrl);
    return wallpaper != null;
  }
}
