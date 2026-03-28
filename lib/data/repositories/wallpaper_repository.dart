import '../api/reddit_service.dart';
import '../api/api_client.dart';
import '../../services/web_scraper.dart';
import '../models/source.dart';
import '../models/wallpaper_item.dart';

class WallpaperRepoPage {
  final List<WallpaperItem> wallpapers;
  final String? nextCursor;

  WallpaperRepoPage({required this.wallpapers, this.nextCursor});
}

class WallpaperRepository {
  final RedditService _redditService = RedditService();
  final ApiClient _apiClient = ApiClient();
  final WebScraper _webScraper = WebScraper();

  Future<WallpaperRepoPage> fetchWallpapersFor(
    Source source, {
    String? query,
    String? cursor,
  }) async {
    final provider = source.providerKey.toLowerCase();
    
    switch (provider) {
      case 'reddit':
        return await _fetchReddit(source, query, cursor);
      case 'wallhaven':
        final wp = await _apiClient.fetchWallhaven(
          params: {'q': source.config ?? query ?? 'wallpapers'},
          page: int.tryParse(cursor ?? '1') ?? 1,
        );
        return _mapToRepoPage(wp, source);
      case 'danbooru':
        final wp = await _apiClient.fetchDanbooru(
          tags: query ?? source.config,
          page: int.tryParse(cursor ?? '1') ?? 1,
        );
        return _mapToRepoPage(wp, source);
      case 'unsplash':
        final wp = await _apiClient.fetchUnsplash(
          query: query ?? source.config,
          page: int.tryParse(cursor ?? '1') ?? 1,
        );
        return _mapToRepoPage(wp, source);
      case 'pinterest':
      case 'websites':
      case 'alpha_coders':
        final scrapePage = await _webScraper.scrapeImagesFromUrl(
          source.config ?? query ?? '',
          cursor: cursor,
        );
        return WallpaperRepoPage(
          wallpapers: scrapePage.wallpapers.map((item) => item.copyWith(
            sourceName: source.title,
            sourceKey: source.key,
          )).toList(),
          nextCursor: scrapePage.nextCursor,
        );
      default:
        return WallpaperRepoPage(wallpapers: []);
    }
  }

  Future<WallpaperRepoPage> _fetchReddit(Source source, String? query, String? cursor) async {
    try {
      final response = query == null || query.isEmpty
          ? await _redditService.fetchSubreddit(
              subreddit: source.config ?? 'wallpapers',
              after: cursor,
            )
          : await _redditService.searchSubredditPosts(
              subreddit: source.config ?? 'wallpapers',
              query: query,
              after: cursor,
            );

      final items = response.data?.children?.map((child) {
        final post = child.data;
        if (post == null) return null;

        final imageUrl = post.overriddenUrl ?? post.url ?? '';
        if (imageUrl.isEmpty) return null;

        final preview = post.preview?.images?.firstOrNull?.source;

        return WallpaperItem(
          id: post.id,
          title: post.title,
          imageUrl: imageUrl.replaceAll('&amp;', '&'),
          sourceUrl: post.permalink != null 
              ? 'https://www.reddit.com${post.permalink}' 
              : imageUrl,
          width: preview?.width,
          height: preview?.height,
          sourceName: source.title,
          sourceKey: source.key,
        );
      }).whereType<WallpaperItem>().toList() ?? [];

      return WallpaperRepoPage(
        wallpapers: items,
        nextCursor: response.data?.after,
      );
    } catch (e) {
      return WallpaperRepoPage(wallpapers: []);
    }
  }

  WallpaperRepoPage _mapToRepoPage(WallpaperPagination wp, Source source) {
    return WallpaperRepoPage(
      wallpapers: wp.wallpapers.map((item) => item.copyWith(
        sourceName: source.title,
        sourceKey: source.key,
      )).toList(),
      nextCursor: wp.nextCursor,
    );
  }
}
