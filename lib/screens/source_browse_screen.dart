import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../providers/browse_provider.dart';
import '../data/models/wallpaper_item.dart';
import 'wallpaper_detail_screen.dart';

class SourceBrowseScreen extends StatefulWidget {
  const SourceBrowseScreen({super.key});

  @override
  State<SourceBrowseScreen> createState() => _SourceBrowseScreenState();
}

class _SourceBrowseScreenState extends State<SourceBrowseScreen> {
  final ScrollController _scrollController = ScrollController();
  final TextEditingController _searchController = TextEditingController();
  bool _isSearching = false;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >= _scrollController.position.maxScrollExtent - 200) {
      context.read<BrowseProvider>().fetchWallpapers(loadMore: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<BrowseProvider>(
      builder: (context, browseProvider, child) {
        final source = browseProvider.currentSource;
        if (source == null) return const Scaffold(body: Center(child: Text('No source selected')));

        return Scaffold(
          appBar: AppBar(
            title: _isSearching
                ? TextField(
                    controller: _searchController,
                    autofocus: true,
                    decoration: const InputDecoration(
                      hintText: 'Search...',
                      border: InputBorder.none,
                    ),
                    onSubmitted: (value) {
                      browseProvider.setQuery(value);
                    },
                  )
                : Text(source.title),
            actions: [
              IconButton(
                icon: Icon(_isSearching ? Icons.close : Icons.search),
                onPressed: () {
                  setState(() {
                    if (_isSearching) {
                      _isSearching = false;
                      _searchController.clear();
                      browseProvider.setQuery('');
                    } else {
                      _isSearching = true;
                    }
                  });
                },
              ),
            ],
          ),
          body: _buildBody(browseProvider),
        );
      },
    );
  }

  Widget _buildBody(BrowseProvider provider) {
    if (provider.isLoading && provider.wallpapers.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }

    if (provider.wallpapers.isEmpty) {
      return const Center(child: Text('No wallpapers found.'));
    }

    return GridView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.all(4),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        childAspectRatio: 0.6,
        mainAxisSpacing: 4,
        crossAxisSpacing: 4,
      ),
      itemCount: provider.wallpapers.length + (provider.hasMore ? 1 : 0),
      itemBuilder: (context, index) {
        if (index == provider.wallpapers.length) {
          return const Center(child: CircularProgressIndicator());
        }

        final wallpaper = provider.wallpapers[index];
        return _buildWallpaperCard(wallpaper);
      },
    );
  }

  Widget _buildWallpaperCard(WallpaperItem wallpaper) {
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
        child: Stack(
          fit: StackFit.expand,
          children: [
            CachedNetworkImage(
              imageUrl: wallpaper.imageUrl,
              fit: BoxFit.cover,
              placeholder: (context, url) => Container(color: Colors.grey[300]),
              errorWidget: (context, url, error) => const Icon(Icons.error),
            ),
            Positioned(
              bottom: 0,
              left: 0,
              right: 0,
              child: Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [Colors.transparent, Colors.black.withValues(alpha: 0.7)],
                  ),
                ),
                child: Text(
                  wallpaper.title,
                  style: const TextStyle(color: Colors.white, fontSize: 12),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
