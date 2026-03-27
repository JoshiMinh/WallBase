import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:wallbase/screens/library_screen.dart';
import 'package:wallbase/screens/browse_screen.dart';
import 'package:wallbase/screens/settings_screen.dart';
import 'package:wallbase/theme_notifier.dart';
import 'package:wallbase/providers/source_provider.dart';
import 'package:wallbase/providers/browse_provider.dart';
import 'package:wallbase/providers/library_provider.dart';
import 'package:wallbase/providers/settings_provider.dart';
import 'package:wallbase/services/rotation_manager.dart';
import 'package:wallbase/services/app_lock_manager.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await RotationManager.initialize();
  
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => ThemeNotifier()),
        ChangeNotifierProvider(create: (_) => SourceProvider()),
        ChangeNotifierProvider(create: (_) => BrowseProvider()),
        ChangeNotifierProvider(create: (_) => LibraryProvider()),
        ChangeNotifierProvider(create: (_) => SettingsProvider()),
      ],
      child: const WallBaseApp(),
    ),
  );
}

class WallBaseApp extends StatelessWidget {
  const WallBaseApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: themeNotifier,
      builder: (context, _) {
        return MaterialApp(
          title: 'Wallbase',
          themeMode: themeNotifier.themeMode,
          theme: ThemeData(
            colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
            useMaterial3: true,
          ),
          darkTheme: ThemeData.dark(useMaterial3: true).copyWith(
            colorScheme: ColorScheme.fromSeed(
              seedColor: Colors.blue,
              brightness: Brightness.dark,
            ),
          ),
          home: const AuthWrapper(),
        );
      },
    );
  }
}

class AuthWrapper extends StatefulWidget {
  const AuthWrapper({super.key});

  @override
  State<AuthWrapper> createState() => _AuthWrapperState();
}

class _AuthWrapperState extends State<AuthWrapper> {
  bool _isAuthenticated = false;
  bool _isInit = true;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_isInit) {
      _checkAuth();
      _isInit = false;
    }
  }

  Future<void> _checkAuth() async {
    final settings = context.read<SettingsProvider>();
    if (settings.appLockEnabled) {
      final authManager = AppLockManager();
      final success = await authManager.authenticate();
      if (mounted) {
        setState(() {
          _isAuthenticated = success;
        });
        if (!success) {
          // If auth fails, we could show an error or exit
          // For now, let's just stay on a blank screen with a retry button
        }
      }
    } else {
      if (mounted) {
        setState(() {
          _isAuthenticated = true;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_isAuthenticated) {
      return Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.lock, size: 64, color: Colors.blue),
              const SizedBox(height: 24),
              const Text('WallBase is Locked', style: TextStyle(fontSize: 20)),
              const SizedBox(height: 24),
              ElevatedButton(
                onPressed: _checkAuth,
                child: const Text('Unlock'),
              ),
            ],
          ),
        ),
      );
    }
    return const MainScreen();
  }
}

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int _selectedIndex = 1; // Default to Browse

  static const List<Widget> _screens = [
    LibraryScreen(),
    BrowseScreen(),
    SettingsScreen(),
  ];

  static const List<String> _titles = [
    'Library',
    'Browse',
    'Settings',
  ];

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_titles[_selectedIndex]),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: _screens[_selectedIndex],
      bottomNavigationBar: BottomNavigationBar(
        items: const <BottomNavigationBarItem>[
          BottomNavigationBarItem(
            icon: Icon(Icons.library_books),
            label: 'Library',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.explore),
            label: 'Browse',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.settings),
            label: 'Settings',
          ),
        ],
        currentIndex: _selectedIndex,
        selectedItemColor: Colors.blue,
        onTap: _onItemTapped,
      ),
    );
  }
}
