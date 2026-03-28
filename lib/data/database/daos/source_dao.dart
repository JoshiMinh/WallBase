import 'package:sqflite/sqflite.dart';
import '../../database/app_database.dart';
import '../../models/source.dart';

class SourceDao {
  Future<Database> get _db async => await AppDatabase().database;

  Future<List<Source>> getAllSources() async {
    final db = await _db;
    final List<Map<String, dynamic>> maps = await db.query('sources');
    return maps.map((map) => Source.fromMap(map)).toList();
  }

  Future<Source?> getSourceByKey(String key) async {
    final db = await _db;
    final List<Map<String, dynamic>> maps = await db.query(
      'sources',
      where: 'key = ?',
      whereArgs: [key],
    );
    if (maps.isEmpty) return null;
    return Source.fromMap(maps.first);
  }

  Future<int> insertSource(Source source) async {
    final db = await _db;
    return await db.insert('sources', source.toMap(), conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<int> deleteSource(int id) async {
    final db = await _db;
    return await db.delete('sources', where: 'source_id = ?', whereArgs: [id]);
  }

  Future<void> updateIcon(String key, String iconUrl) async {
    final db = await _db;
    await db.update(
      'sources',
      {'icon_url': iconUrl},
      where: 'key = ?',
      whereArgs: [key],
    );
  }
}
