import 'package:workmanager/workmanager.dart';
import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:path_provider/path_provider.dart';
import 'package:dio/dio.dart';
import '../data/database/app_database.dart';
import '../data/models/wallpaper_item.dart';

@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    try {
      final db = await AppDatabase().database;
      
      final List<Map<String, dynamic>> maps = await db.query(
        'wallpapers',
        orderBy: 'RANDOM()',
        limit: 1,
      );

      if (maps.isEmpty) return true;

      final wallpaper = WallpaperItem.fromMap(maps[0]);
      
      final dio = Dio();
      final tempDir = await getTemporaryDirectory();
      final tempPath = '${tempDir.path}/rotation_wallpaper.jpg';
      
      await dio.download(wallpaper.imageUrl, tempPath);
      
      await AsyncWallpaper.setWallpaperFromFile(
        filePath: tempPath,
        wallpaperLocation: AsyncWallpaper.HOME_SCREEN,
      );
      await AsyncWallpaper.setWallpaperFromFile(
        filePath: tempPath,
        wallpaperLocation: AsyncWallpaper.LOCK_SCREEN,
      );
      
      return true;
    } catch (e) {
      return false;
    }
  });
}

class RotationManager {
  static const String taskName = 'com.joshiminh.wallbase.rotation_task';

  static Future<void> initialize() async {
    await Workmanager().initialize(callbackDispatcher);
  }

  static Future<void> scheduleRotation(int intervalMinutes) async {
    await Workmanager().registerPeriodicTask(
      '1',
      taskName,
      frequency: Duration(minutes: intervalMinutes),
      existingWorkPolicy: ExistingPeriodicWorkPolicy.update,
      constraints: Constraints(
        networkType: NetworkType.connected,
      ),
    );
  }

  static Future<void> cancelRotation() async {
    await Workmanager().cancelByUniqueName('1');
  }
}
