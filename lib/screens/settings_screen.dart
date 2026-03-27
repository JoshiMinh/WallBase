import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:wallbase/theme_notifier.dart';
import '../providers/settings_provider.dart';
import '../services/rotation_manager.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Consumer<SettingsProvider>(
      builder: (context, settings, child) {
        return ListView(
          children: [
            _buildSectionHeader('Appearance'),
            _buildDarkModeTile(),
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
            
            _buildSectionHeader('Wallpaper Rotation'),
            SwitchListTile(
              title: const Text('Periodic Rotation'),
              subtitle: const Text('Automatically change wallpaper'),
              secondary: const Icon(Icons.sync),
              value: settings.rotationEnabled,
              onChanged: (value) async {
                await settings.setRotationEnabled(value);
                if (value) {
                  await RotationManager.scheduleRotation(settings.rotationInterval);
                } else {
                  await RotationManager.cancelRotation();
                }
              },
            ),
            if (settings.rotationEnabled)
              ListTile(
                title: const Text('Rotation Interval'),
                subtitle: Text('${settings.rotationInterval} minutes'),
                leading: const Icon(Icons.timer),
                onTap: () => _showIntervalPicker(context, settings),
              ),
            const Divider(),
            
            _buildSectionHeader('About'),
            const ListTile(
              leading: Icon(Icons.info_outline),
              title: Text('WallBase (Flutter Migration)'),
              subtitle: Text('Version 1.0.0'),
            ),
          ],
        );
      },
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Text(
        title,
        style: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.bold,
          color: Colors.blue,
        ),
      ),
    );
  }

  Widget _buildDarkModeTile() {
    return ListenableBuilder(
      listenable: themeNotifier,
      builder: (context, _) {
        return SwitchListTile(
          title: const Text('Dark Mode'),
          subtitle: const Text('Enable dark theme'),
          secondary: const Icon(Icons.dark_mode),
          value: themeNotifier.isDarkMode,
          onChanged: (value) => themeNotifier.toggleTheme(value),
        );
      },
    );
  }

  void _showIntervalPicker(BuildContext context, SettingsProvider settings) {
    showDialog(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Select Interval'),
        children: [15, 30, 60, 120, 240, 480, 1440].map((mins) {
          return SimpleDialogOption(
            onPressed: () async {
              await settings.setRotationInterval(mins);
              await RotationManager.scheduleRotation(mins);
              if (context.mounted) Navigator.pop(context);
            },
            child: Text(mins < 60 ? '$mins minutes' : '${mins ~/ 60} hours'),
          );
        }).toList(),
      ),
    );
  }
}
