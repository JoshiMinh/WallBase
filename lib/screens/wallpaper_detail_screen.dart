import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:path_provider/path_provider.dart';
import 'package:dio/dio.dart';
import 'package:share_plus/share_plus.dart';
import '../data/models/wallpaper_item.dart';
import '../providers/library_provider.dart';

class WallpaperDetailScreen extends StatefulWidget {
  final WallpaperItem wallpaper;

  const WallpaperDetailScreen({super.key, required this.wallpaper});

  @override
  State<WallpaperDetailScreen> createState() => _WallpaperDetailScreenState();
}

class _WallpaperDetailScreenState extends State<WallpaperDetailScreen> {
  bool _isApplying = false;
  bool _isInLibrary = false;

  @override
  void initState() {
    super.initState();
    _checkLibraryStatus();
  }

  Future<void> _checkLibraryStatus() async {
    final inLibrary = await context.read<LibraryProvider>().isWallpaperInLibrary(widget.wallpaper.imageUrl);
    if (mounted) {
      setState(() {
        _isInLibrary = inLibrary;
      });
    }
  }

  Future<void> _setWallpaper(int location) async {
    setState(() => _isApplying = true);
    try {
      final dio = Dio();
      final tempDir = await getTemporaryDirectory();
      final tempPath = '${tempDir.path}/temp_wallpaper.jpg';
      
      await dio.download(widget.wallpaper.imageUrl, tempPath);
      
      final result = await AsyncWallpaper.setWallpaperFromFile(
        filePath: tempPath,
        wallpaperLocation: location,
      );
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(result ? 'Wallpaper set successfully' : 'Failed to set wallpaper')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _isApplying = false);
    }
  }

  void _showSetWallpaperDialog() {
    showModalBottomSheet(
      context: context,
      builder: (context) => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            leading: const Icon(Icons.home),
            title: const Text('Home Screen'),
            onTap: () {
              Navigator.pop(context);
              _setWallpaper(AsyncWallpaper.HOME_SCREEN);
            },
          ),
          ListTile(
            leading: const Icon(Icons.lock),
            title: const Text('Lock Screen'),
            onTap: () {
              Navigator.pop(context);
              _setWallpaper(AsyncWallpaper.LOCK_SCREEN);
            },
          ),
          ListTile(
            leading: const Icon(Icons.important_devices),
            title: const Text('Both'),
            onTap: () {
              Navigator.pop(context);
              _setWallpaper(AsyncWallpaper.BOTH_SCREENS);
            },
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.share, color: Colors.white),
            onPressed: () {
              Share.share(widget.wallpaper.sourceUrl.isNotEmpty 
                  ? widget.wallpaper.sourceUrl 
                  : widget.wallpaper.imageUrl);
            },
          ),
        ],
      ),
      body: Stack(
        fit: StackFit.expand,
        children: [
          CachedNetworkImage(
            imageUrl: widget.wallpaper.imageUrl,
            fit: BoxFit.cover,
            placeholder: (context, url) => const Center(child: CircularProgressIndicator()),
            errorWidget: (context, url, error) => const Icon(Icons.error),
          ),
          _buildOverlay(),
        ],
      ),
    );
  }

  Widget _buildOverlay() {
    return Positioned(
      bottom: 0,
      left: 0,
      right: 0,
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Colors.transparent, Colors.black.withValues(alpha: 0.8)],
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              widget.wallpaper.title,
              style: const TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              widget.wallpaper.sourceName ?? 'Unknown Source',
              style: TextStyle(color: Colors.white.withValues(alpha: 0.7), fontSize: 16),
            ),
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _isApplying ? null : _showSetWallpaperDialog,
                    icon: _isApplying 
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.wallpaper),
                    label: const Text('Set Wallpaper'),
                  ),
                ),
                const SizedBox(width: 12),
                IconButton(
                  onPressed: () async {
                    if (_isInLibrary) {
                      await context.read<LibraryProvider>().removeFromLibrary(widget.wallpaper.imageUrl);
                    } else {
                      await context.read<LibraryProvider>().addToLibrary(widget.wallpaper);
                    }
                    _checkLibraryStatus();
                  },
                  icon: Icon(
                    _isInLibrary ? Icons.bookmark : Icons.bookmark_border,
                    color: Colors.white,
                  ),
                  style: IconButton.styleFrom(
                    backgroundColor: Colors.white.withValues(alpha: 0.2),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
