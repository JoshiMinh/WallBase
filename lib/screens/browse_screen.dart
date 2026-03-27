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
          return const Center(child: Text('No sources found.'));
        }

        return ListView.builder(
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
                trailing: Switch(
                  value: source.enabled,
                  onChanged: (value) => sourceProvider.toggleSource(source),
                ),
                onTap: source.enabled 
                  ? () {
                      context.read<BrowseProvider>().setSource(source);
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const SourceBrowseScreen(),
                        ),
                      );
                    }
                  : null,
              ),
            );
          },
        );
      },
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
