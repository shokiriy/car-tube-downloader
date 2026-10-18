# Third-party components

| Component | Pinned version | Purpose | License / provenance |
| --- | --- | --- | --- |
| youtubedl-android (library + ffmpeg) | 0.18.1 | Android Python, QuickJS, FFmpeg packaging | GPL-3.0 upstream, https://github.com/yausername/youtubedl-android |
| yt-dlp | 2026.08.19 | extraction | Unlicense, https://github.com/yt-dlp/yt-dlp |
| yt-dlp-ejs | 0.8.0 | JavaScript challenge components | Unlicense AND MIT AND ISC (wheel metadata), https://github.com/yt-dlp/ejs |
| FFmpeg and transitive native codecs | wrapper 0.18.1 payload | encoding, merge and media validation | FFmpeg LGPL/GPL depending on compiled configuration; inspect native build provenance before release |
| AndroidX, Kotlin, Media3 | versions in `app/build.gradle.kts` | Android app | Apache-2.0 and library-specific notices |

The yt-dlp script and EJS wheel are bundled in `res/raw` and `assets/engine` respectively. Corresponding source is upstream at the version/tag above. Binary FFmpeg native payload originates from the wrapper release. Do not ship a signed public release until its exact configured codec licenses and corresponding source offer have been reviewed.
