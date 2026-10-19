# CarTube Downloader — build and validation report

## Version 0.1.2 — media output and system-bar fix

- Source commit [`e60ca0b`](https://github.com/shokiriy/car-tube-downloader/commit/e60ca0bbb3c51b4d6a88a4e8c988d6b3b0e986b6) accepts only yt-dlp's final `VIDEO_ID.mp4` or `VIDEO_ID.mp3`, instead of an arbitrary intermediate stream. Android MediaExtractor verifies container, H.264 video/AAC audio or MP3 audio, and readable samples before publication. The downloader screen now applies system status/navigation bar insets in both rotations. `versionCode=3` / `versionName=0.1.2`.
- [GitHub Actions run 39218951196](https://github.com/shokiriy/car-tube-downloader/actions/runs/39218951196) passed `./gradlew test assembleDebug --stacktrace` and uploaded signed ARM64/ARMv7 debug APKs. Debug and release JVM reports each show 10 tests, 0 failures. The new output selection tests reject a video-only intermediate file.
- A user screenshot from a phone shows real metadata and a thumbnail for their test link, plus a failed MP4 task at 46.4% with `The media file is not playable.` This establishes device metadata extraction, **not** a completed download. We could not obtain the device diagnostic export to distinguish FFprobe startup failure from a genuinely incomplete MP4. The code addresses both plausible paths; a device retry and diagnostic export are still required.

| 0.1.2 signed debug APK | Bytes | ABI | SHA-256 |
| --- | ---: | --- | --- |
| `CarTube-Downloader-v0.1.2-arm64-v8a-debug.apk` | 77,694,348 | `arm64-v8a` | `122f2594c244dfd04fdff89c8c3a3712e7d4e407a941a39b9bb472c66742c7bf` |
| `CarTube-Downloader-v0.1.2-armeabi-v7a-debug.apk` | 71,023,158 | `armeabi-v7a` | `5580fc3a15d01cbdb897549db34f1c179d9a9b1b580110bb58ec9f3dcf350cd5` |

Both APKs contain their named native ABI only, including `libpython.so`, `libpython.zip.so`, `libqjs.so`, `libffmpeg.so`, `libffmpeg.zip.so`, and `libffprobe.so`. The physical device ABI remains unknown; install the matching variant. UI safe area, MP4 completion, MediaStore publishing, and playback are **BUILD-VERIFIED ONLY** for 0.1.2; no instrumented or physical device test was available in CI. Playlist selection remains implemented but not tested on the target device, and further device work follows the video fix.

Local `./gradlew test assembleDebug` could not download Gradle because the development sandbox blocked `services.gradle.org`. The same command succeeded in GitHub Actions. Debug signing keys vary between CI environments; an installed older debug APK may require uninstall before the 0.1.2 APK can be installed, which clears app queue and playlist records. Completed shared media remains in storage.

## Version 0.1.1 — portrait phone interface

- Commit [`cdb6dade`](https://github.com/shokiriy/car-tube-downloader/commit/cdb6dade6896d01bdaaa2991ac5c15ed7946cebd) removes forced landscape from the downloader and player. Android follows device rotation; portrait stacks actions and puts navigation at the bottom. The Paste action analyzes the clipboard URL, then the screen guides format selection and queueing. `versionCode` is 2 so the APK updates 0.1.0.
- [GitHub Actions run](https://github.com/shokiriy/car-tube-downloader/actions/runs/39157227607) passed `test assembleDebug` and uploaded both signed debug APKs. The debug unit test report has 8 tests and 0 failures; instrumentation and physical-device UI/download tests were not run.
- APK integrity and the single packaged ABI in each APK were checked after downloading the CI artifact.

| 0.1.1 signed debug APK | Bytes | ABI | SHA-256 |
| --- | ---: | --- | --- |
| `app-arm64-v8a-debug.apk` | 77,691,208 | `arm64-v8a` | `c41e123dd9ba1f089f542bdf06d93442d02e891c2f9cfa431e645b27400ff378` |
| `app-armeabi-v7a-debug.apk` | 71,020,018 | `armeabi-v7a` | `bc5cd3a3fa47d95d8947a89f377c5efc65229a13548e4df9709c8625762604da` |

Orientation and the paste flow are **BUILD-VERIFIED ONLY** until checked on an actual phone. Real MP3/MP4 downloads on the head unit are still **NOT TESTED**.

The old local debug APK's signing certificate SHA-256 is `18033da2e0dfca495df55e5f059fb4c2a0c0b5522c94ac20efcaea529bed724e`; the 0.1.1 CI APK's is `92497bb459b65c09883234d9c8fa3411200ef11338586b9212437403154f82e4`. An installed 0.1.0 debug APK signed with the former certificate must be uninstalled before installing this 0.1.1 APK. This removes the in-app queue and library records, though completed files in shared MediaStore or a selected external folder remain. Future upgrades require a stable private release signing key.

## Environment and versions

- Source branch: [`main`](https://github.com/shokiriy/car-tube-downloader/tree/main), pushed to `shokiriy/car-tube-downloader`
- JDK 17.0.20; Gradle 8.13; Android Gradle Plugin 8.13.2; Kotlin 2.3.20
- Android compile SDK 36, minimum SDK 30, target SDK 35
- yt-dlp Android wrapper 0.18.1 (Python 3.12, QuickJS 2025-04-26); FFmpeg/FFprobe wrapper payload 0.18.1
- yt-dlp 2026.08.19; yt-dlp-ejs 0.8.0, matching the yt-dlp version's default dependency declaration
- One native ABI per APK: `armeabi-v7a` or `arm64-v8a`

## Commands

`./gradlew test assembleDebug assembleRelease assembleDebugAndroidTest` — BUILD SUCCESSFUL. A final `./gradlew test assembleDebug assembleRelease` after the media validator change was also BUILD SUCCESSFUL. `./gradlew test` executed debug and release unit test variants. Android instrumentation test APK was compiled, but there is no attached device or emulator to execute it.

## Acceptance evidence

| Feature | Status | Evidence / remaining check |
| --- | --- | --- |
| URL parsing, normalization, format selection, progress mapping, filename sanitization, ABI and storage budget | VERIFIED | JVM unit tests pass (debug and release). |
| Main UI, five sections, batch selection, queue, playlist paging, settings, library/player | BUILD-VERIFIED ONLY | Kotlin/Android build completes; screen behavior requires device test. |
| Persistent Room queue, foreground service, recovery, WorkManager, MediaStore/SAF | BUILD-VERIFIED ONLY | Android compilation and instrumented test APK build; tests require device. |
| Bundled Python, QuickJS, FFmpeg/FFprobe, EJS, yt-dlp | BUILD-VERIFIED ONLY | Native ELF/archives and EJS/yt-dlp assets are in each ABI APK; Android launch not tested. |
| Public authorized YouTube metadata on development host | VERIFIED | yt-dlp returned real Big Buck Bunny title, ID, and 30 formats; this is not an Android runtime test. |
| MP3 conversion and MP4 download on head unit | NOT TESTED | No device connected; host media request returned an HTML “Site Unavailable” page (195 bytes), which FFmpeg rejected. No valid media bytes were obtained. |
| Physical Android 11 / XY AUDIO B7.3 installation and playback | NOT TESTED | `adb devices` returned no devices. Device ABI is unknown. |
| GitHub Actions | BUILD-VERIFIED ONLY | [CI run](https://github.com/shokiriy/car-tube-downloader/actions/runs/39104475436) passed `test assembleDebug` and uploaded two debug APKs with test reports. Physical device execution remains untested. |

Version 0.1.2 validates completed media with Android MediaExtractor and will not publish an empty/invalid MP3 or MP4 as a successful download. The build and source integration alone do not establish live YouTube download functionality. Native package license provenance and specific FFmpeg build options require review before public redistribution.

## Device acceptance steps

1. Check `ro.product.cpu.abilist` (if ADB is available) or device About information and install the matching debug APK.
2. Open Settings → Check engine. Confirm Python/yt-dlp initialization; a successful health check does not prove YouTube access.
3. Analyze `https://www.youtube.com/watch?v=YE7VzlLtp-4` (Big Buck Bunny, Blender Foundation, CC BY 3.0), then select MP3 192 kbps. Verify a real `audio/mpeg` file with playable audio.
4. Download 360p or 480p MP4 if offered. Verify H.264 video and audio, and playback only while safely parked.
5. Test a public playlist, cancellation, retry, process restart, Wi-Fi-only behavior, external SAF folder, and low storage.
6. If extraction fails, export diagnostics from Settings; inspect the category and runtime output without treating a metadata-only result as a successful download.

## Previous 0.1.0 APK files

The signed debug APKs and unsigned release APKs are in `app/build/outputs/apk/`. The unsigned release outputs require a user keystore before installation. `apksigner verify` succeeds for debug and rejects the unsigned release as expected. Each APK contains only its named ARM ABI and bundles `libpython.so`, `libpython.zip.so`, `libqjs.so`, `libffmpeg.so`, `libffmpeg.zip.so`, `libffprobe.so`, yt-dlp and the EJS wheel.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk` | 71,414,978 | `7ee1776a43f610fdb3b8094e44851093ff608ae5087ca9f330ea06ba0bc384c3` |
| `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` | 77,851,944 | `3fc4cee2c62dd9f29a26f6784eba1dc726c93fd1ce71eefb377c2ed08216c719` |
| `app/build/outputs/apk/release/app-armeabi-v7a-release-unsigned.apk` | 66,621,905 | `9b53bb5dc00cad6b9deb779cb076b6ddcc583250281529b73e151242f966fb64` |
| `app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk` | 73,275,003 | `81c7a8e82403913c19ccf1b057fd6604c9025f302019bb941c4e0cd53e86b530` |

JVM tests: 8 passed, 0 failed in `testDebugUnitTest`; 8 passed, 0 failed in `testReleaseUnitTest`. Three Android instrumentation tests compile, but have not run. Host yt-dlp fetched metadata for the CC BY 3.0 test video, but the media URL returned a 195-byte HTML “Site Unavailable” response in this restricted development network. This is an extraction/download **blocker for runtime verification**, not an APK build failure.
