import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';

class AppDatabase {
  static final AppDatabase _instance = AppDatabase._internal();
  static Database? _database;

  AppDatabase._internal();

  factory AppDatabase() => _instance;

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase() async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, 'wallbase.db');

    return await openDatabase(
      path,
      version: 1,
      onCreate: _onCreate,
    );
  }

  Future<void> _onCreate(Database db, int version) async {
    // Albums table
    await db.execute('''
      CREATE TABLE albums (
        album_id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL UNIQUE,
        description TEXT,
        cover_wallpaper_id INTEGER,
        sort_order INTEGER NOT NULL DEFAULT 0,
        is_pinned INTEGER NOT NULL DEFAULT 0,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        sync_token TEXT
      )
    ''');

    // Wallpapers table
    await db.execute('''
      CREATE TABLE wallpapers (
        wallpaper_id INTEGER PRIMARY KEY AUTOINCREMENT,
        source_key TEXT NOT NULL,
        remote_id TEXT,
        source TEXT NOT NULL,
        title TEXT NOT NULL,
        description TEXT,
        image_url TEXT NOT NULL,
        source_url TEXT,
        local_uri TEXT UNIQUE,
        width INTEGER,
        height INTEGER,
        color_palette TEXT,
        crop_settings TEXT,
        edit_settings TEXT,
        file_size_bytes INTEGER,
        is_favorite INTEGER NOT NULL DEFAULT 0,
        is_downloaded INTEGER NOT NULL DEFAULT 0,
        applied_at INTEGER,
        added_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
    ''');

    // Album-Wallpaper junction table
    await db.execute('''
      CREATE TABLE album_wallpaper_cross_ref (
        album_id INTEGER NOT NULL,
        wallpaper_id INTEGER NOT NULL,
        PRIMARY KEY (album_id, wallpaper_id),
        FOREIGN KEY (album_id) REFERENCES albums (album_id) ON DELETE CASCADE,
        FOREIGN KEY (wallpaper_id) REFERENCES wallpapers (wallpaper_id) ON DELETE CASCADE
      )
    ''');

    // Sources table
    await db.execute('''
      CREATE TABLE sources (
        source_id INTEGER PRIMARY KEY AUTOINCREMENT,
        key TEXT NOT NULL UNIQUE,
        provider_key TEXT NOT NULL,
        title TEXT NOT NULL,
        description TEXT NOT NULL,
        icon_res INTEGER,
        icon_url TEXT,
        show_in_explore INTEGER NOT NULL DEFAULT 0,
        is_enabled INTEGER NOT NULL DEFAULT 0,
        is_local INTEGER NOT NULL DEFAULT 0,
        config TEXT
      )
    ''');

    // Rotation schedules table
    await db.execute('''
      CREATE TABLE rotation_schedules (
        schedule_id INTEGER PRIMARY KEY AUTOINCREMENT,
        album_id INTEGER NOT NULL UNIQUE,
        interval_minutes INTEGER NOT NULL,
        target TEXT NOT NULL,
        is_enabled INTEGER NOT NULL DEFAULT 0,
        last_applied_at INTEGER,
        last_wallpaper_id INTEGER,
        FOREIGN KEY (album_id) REFERENCES albums (album_id) ON DELETE CASCADE
      )
    ''');

    // Preload default sources
    await _preloadSources(db);
  }

  Future<void> _preloadSources(Database db) async {

    final defaultSources = [
      {
        'key': 'reddit:wallpapers',
        'provider_key': 'reddit',
        'title': 'r/wallpapers',
        'description': 'Top posts from r/wallpapers',
        'icon_url': 'https://www.google.com/s2/favicons?sz=128&domain=reddit.com',
        'show_in_explore': 1,
        'is_enabled': 1,
        'is_local': 0,
        'config': 'wallpapers',
      },
      {
        'key': 'pinterest:wallpaper_board',
        'provider_key': 'pinterest',
        'title': 'Pinterest Wallpapers',
        'description': 'Latest pins from our Pinterest board (limited support)',
        'icon_url': 'https://www.google.com/s2/favicons?sz=128&domain=pinterest.com',
        'show_in_explore': 1,
        'is_enabled': 1,
        'is_local': 0,
        'config': 'https://www.pinterest.com/wallpapercollec/wallpapers',
      },
    ];

    for (final source in defaultSources) {
      await db.insert('sources', source);
    }
  }
}
