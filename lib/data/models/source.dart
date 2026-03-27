class Source {
  final int id;
  final int? iconRes;
  final String? iconUrl;
  final String title;
  final String description;
  final bool showInExplore;
  final bool enabled;
  final String key;
  final String providerKey;
  final bool isLocal;
  final String? config;

  Source({
    required this.id,
    this.iconRes,
    this.iconUrl,
    required this.title,
    required this.description,
    required this.showInExplore,
    required this.enabled,
    required this.key,
    required this.providerKey,
    required this.isLocal,
    this.config,
  });

  Map<String, dynamic> toMap() {
    return {
      'source_id': id == 0 ? null : id,
      'icon_res': iconRes,
      'icon_url': iconUrl,
      'title': title,
      'description': description,
      'show_in_explore': showInExplore ? 1 : 0,
      'is_enabled': enabled ? 1 : 0,
      'key': key,
      'provider_key': providerKey,
      'is_local': isLocal ? 1 : 0,
      'config': config,
    };
  }

  factory Source.fromMap(Map<String, dynamic> map) {
    return Source(
      id: map['source_id'] ?? 0,
      iconRes: map['icon_res'],
      iconUrl: map['icon_url'],
      title: map['title'] ?? '',
      description: map['description'] ?? '',
      showInExplore: (map['show_in_explore'] ?? 0) == 1,
      enabled: (map['is_enabled'] ?? 0) == 1,
      key: map['key'] ?? '',
      providerKey: map['provider_key'] ?? '',
      isLocal: (map['is_local'] ?? 0) == 1,
      config: map['config'],
    );
  }

  Source copyWith({
    int? id,
    int? iconRes,
    String? iconUrl,
    String? title,
    String? description,
    bool? showInExplore,
    bool? enabled,
    String? key,
    String? providerKey,
    bool? isLocal,
    String? config,
  }) {
    return Source(
      id: id ?? this.id,
      iconRes: iconRes ?? this.iconRes,
      iconUrl: iconUrl ?? this.iconUrl,
      title: title ?? this.title,
      description: description ?? this.description,
      showInExplore: showInExplore ?? this.showInExplore,
      enabled: enabled ?? this.enabled,
      key: key ?? this.key,
      providerKey: providerKey ?? this.providerKey,
      isLocal: isLocal ?? this.isLocal,
      config: config ?? this.config,
    );
  }
}
