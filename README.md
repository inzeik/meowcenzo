# MEOWCENZO

```
 /\_/\
( o.o )   an offline, local audio player
 > ^ <    terminal look. zero rounded corners.
```

Meowcenzo plays the music that is already on your phone. No account, no cloud, no tracking.
The app does not request the INTERNET permission.

Built with Kotlin, Jetpack Compose and AndroidX Media3.

## Features

**Library**
- Reads your music through MediaStore, rescans automatically when files are added or removed
- Tabs: Songs, Albums, Artists, Playlists, Favourites
- Live search, sort (title / artist / newest / longest), recently played row
- Playlists, favourites and play counts stored on the device
- Track info screen with full metadata and technical details (format, bitrate, sample rate, bit depth, size, path)
- Edit title, artist, album and track number (saved inside Meowcenzo; the audio file's own tags are not modified)

**Playback**
- Background playback with notification, lock-screen and Bluetooth/headset controls
- Shuffle, repeat (off / all / one), speed 0.75x to 2x, sleep timer
- Queue view, play next, add to queue, queue restored after the app restarts
- Tuned buffering so seeking in hi-res files (24-bit FLAC, ALAC) resumes quickly
- Equalizer with bands, presets and bass boost

**Interface**
- Monochrome terminal design: monospace type, sharp rectangles, 1dp borders
- Now Playing with 3D tilt artwork, segmented visualizer (decorative) and a joined transport bar
- Settings and About screens

## Install

Download the latest `.apk` from the [Releases](../../releases) page and open it on your phone.
Android will ask you to allow installing apps from your browser or file manager.

Requires Android 8.0 (API 26) or newer.

## Build from source

1. Install Android Studio and open this folder.
2. Let Gradle sync (JDK 17).
3. Run on a device or emulator that has music files.
4. Tap "Allow access" on first launch.

Debug APK: `Build > Build Bundle(s) / APK(s) > Build APK(s)`, output in `app/build/outputs/apk/debug/`.

## Screenshots

<!-- Put images in docs/screenshots/ and link them here, for example:
![Home](docs/screenshots/home.png)
-->

## Roadmap

- Folders tab and Most Played screen (the data is already collected)
- Drag-to-reorder queue
- Synced lyrics
- Real audio-reactive visualizer
- Crossfade and volume normalization
- Home-screen widget
- Writing tags into the audio files

## License

MIT, see [LICENSE](LICENSE).