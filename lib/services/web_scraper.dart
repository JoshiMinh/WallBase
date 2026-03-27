import 'package:dio/dio.dart';
import 'package:html/parser.dart' as parser;
import '../data/api/api_client.dart';
import '../data/models/wallpaper_item.dart';

class WebScraper {
  final Dio _dio = ApiClient().dio;

  Future<WallpaperPagination> scrapeImagesFromUrl(String url, {String? cursor}) async {
    try {
      final response = await _dio.get(url);
      final document = parser.parse(response.data);
      
      final images = document.querySelectorAll('img');
      final wallpapers = <WallpaperItem>[];
      
      for (var img in images) {
        final src = img.attributes['src'] ?? img.attributes['data-src'];
        if (src == null || !src.startsWith('http')) continue;
        
        wallpapers.add(WallpaperItem(
          id: src.hashCode.toString(),
          title: img.attributes['alt'] ?? 'Scraped Image',
          imageUrl: src,
          sourceUrl: url,
        ));
      }

      int start = int.tryParse(cursor ?? '0') ?? 0;
      int end = (start + 30).clamp(0, wallpapers.length);
      final paged = wallpapers.skip(start).take(30).toList();
      
      return WallpaperPagination(
        wallpapers: paged,
        nextCursor: end < wallpapers.length ? end.toString() : null,
      );
    } catch (e) {
      return WallpaperPagination(wallpapers: []);
    }
  }

  Future<WallpaperPagination> scrapePinterest(String query, {String? cursor}) async {
    final url = 'https://www.pinterest.com/search/pins/?q=${Uri.encodeComponent(query)}';
    return await scrapeImagesFromUrl(url, cursor: cursor);
  }
}
