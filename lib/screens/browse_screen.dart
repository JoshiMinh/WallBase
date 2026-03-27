import 'package:flutter/material.dart';

class BrowseScreen extends StatelessWidget {
  const BrowseScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.explore, size: 64, color: Colors.green),
          SizedBox(height: 16),
          Text(
            'Browse Screen',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
          ),
          SizedBox(height: 8),
          Text('Discover new wallpapers here.'),
        ],
      ),
    );
  }
}
