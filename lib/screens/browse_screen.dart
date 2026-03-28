import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../providers/source_provider.dart';
import '../providers/browse_provider.dart';
import '../data/models/source.dart';
import 'source_browse_screen.dart';

class BrowseScreen extends StatelessWidget {
  const BrowseScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Consumer<SourceProvider>(
      builder: (context, sourceProvider, child) {
        if (sourceProvider.isLoading) {
          return const Center(child: CircularProgressIndicator());
        }

        final sources = sourceProvider.sources;

        if (sources.isEmpty) {
          return Scaffold(
            body: const Center(child: Text('No sources found.')),
            floatingActionButton: _buildAddSourceFab(context, sourceProvider),
          );
        }

        return Scaffold(
          appBar: AppBar(
            title: const Text('Browse'),
            backgroundColor: Colors.transparent,
            elevation: 0,
            centerTitle: false,
            actions: [
              IconButton(
                icon: const Icon(Icons.info_outline),
                onPressed: () => _showSupportedSourcesDialog(context),
              ),
            ],
          ),
          body: ListView.builder(
            padding: const EdgeInsets.all(8),
            itemCount: sources.length,
            itemBuilder: (context, index) {
              final source = sources[index];
              return Card(
                margin: const EdgeInsets.symmetric(vertical: 4),
                child: ListTile(
                  leading: _buildSourceIcon(source),
                  title: Text(source.title),
                  subtitle: Text(source.description),
                  onTap: () => _showSourceSettingsDialog(context, sourceProvider, source),
                ),
              );
            },
          ),
          floatingActionButton: _buildAddSourceFab(context, sourceProvider),
        );
      },
    );
  }

  void _showSourceSettingsDialog(BuildContext context, SourceProvider provider, Source source) {
    showDialog(
      context: context,
      builder: (context) => SimpleDialog(
        title: Text(source.title),
        children: [
          SimpleDialogOption(
            onPressed: () {
              Navigator.pop(context);
              context.read<BrowseProvider>().setSource(source);
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => const SourceBrowseScreen(),
                ),
              );
            },
            child: const Row(
              children: [
                Icon(Icons.explore),
                SizedBox(width: 12),
                Text('Browse Wallpapers'),
              ],
            ),
          ),
          SimpleDialogOption(
            onPressed: () {
              Navigator.pop(context);
              provider.refetchFavicon(source);
            },
            child: const Row(
              children: [
                Icon(Icons.refresh),
                SizedBox(width: 12),
                Text('Refetch Favicon'),
              ],
            ),
          ),
          SimpleDialogOption(
            onPressed: () {
              Navigator.pop(context);
              _confirmRemoveSource(context, provider, source);
            },
            child: const Row(
              children: [
                Icon(Icons.delete, color: Colors.red),
                SizedBox(width: 12),
                Text('Remove Source', style: TextStyle(color: Colors.red)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _confirmRemoveSource(BuildContext context, SourceProvider provider, Source source) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Remove Source'),
        content: Text('Are you sure you want to remove ${source.title}?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              provider.removeSource(source);
              Navigator.pop(context);
            },
            style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
            child: const Text('Remove', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  void _showSupportedSourcesDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Supported Sources'),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('WallBase currently supports:'),
            SizedBox(height: 12),
            Text('• Wallhaven (Explore)'),
            Text('• Danbooru (Explore)'),
            Text('• Unsplash (Explore)'),
            Text('• Reddit (Custom Subreddits)'),
            SizedBox(height: 12),
            Text('Pinterest support coming soon!'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  Widget _buildAddSourceFab(BuildContext context, SourceProvider provider) {
    return FloatingActionButton(
      onPressed: () => _showAddSourceDialog(context, provider),
      child: const Icon(Icons.add),
    );
  }

  void _showAddSourceDialog(BuildContext context, SourceProvider provider) {
    final controller = TextEditingController();
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Add Reddit Source'),
        content: TextField(
          controller: controller,
          decoration: const InputDecoration(
            hintText: 'e.g., wallpapers, art',
            labelText: 'Subreddit Name',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              if (controller.text.isNotEmpty) {
                provider.addRedditSource(controller.text);
                Navigator.pop(context);
              }
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );
  }

  Widget _buildSourceIcon(Source source) {
    if (source.iconUrl != null && source.iconUrl!.isNotEmpty) {
      return CachedNetworkImage(
        imageUrl: source.iconUrl!,
        width: 32,
        height: 32,
        placeholder: (context, url) => const Icon(Icons.public, size: 32),
        errorWidget: (context, url, error) => const Icon(Icons.public, size: 32),
      );
    }
    return const Icon(Icons.public, size: 32);
  }
}
