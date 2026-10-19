# CarTube Downloader

Native Kotlin Android 11 application for downloading publicly accessible YouTube media when you have permission to do so. The application runs locally on the head unit. It does not bypass authentication, DRM, paywalls, or access restrictions. Video playback is explicitly parked-only.

## Architecture

- Conventional Android Views with an `AndroidViewModel`, coroutines, and StateFlow. A single workspace has Video and Playlist modes, a persistent download status panel, and compact sheets for Queue, Library, and Settings.
- A `MediaExtractor` boundary isolates yt-dlp. `YtDlpExtractor` uses the Android Python wrapper (`youtubedl-android` 0.18.1), bundled yt-dlp 2026.08.19, `yt-dlp-ejs` 0.8.0, bundled QuickJS, and FFmpeg. Engine initialization installs EJS in the wrapper's Python 3.12 environment. No post-install tool download is required.
- Room persists queue and playlist entries. Initial manual playlist analysis discovers the first 50 items; later pages are fetched as the recycled list approaches its end. "Topilganlar" selects discovered items; it does not claim to select items that have not yet been fetched. A foreground service processes one download at a time, with typed failures, cancellation, bounded retry and interruption reconciliation. WorkManager schedules opt-in daily/weekly playlist checks; exact timing is not guaranteed.
- Playlist thumbnails load asynchronously from extractor metadata or a validated video-ID fallback, using bounded memory and private disk caches. Tapping a playlist thumbnail/title opens a nonmodal mini player over that thumbnail without changing any row's size or scroll position. The official YouTube IFrame player requires at least 200 × 200 CSS pixels, so the actual player overlay is larger than the thumbnail. Scrolling or tapping elsewhere closes it; long press or the 48 dp expand button opens a larger dialog. Fast scrolling at the list's right edge is disabled so it cannot intercept expand-button taps. A saved MP4 uses Media3. The WebView identifies the installed app through its Referer; embed errors are recorded with their code and show a specific explanation. Content owners can still disable embedding, in which case the item offers an external YouTube action. Visual previews require explicit parked confirmation, and no vehicle-state sensor is assumed.
- Media is downloaded into private temporary storage, checked with Android's MediaExtractor for playable audio/video tracks, and published through MediaStore (`Music/CarTube`, `Movies/CarTube`) or an explicitly selected Storage Access Framework folder. The offline library uses Media3.
- ABI-specific APKs contain native components for `armeabi-v7a` and `arm64-v8a`. The target head unit's actual ABI must be checked during installation.

## Build

JDK 17, Android SDK 36, Android Build Tools 35+, and an Internet connection for Gradle dependencies are needed on the development computer.

```sh
./gradlew test
./gradlew assembleDebug
./gradlew assembleRelease
```

Windows: `gradlew.bat test`, `gradlew.bat assembleDebug`, `gradlew.bat assembleRelease`.

To sign a release APK, copy `keystore.properties.example` to `keystore.properties` and fill in your own keystore details. Never commit the keystore or credentials. An unsigned release APK cannot be installed as-is; use the debug APK or sign the release output.

## Install on Android 11

1. In the head unit's About screen check whether the device supports ARMv7 (`armeabi-v7a`) or ARM64 (`arm64-v8a`). With a development computer you can run `adb shell getprop ro.product.cpu.abilist`.
2. Copy the matching `app-armeabi-v7a-debug.apk` or `app-arm64-v8a-debug.apk` to USB/SD or transfer it to the head unit.
3. Open the APK in the head unit's file manager. Allow installation from that file manager when Android prompts, then install.
4. Connect Wi-Fi, open CarTube Downloader, tap **Settings → Check engine**, then analyze an authorized public link. The health check only confirms bundled tools initialize; actual extraction requires a network test on the device.
5. Completed files appear in Music/CarTube or Movies/CarTube and the in-app library. Choose a folder in Settings for USB/SD output.

**Updating an earlier debug APK:** CI debug APKs can use a different signing certificate on each run. Android may reject an in-place update to 0.1.9. Finish any queued downloads, uninstall the old app, then install the new APK if Android reports a signature conflict. Uninstalling clears the app's queue, playlist records, and library database; completed media already published to Music/CarTube, Movies/CarTube, or a selected external folder remains in device storage but is not automatically re-imported into the in-app library. A stable user-owned release signing key is needed for seamless future upgrades; never commit it to this repository.

On a phone, the interface opens in portrait and follows Android's Auto-rotate setting when the phone turns landscape. The fixed landscape head unit continues to display landscape. In **Video**, paste a link with **Joylash** (which immediately analyzes), or type/share one and tap **Tahlil**; choose MP3 or MP4 and quality, select items, then tap **Yuklash (N)**. In **Pleylist**, tap **Tahlil**, scroll for more videos, check items (or **Topilganlar** for all discovered items), and tap **Yuklash (N)**. **Yangilari** selects only newly found items. Tap a thumbnail/title for a small floating preview without enlarging the row; hold or use ⤢ for a larger preview. The bottom status opens the queue sheet. The top-right icons open Queue, saved files, and Settings. Only download content you are authorized to save.

Audio bitrate is an encoding request, not a guarantee of source quality. Paused items safely restart if stream URLs are stale. The app needs enough free local storage for downloaded streams plus conversion; by default it reserves 1 GiB in addition to an estimated threefold working-space allowance. It does not promise background operation through vendor process kills, power loss, or system restrictions.

MP4 quality is a maximum resolution. The app prefers an already combined H.264/AAC MP4 at or below that value; when unavailable, yt-dlp attempts separate H.264 and AAC streams with FFmpeg merging. YouTube Mix links with an `RD` ID derived from a video are opened using that video's watch page. Auto-generated Mix availability is not guaranteed; normal public playlist URLs are more reliable.

If a device download still fails, use **Settings → Export log** and share the exported text together with the error shown in Queue. The diagnostic identifies extraction, network, or media validation errors without claiming a successful download from a partially downloaded file.

## Source and licenses

This repository is GPL-3.0-only because it bundles GPL components. See [LICENSE](LICENSE) and [third-party notices](docs/THIRD_PARTY.md). Supply corresponding source, this repository commit, and build instructions when distributing APKs. The release APK may include additional native FFmpeg codec libraries with their respective license obligations. Review those components and legal terms before distribution.

## Validation status

See [build report](docs/BUILD_REPORT.md) for actual commands, APK hashes/ABIs, and outstanding device tests. A successful compilation does **not** establish that current YouTube extraction works on the XY AUDIO device. It requires one metadata extraction and real media download on a compatible device with permitted test content.
