import 'package:flutter/foundation.dart';
import '../data/models/source.dart';
import '../data/repositories/source_repository.dart';

class SourceProvider extends ChangeNotifier {
  final SourceRepository _repository = SourceRepository();
  
  List<Source> _sources = [];
  bool _isLoading = false;

  List<Source> get sources => _sources;
  List<Source> get enabledSources => _sources.where((s) => s.enabled).toList();
  bool get isLoading => _isLoading;

  SourceProvider() {
    loadSources();
  }

  Future<void> loadSources() async {
    _isLoading = true;
    notifyListeners();
    
    _sources = await _repository.getSources();
    
    _isLoading = false;
    notifyListeners();
  }

  Future<void> toggleSource(Source source) async {
    await _repository.toggleSource(source);
    await loadSources();
  }

  Future<void> addRedditSource(String subreddit) async {
    await _repository.addRedditSource(subreddit);
    await loadSources();
  }

  Future<void> removeSource(Source source) async {
    await _repository.removeSource(source);
    await loadSources();
  }
}
