import 'package:local_auth/local_auth.dart';
import 'package:flutter/services.dart';

class AppLockManager {
  final LocalAuthentication _auth = LocalAuthentication();

  Future<bool> isBiometricAvailable() async {
    try {
      final bool canAuthenticateWithBiometrics = await _auth.canCheckBiometrics;
      final bool canAuthenticate = canAuthenticateWithBiometrics || await _auth.isDeviceSupported();
      return canAuthenticate;
    } on PlatformException {
      return false;
    }
  }

  Future<bool> authenticate() async {
    try {
      // Basic authentication call to avoid complex options check for now
      return await _auth.authenticate(
        localizedReason: 'Please authenticate to open WallBase',
      );
    } on PlatformException {
      return false;
    }
  }
}
