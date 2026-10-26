# LaCzek Audio Lab

A local-first Android audio utility for recording, previewing, trimming and sharing audio.

## Features
- Microphone recording to AAC/M4A with runtime permission handling and elapsed timer.
- Local playback of the latest recording.
- Export recordings to the Music/LaCzek Audio Lab folder and share them with other apps.
- Import audio using Android's document picker.
- Preview imported audio with adjustable playback volume.
- Trim AAC/M4A audio by start/end seconds into a new M4A clip, without re-encoding.
- Share imported audio or the resulting clip through Android's share sheet.
- AMOLED dark interface with orange accents.
- GitHub Actions build, APK artifact, and automated GitHub Release.

## Important limitations
- **WhatsApp call audio:** Android and WhatsApp may restrict capture of remote call audio. This app records from the microphone and does not bypass Android security or another app's privacy controls. Depending on the device, Android version, audio routing and app version, recordings may contain only microphone audio or may be silent. Test before relying on a recording and obtain consent from everyone involved.
- **Voice notes:** Imported audio is shared as an audio file. WhatsApp controls whether it appears as a normal audio attachment or voice note; the app cannot force WhatsApp's private voice-note format.
- **Trimming:** The fast trim operation currently supports AAC audio in M4A/MP4 containers. Other formats are not silently converted; the app reports that they are unsupported.
- **Device testing:** CI verifies compilation and APK packaging. Real microphone, playback, document-provider and WhatsApp behavior still needs testing on a physical Android device.

## Download and build
See [GitHub Releases](https://github.com/iamcz3k/laczek-whatsapp-toolkit/releases) for the latest APK. Pushes to `main` trigger a build and release.

Package: `com.laczek.whatsapptoolkit`
