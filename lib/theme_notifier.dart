import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

enum AppTheme { system, light, dark, amoled }

class ThemeNotifier extends ChangeNotifier {
  static const String _keyTheme = 'theme_mode';
  static const String _keyAccent = 'accent_color';

  AppTheme _theme = AppTheme.system;
  Color _accentColor = const Color(0xFFED1E69); // Pink #ed1e69

  AppTheme get theme => _theme;
  Color get accentColor => _accentColor;

  ThemeNotifier() {
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    
    // Load theme
    final themeIndex = prefs.getInt(_keyTheme);
    if (themeIndex != null) {
      _theme = AppTheme.values[themeIndex];
    }

    // Load accent color
    final accentValue = prefs.getInt(_keyAccent);
    if (accentValue != null) {
      _accentColor = Color(accentValue);
    }
    
    notifyListeners();
  }

  Future<void> setTheme(AppTheme theme) async {
    _theme = theme;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_keyTheme, theme.index);
    notifyListeners();
  }

  Future<void> setAccentColor(Color color) async {
    _accentColor = color;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_keyAccent, color.toARGB32());
    notifyListeners();
  }

  ThemeMode get themeMode {
    switch (_theme) {
      case AppTheme.system:
        return ThemeMode.system;
      case AppTheme.light:
        return ThemeMode.light;
      case AppTheme.dark:
      case AppTheme.amoled:
        return ThemeMode.dark;
    }
  }

  ThemeData getTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;
    final useAmoled = _theme == AppTheme.amoled && isDark;

    return ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: _accentColor,
        brightness: brightness,
        surface: useAmoled ? Colors.black : null,
      ),
      scaffoldBackgroundColor: useAmoled ? Colors.black : null,
      appBarTheme: AppBarTheme(
        backgroundColor: useAmoled ? Colors.black : null,
        elevation: 0,
      ),
    );
  }
}

final themeNotifier = ThemeNotifier();
