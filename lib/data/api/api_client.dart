import 'package:dio/dio.dart';
import '../models/wallpaper_item.dart';

class WallpaperPagination {
  final List<WallpaperItem> wallpapers;
  final String? nextCursor;

  WallpaperPagination({required this.wallpapers, this.nextCursor});
}

class ApiClient {
  static final ApiClient _instance = ApiClient._internal();
  late final Dio dio;

  ApiClient._internal() {
    dio = Dio(
      BaseOptions(
        connectTimeout: const Duration(seconds: 15),
        receiveTimeout: const Duration(seconds: 15),
        headers: {
          'User-Agent': 'WallBase/1.0 (Flutter)',
        },
      ),
    );
  }

  factory ApiClient() => _instance;

  Future<WallpaperPagination> fetchWallhaven({Map<String, dynamic>? params, int page = 1}) async {
    try {
      final response = await dio.get(
        'https://wallhaven.cc/api/v1/search',
        queryParameters: {
          ...?params,
          'page': page,
        },
      );
      final data = response.data['data'] as List;
      final wallpapers = data.map((item) => WallpaperItem(
        id: 'wallhaven_${item['id']}',
        title: 'Wallhaven #${item['id']}',
        imageUrl: item['path'],
        sourceUrl: item['url'],
        width: item['dimension_x'],
        height: item['dimension_y'],
      )).toList();
      
      final meta = response.data['meta'];
      final nextCursor = (meta['current_page'] < meta['last_page']) ? (meta['current_page'] + 1).toString() : null;
      
      return WallpaperPagination(wallpapers: wallpapers, nextCursor: nextCursor);
    } catch (e) {
      return WallpaperPagination(wallpapers: []);
    }
  }

  Future<WallpaperPagination> fetchDanbooru({String? tags, int page = 1}) async {
    try {
      final response = await dio.get(
        'https://danbooru.donmai.us/posts.json',
        queryParameters: {
          'tags': tags ?? 'wallpaper rating:s',
          'page': page,
          'limit': 30,
        },
      );
      final data = response.data as List;
      final wallpapers = data.map((item) {
        final imageUrl = item['large_file_url'] ?? item['file_url'];
        if (imageUrl == null) return null;
        return WallpaperItem(
          id: 'danbooru_${item['id']}',
          title: item['tag_string_general']?.split(' ').take(3).join(' ') ?? 'Danbooru #${item['id']}',
          imageUrl: imageUrl,
          sourceUrl: 'https://danbooru.donmai.us/posts/${item['id']}',
          width: item['image_width'],
          height: item['image_height'],
        );
      }).whereType<WallpaperItem>().toList();

      final nextCursor = (data.length == 30) ? (page + 1).toString() : null;
      return WallpaperPagination(wallpapers: wallpapers, nextCursor: nextCursor);
    } catch (e) {
      return WallpaperPagination(wallpapers: []);
    }
  }

  Future<WallpaperPagination> fetchUnsplash({String? query, int page = 1}) async {
    try {
      final response = await dio.get(
        'https://unsplash.com/napi/search/photos',
        queryParameters: {
          'query': query ?? 'wallpapers',
          'page': page,
          'per_page': 30,
        },
      );
      final data = response.data['results'] as List;
      final wallpapers = data.map((item) => WallpaperItem(
        id: 'unsplash_${item['id']}',
        title: item['description'] ?? item['alt_description'] ?? 'Unsplash #${item['id']}',
        imageUrl: item['urls']['regular'],
        sourceUrl: item['links']['html'],
        width: item['width'],
        height: item['height'],
      )).toList();

      final totalPages = response.data['total_pages'] ?? 1;
      final nextCursor = (page < totalPages) ? (page + 1).toString() : null;
      return WallpaperPagination(wallpapers: wallpapers, nextCursor: nextCursor);
    } catch (e) {
      return WallpaperPagination(wallpapers: []);
    }
  }
}
