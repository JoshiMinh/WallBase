import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:wallbase/theme_notifier.dart';
import '../providers/settings_provider.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Consumer<SettingsProvider>(
      builder: (context, settings, child) {
        return ListView(
          children: [
            _buildSectionHeader('Appearance'),
            _buildThemeSelector(),
            _buildAccentColorSelector(),
            const Divider(),
            
            _buildSectionHeader('Privacy & Security'),
            SwitchListTile(
              title: const Text('App Lock'),
              subtitle: const Text('Require biometrics to open WallBase'),
              secondary: const Icon(Icons.lock),
              value: settings.appLockEnabled,
              onChanged: (value) => settings.setAppLockEnabled(value),
            ),
            const Divider(),
            
            _buildSectionHeader('About'),
            const ListTile(
              leading: Icon(Icons.info_outline),
              title: Text('WallBase'),
              subtitle: Text('Version 1.2'),
            ),
          ],
        );
      },
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Text(
        title,
        style: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.bold,
          color: themeNotifier.accentColor,
        ),
      ),
    );
  }

  Widget _buildThemeSelector() {
    return ListenableBuilder(
      listenable: themeNotifier,
      builder: (context, _) {
        return ListTile(
          leading: const Icon(Icons.palette),
          title: const Text('App Theme'),
          subtitle: Text(_themeName(themeNotifier.theme)),
          onTap: () => _showThemePicker(context),
        );
      },
    );
  }

  String _themeName(AppTheme theme) {
    switch (theme) {
      case AppTheme.system: return 'System Default';
      case AppTheme.light: return 'Light';
      case AppTheme.dark: return 'Dark';
      case AppTheme.amoled: return 'AMOLED Black';
    }
  }

  void _showThemePicker(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Select Theme'),
        children: AppTheme.values.map((theme) {
          return SimpleDialogOption(
            onPressed: () {
              themeNotifier.setTheme(theme);
              Navigator.pop(context);
            },
            child: Row(
              children: [
                _themeIcon(theme),
                const SizedBox(width: 12),
                Text(_themeName(theme)),
              ],
            ),
          );
        }).toList(),
      ),
    );
  }

  Widget _themeIcon(AppTheme theme) {
    switch (theme) {
      case AppTheme.system: return const Icon(Icons.brightness_auto);
      case AppTheme.light: return const Icon(Icons.light_mode);
      case AppTheme.dark: return const Icon(Icons.dark_mode);
      case AppTheme.amoled: return const Icon(Icons.nightlight_round);
    }
  }

  Widget _buildAccentColorSelector() {
    final colors = [
      const Color(0xFFED1E69), // Pink
      Colors.blue,
      Colors.red,
      Colors.green,
      Colors.purple,
      Colors.yellow,
    ];

    return ListenableBuilder(
      listenable: themeNotifier,
      builder: (context, _) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: Text('Accent Color', style: TextStyle(fontSize: 16)),
            ),
            SizedBox(
              height: 60,
              child: ListView.builder(
                padding: const EdgeInsets.symmetric(horizontal: 12.0),
                scrollDirection: Axis.horizontal,
                itemCount: colors.length,
                itemBuilder: (context, index) {
                  final color = colors[index];
                  final isSelected = themeNotifier.accentColor == color;
                  return GestureDetector(
                    onTap: () => themeNotifier.setAccentColor(color),
                    child: Container(
                      width: 44,
                      height: 44,
                      margin: const EdgeInsets.symmetric(horizontal: 4.0),
                      decoration: BoxDecoration(
                        color: color,
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: isSelected ? Colors.white : Colors.transparent,
                          width: 2,
                        ),
                        boxShadow: [
                          if (isSelected)
                            BoxShadow(
                              color: color.withValues(alpha: 0.4),
                              blurRadius: 8,
                              spreadRadius: 2,
                            ),
                        ],
                      ),
                      child: isSelected
                          ? const Icon(Icons.check, color: Colors.white)
                          : null,
                    ),
                  );
                },
              ),
            ),
          ],
        );
      },
    );
  }

}
