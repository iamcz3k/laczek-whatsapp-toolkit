# LaCzek Audio Lab

A local-first Android audio utility.

## Included in the current build
- Microphone recording to AAC/M4A with runtime permission handling.
- Elapsed recording timer, local playback, and basic file/status checks.
- Export recordings to Music/LaCzek Audio Lab and share with other apps.
- Import audio using Android's document picker and share it to WhatsApp or another app.
- AMOLED dark interface with orange accents.
- GitHub Actions build, APK artifact, and automated GitHub Release.

## Call recording limitation
Android and WhatsApp may restrict capture of remote call audio. This app does not bypass Android security, accessibility restrictions, or another app's privacy controls. Depending on device, Android version, audio routing, and app version, call recording may capture only microphone audio or may be silent. Obtain consent from everyone involved before recording.

## Music sharing limitation
Imported audio is shared as an audio file. WhatsApp decides whether it appears as a voice note or a normal audio attachment; this app cannot force WhatsApp's private voice-note format.

## Build
Pushes to `main` trigger an APK build. A successful build is attached to a GitHub Release. Package: `com.laczek.whatsapptoolkit`.
