import 'package:dio/dio.dart';
import 'api_client.dart';
import 'reddit_models.dart';

class RedditService {
  final Dio _dio = ApiClient().dio;

  Future<RedditListingResponse> fetchSubreddit({
    required String subreddit,
    int limit = 30,
    String? after,
  }) async {
    final response = await _dio.get(
      'https://www.reddit.com/r/$subreddit.json',
      queryParameters: {
        'limit': limit,
        if (after != null) 'after': after,
      },
    );
    return RedditListingResponse.fromJson(response.data);
  }

  Future<RedditListingResponse> searchSubredditPosts({
    required String subreddit,
    required String query,
    int limit = 30,
    String? after,
  }) async {
    final response = await _dio.get(
      'https://www.reddit.com/r/$subreddit/search.json',
      queryParameters: {
        'q': query,
        'restrict_sr': 1,
        'limit': limit,
        if (after != null) 'after': after,
      },
    );
    return RedditListingResponse.fromJson(response.data);
  }
}
