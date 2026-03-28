import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../providers/library_provider.dart';
import '../data/models/wallpaper_item.dart';
import 'wallpaper_detail_screen.dart';

class LibraryScreen extends StatelessWidget {
  const LibraryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Column(
        children: [
          const TabBar(
            tabs: [
              Tab(text: 'Wallpapers'),
              Tab(text: 'Albums'),
            ],
          ),
          Expanded(
            child: TabBarView(
              children: [
                _buildWallpapersTab(context),
                _buildAlbumsTab(context),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildWallpapersTab(BuildContext context) {
    return Consumer<LibraryProvider>(
      builder: (context, libraryProvider, child) {
        if (libraryProvider.isLoading) {
          return const Center(child: CircularProgressIndicator());
        }

        final wallpapers = libraryProvider.libraryWallpapers;

        if (wallpapers.isEmpty) {
          return const Center(
            child: Text('No wallpapers in your library yet.'),
          );
        }

        return GridView.builder(
          padding: const EdgeInsets.all(4),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 3,
            childAspectRatio: 0.6,
            mainAxisSpacing: 4,
            crossAxisSpacing: 4,
          ),
          itemCount: wallpapers.length,
          itemBuilder: (context, index) {
            final wallpaper = wallpapers[index];
            return _buildWallpaperCard(context, wallpaper);
          },
        );
      },
    );
  }

  Widget _buildWallpaperCard(BuildContext context, WallpaperItem wallpaper) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => WallpaperDetailScreen(wallpaper: wallpaper),
            ),
          );
        },
        child: CachedNetworkImage(
          imageUrl: wallpaper.imageUrl,
          fit: BoxFit.cover,
          placeholder: (context, url) => Container(color: Colors.grey[300]),
          errorWidget: (context, url, error) => const Icon(Icons.error),
        ),
      ),
    );
  }

  Widget _buildAlbumsTab(BuildContext context) {
    return Consumer<LibraryProvider>(
      builder: (context, libraryProvider, child) {
        if (libraryProvider.isLoading) {
          return const Center(child: CircularProgressIndicator());
        }

        final albums = libraryProvider.albums;

        if (albums.isEmpty) {
          return const Center(
            child: Text('No albums created yet.'),
          );
        }

        return ListView.builder(
          itemCount: albums.length,
          itemBuilder: (context, index) {
            final album = albums[index];
            return ListTile(
              leading: const Icon(Icons.album),
              title: Text(album.title),
              subtitle: Text('${album.wallpaperCount} wallpapers'),
              onTap: () {
                // TODO: Navigate to AlbumDetailScreen
              },
            );
          },
        );
      },
    );
  }
}
