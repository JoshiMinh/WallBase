@echo off

REM Launch the Android emulator
flutter emulators --launch Medium_Phone_API_36.0

REM Run the Flutter app on the launched emulator
flutter run -d emulator-5554
