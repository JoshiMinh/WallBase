import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SettingsProvider extends ChangeNotifier {
  static const String _keyAppLock = 'app_lock_enabled';
  
  bool _appLockEnabled = false;
  
  bool get appLockEnabled => _appLockEnabled;

  SettingsProvider() {
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    _appLockEnabled = prefs.getBool(_keyAppLock) ?? false;
    notifyListeners();
  }

  Future<void> setAppLockEnabled(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyAppLock, value);
    _appLockEnabled = value;
    notifyListeners();
  }
}
