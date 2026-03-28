import '../database/daos/source_dao.dart';
import '../models/source.dart';

class SourceRepository {
  final SourceDao _sourceDao = SourceDao();

  Future<List<Source>> getSources() async {
    return await _sourceDao.getAllSources();
  }

  Future<Source?> getSource(String key) async {
    return await _sourceDao.getSourceByKey(key);
  }

  Future<Source> addRedditSource(String subreddit) async {
    final normalized = subreddit.trim().toLowerCase();
    final key = 'reddit:$normalized';
    
    final existing = await _sourceDao.getSourceByKey(key);
    if (existing != null) return existing;

    final source = Source(
      id: 0,
      key: key,
      providerKey: 'reddit',
      title: 'r/$normalized',
      description: 'Reddit subreddit: $normalized',
      iconUrl: 'https://www.google.com/s2/favicons?sz=128&domain=reddit.com',
      showInExplore: true,
      enabled: true,
      isLocal: false,
      config: normalized,
    );

    final id = await _sourceDao.insertSource(source);
    return source.copyWith(id: id);
  }

  Future<void> removeSource(Source source) async {
    if (source.id != 0) {
      await _sourceDao.deleteSource(source.id);
    }
  }

  Future<void> updateSourceIcon(String key, String iconUrl) async {
    await _sourceDao.updateIcon(key, iconUrl);
  }
}
