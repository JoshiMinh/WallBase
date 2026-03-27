import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SettingsProvider extends ChangeNotifier {
  static const String _keyAppLock = 'app_lock_enabled';
  static const String _keyRotationEnabled = 'rotation_enabled';
  static const String _keyRotationInterval = 'rotation_interval_minutes';
  
  bool _appLockEnabled = false;
  bool _rotationEnabled = false;
  int _rotationInterval = 60; // default 1 hour
  
  bool get appLockEnabled => _appLockEnabled;
  bool get rotationEnabled => _rotationEnabled;
  int get rotationInterval => _rotationInterval;

  SettingsProvider() {
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    _appLockEnabled = prefs.getBool(_keyAppLock) ?? false;
    _rotationEnabled = prefs.getBool(_keyRotationEnabled) ?? false;
    _rotationInterval = prefs.getInt(_keyRotationInterval) ?? 60;
    notifyListeners();
  }

  Future<void> setAppLockEnabled(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyAppLock, value);
    _appLockEnabled = value;
    notifyListeners();
  }

  Future<void> setRotationEnabled(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyRotationEnabled, value);
    _rotationEnabled = value;
    notifyListeners();
    
    if (value) {
      // TODO: Schedule Workmanager task
    } else {
      // TODO: Cancel Workmanager task
    }
  }

  Future<void> setRotationInterval(int minutes) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_keyRotationInterval, minutes);
    _rotationInterval = minutes;
    notifyListeners();
    
    if (_rotationEnabled) {
      // TODO: Reschedule Workmanager task
    }
  }
}
