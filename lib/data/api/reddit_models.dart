class RedditListingResponse {
  final RedditListingData? data;

  RedditListingResponse({this.data});

  factory RedditListingResponse.fromJson(Map<String, dynamic> json) {
    return RedditListingResponse(
      data: json['data'] != null ? RedditListingData.fromJson(json['data']) : null,
    );
  }
}

class RedditListingData {
  final List<RedditChild>? children;
  final String? after;

  RedditListingData({this.children, this.after});

  factory RedditListingData.fromJson(Map<String, dynamic> json) {
    return RedditListingData(
      children: (json['children'] as List?)
          ?.map((e) => RedditChild.fromJson(e))
          .toList(),
      after: json['after'],
    );
  }
}

class RedditChild {
  final RedditPost? data;

  RedditChild({this.data});

  factory RedditChild.fromJson(Map<String, dynamic> json) {
    return RedditChild(
      data: json['data'] != null ? RedditPost.fromJson(json['data']) : null,
    );
  }
}

class RedditPost {
  final String id;
  final String title;
  final String? permalink;
  final String? overriddenUrl;
  final String? url;
  final RedditPreview? preview;

  RedditPost({
    required this.id,
    required this.title,
    this.permalink,
    this.overriddenUrl,
    this.url,
    this.preview,
  });

  factory RedditPost.fromJson(Map<String, dynamic> json) {
    return RedditPost(
      id: json['id'] ?? '',
      title: json['title'] ?? '',
      permalink: json['permalink'],
      overriddenUrl: json['url_overridden_by_dest'],
      url: json['url'],
      preview: json['preview'] != null ? RedditPreview.fromJson(json['preview']) : null,
    );
  }
}

class RedditPreview {
  final List<RedditPreviewImage>? images;

  RedditPreview({this.images});

  factory RedditPreview.fromJson(Map<String, dynamic> json) {
    return RedditPreview(
      images: (json['images'] as List?)
          ?.map((e) => RedditPreviewImage.fromJson(e))
          .toList(),
    );
  }
}

class RedditPreviewImage {
  final RedditPreviewSource? source;

  RedditPreviewImage({this.source});

  factory RedditPreviewImage.fromJson(Map<String, dynamic> json) {
    return RedditPreviewImage(
      source: json['source'] != null ? RedditPreviewSource.fromJson(json['source']) : null,
    );
  }
}

class RedditPreviewSource {
  final String? url;
  final int? width;
  final int? height;

  RedditPreviewSource({this.url, this.width, this.height});

  factory RedditPreviewSource.fromJson(Map<String, dynamic> json) {
    return RedditPreviewSource(
      url: json['url'],
      width: json['width'],
      height: json['height'],
    );
  }
}
