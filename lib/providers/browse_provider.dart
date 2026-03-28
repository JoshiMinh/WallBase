import 'package:flutter/foundation.dart';
import '../data/models/source.dart';
import '../data/models/wallpaper_item.dart';
import '../data/repositories/wallpaper_repository.dart';

class BrowseProvider extends ChangeNotifier {
  final WallpaperRepository _repository = WallpaperRepository();

  Source? _currentSource;
  List<WallpaperItem> _wallpapers = [];
  bool _isLoading = false;
  String? _nextCursor;
  String _query = '';

  Source? get currentSource => _currentSource;
  List<WallpaperItem> get wallpapers => _wallpapers;
  bool get isLoading => _isLoading;
  bool get hasMore => _nextCursor != null;

  void setSource(Source source) {
    if (_currentSource?.key == source.key) return;
    _currentSource = source;
    _query = '';
    _nextCursor = null;
    _wallpapers = [];
    fetchWallpapers();
  }

  void setQuery(String query) {
    if (_query == query) return;
    _query = query;
    _nextCursor = null;
    _wallpapers = [];
    fetchWallpapers();
  }

  Future<void> fetchWallpapers({bool loadMore = false}) async {
    if (_currentSource == null) return;
    if (_isLoading) return;
    if (loadMore && _nextCursor == null) return;

    _isLoading = true;
    notifyListeners();

    try {
      final page = await _repository.fetchWallpapersFor(
        _currentSource!,
        query: _query,
        cursor: loadMore ? _nextCursor : null,
      );

      if (loadMore) {
        _wallpapers.addAll(page.wallpapers);
      } else {
        _wallpapers = page.wallpapers;
      }
      
      _nextCursor = page.nextCursor;
    } catch (e) {
      // Handle error
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
}
