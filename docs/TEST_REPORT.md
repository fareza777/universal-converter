# Test report — Universal Converter 1.0.0

Date: 31 October 2026
Build: `com.universalconverter.pro` versionCode 1, versionName 1.0.0
Toolchain: JDK 21, Gradle 8.14.3, AGP 8.13.2, Kotlin 2.3.21, Compose BOM 2026.01.01, compile/target SDK 36
Devices used for manual QA: Android 16 emulator (API 36, 1080×2340, Google APIs image) and Redmi Note 11 (Android 13, 1080×2400)

## Monetisation and visual update — 31 October 2026

The final build adds Google AdMob, UMP consent, Google Play Billing, a new icon/splash, onboarding and a full theme system. Verified on the Redmi Note 11 with the debug and release signing keys:

- Native AndroidX splash plus the branded in-app splash render; the earlier blank-start window is gone.
- Onboarding pages 1–3 render, Skip/Continue/Start work, and theme changes apply live.
- System/Light/Dark, all four accents (Ember, Sage, Gold, Cobalt) and OLED pure-black mode persist across launches.
- Settings workflow, notification shortcut, storage clearing confirmations and replay-tour action render correctly with the enlarged type scale.
- Studio camera framing was fixed with a sphere-based `fitDistance`; torus and cube stay fully framed on 20:9. The new JVM test covers five aspect ratios.
- Queue action buttons fit; 3D results use a full-width “Open in 3D Studio” action.
- AdMob test banner appears above the app bottom navigation and never covers Android's system buttons. No-fill banner slots no longer reserve blank space.
- Rewarded test ad completed and granted the 24-hour ad-free pass; banner/interstitial were disabled for the pass.
- A three-file batch (TXT→PDF 327 KB, PDF→PNG pages ZIP 125 KB, TXT→PDF 326 KB) completed with header verification. The frequency-capped interstitial showed after the third conversion.
- Billing UI correctly reports that `remove_ads` is unavailable on a sideloaded build. A real purchase/restore test requires the Play Console product, a license tester and the licensing public key.
- UMP consent initialisation runs before MobileAds; the EEA/UK form requires a configured UMP message in the AdMob console.
- Production AdMob and Billing identifiers are **not** in this build; `ads.properties` contains Google test IDs and an empty licensing key. See the README monetisation section.

Final-device screenshots are kept in the local QA workspace and are intentionally not published with the public repository.

## Release artifacts verified

| Artifact | Size | Verification |
| --- | --- | --- |
| `release/UniversalConverterPro-1.0.0.apk` | 10.8 MiB (11.4 MB) | Installed and launched on the Redmi Note 11; APK Signature Scheme v2 + v3 |
| `release/UniversalConverterPro-1.0.0.aab` | 16.3 MiB (17.1 MB) | Bundletool validation exit 0; JAR signature verified |
| `release/mapping-release.txt` | 74.8 MiB (78.4 MB) | R8 mapping for Play deobfuscation |

- `apksigner verify --print-certs`: v2 true, v3 true, 4096-bit RSA, certificate valid until 2054.
- `aapt2 dump permissions`: `INTERNET` and `ACCESS_NETWORK_STATE` (optional ads/billing only), `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, plus Google advertising-ID, Privacy Sandbox ad-services and Play Billing permissions merged by the SDKs. Conversion itself uses no network.
- `bundletool dump manifest`: minSdk 26, targetSdk 36, service type `dataSync` (0x1), FileProvider authority `com.universalconverter.pro.files`, `allowBackup=false`, `usesCleartextTraffic=false`.

## Automated JVM tests

`./gradlew :app:testDebugUnitTest` → **21 tests, 0 failures** (adds StudioFramingTest: sphere fit across five aspect ratios and portrait/landscape distance ordering).

Coverage includes:
- OBJ/STL (ASCII+binary)/PLY/OFF/GLB/glTF round-trips, bounds, normals, watertight/mesh-health analysis, low-poly renderer output, turntable GIF header.
- Format detection for mesh/text/PDF/PNG-in-disguise/Office ZIP.
- GIF89a LZW, animated frame count and palette decoding via an independent test decoder; dithered path.
- TIFF LZW/Deflate/PackBits/alpha/gray/palette/embedded-JPEG reads; TIFF writer round-trip; BMP writer layout.
- DOCX/XLSX round-trips, Markdown/HTML and CSV quoting, JSON table conversion, Word `List Bullet` style parsing.

## On-device release checks

### Built-in diagnostics (release APK)

`Settings → Device self-check` → **6 passed, 0 failed** (final release APK on Redmi Note 11, Android 13):

| Check | Result |
| --- | --- |
| Content detection | Pass — OBJ identified from content |
| 3D mesh round-trip | Pass — 12 triangles, watertight, 1 shell |
| Document table round-trip | Pass — 2×2 cells preserved |
| Image PNG → JPEG | Pass — header verified |
| Archive extract | Pass — hello.txt extracted |
| WAV → M4A | Pass — 0.4 s tone encoded and verified |

Device screenshots are kept in the local QA workspace.

### Mixed batch on Android 16 emulator

An 11-file batch imported from a folder with smart targets produced **10 successful conversions and 1 failure**:

| Source | Target | Result | Evidence |
| --- | --- | --- | --- |
| bundle.zip | extract folder | 2 files extracted, 63 ms | queue |
| cube.stl | GLB | 1.2 KB, 16 ms, header verified | queue |
| drawing.svg | PNG | 65.1 KB, 393 ms, header verified | scaled render 2048×1280 |
| photo.pdf | PNG pages ZIP | 1.1 MB, 2.2 s, ZIP verified; page 2200×1467 | page pulled and decoded |
| photo.tiff | JPEG | 39.7 KB, 735 ms, header verified | — |
| report.docx | PDF | 38.9 KB, 967 ms, header verified | rendered; table + bullet present |
| sample_short.mp4 | MP4 | 354 KB, 7.1 s, H.264 640×360, 4.0 s, plays | ffprobe + frame decode |
| table.csv | XLSX | 1.7 KB, 24 ms, header verified | — |
| tone.aiff | M4A | 28.2 KB, 683 ms, header verified (after fix) | — |
| tone.mp3 | M4A | 28.2 KB, 1.7 s, header verified | — |

The only failure was `tone.aiff`: “This AIFF has no readable PCM sound data.” Root cause was the 80-bit AIFF sample-rate mantissa being parsed as a signed `Long`, making 44.1 kHz negative. The parser now builds the mantissa as an unsigned `Double`. Retested successfully with the same sample (28.2 KB M4A, 683 ms, verified).

### Additional verified scenarios

- **Video with AAC audio** (`sample_av.mp4`): compressed output 139 KB, ffprobe reports H.264 video + AAC audio, full decode exits 0.
- **WebM/Opus → MP4**: 153 KB; Opus audio decoded and re-encoded to AAC; H.264 video + AAC audio; full decode exits 0.
- **Video → GIF**: 480×270, 12 frames, 80 ms delay; valid GIF89a. Rotation-metadata input produced 480×853 portrait GIF, so orientation handling was exercised.
- **Contact sheet**: 1360×738 JPEG with 12 labelled stills, dark studio styling.
- **PDF → TXT on the release APK**: 178 bytes extracted from a text PDF in 1.9 s using PDFBox after R8.
- **SVG scaling**: `drawing.svg` rasterized to a full 2048×1280 cream canvas (earlier corner-only scaling bug fixed and retested).
- **DXF → SVG**: 1.8 KB vector output preserving a red layer line, black walls, circle, arc and text; SVG source inspected.
- **3D Studio**: OpenGL ES 3.0 renderer ran on the emulator; torus demo framed with camera fit; cube GLB shown with flat-shaded faces; score 100 / print-ready for the demo. Turntable GIF export produced 32 frames/448 KB before the final supersampling/bitrate optimization; the final encoder path is covered by JVM GIF tests, but the optimized 28-frame export was not re-exported on-device.
- **Dark theme**: verified on the home screen.
- **Notification permission** prompt shown once; conversion still runs if denied.

### Defects found and fixed during QA

1. GLB reader read the GLB header from offset 0 instead of skipping the magic (no JSON chunk).
2. GIF LZW widened codes one entry too early; fixed and validated by an independent decoder.
3. TIFF LZW used the wrong dictionary-entry length; fixed, validated against Pillow/imagecodecs-produced TIFFs.
4. OBJ diagnostics fixture encoded two triangles as one hexagonal face (test fixture, not engine), fixed.
5. Selection from Library did not navigate to Convert due to a stale captured route; fixed by checking the live NavController destination.
6. SVG/PDF rendering ignored the target rectangle; replaced with a manually scaled picture.
7. Studio portrait camera fit cropped tall objects; camera distance now accounts for horizontal FOV and model extents.
8. Video bitrates were not scaled down with resolution; now scaled against 1080p, reducing a 4 s 360p clip from 2.6 MB to 354 KB.
9. DOCX `List Bullet` style was not recognized; bullet detection now uses both numbering and list styles. DOCX core title is read when present, avoiding redundant filename mastheads.
10. AIFF 80-bit sample-rate sign bug described above.
11. X-ray action row overflowed on narrow screens; actions are horizontally scrollable.

## Not covered by this QA run

The following remain to be validated before/within a production rollout:

- Incoming Android share-sheet intents and cross-app drag-and-drop (implemented; not exercised in the emulator QA).
- File-manager rename/delete/preview dialogs, folder browsing after extraction, and Save copy to Downloads on a real Google-account device.
- DNG, HEIC and AVIF decoding on physical devices with vendor codecs.
- 7Z extraction, password-protected PDFs/archives.
- Very large files, 4K/60 fps video, hour-long audio, low-RAM/API 26–35 devices.
- Google Play internal-track installation of the AAB (the signed APK was installed directly).
- Real Redmi Note 11 smoke test (MIUI blocked adb installs because “Install via USB” was disabled during the session).

The emulator run used API 36 only. A Pixel/Redmi device sweep and a Play internal test track install are recommended before production.

## Artifacts

- Unit test result XML/logs: `qa/logs/` (where retained)
- Device QA screenshots/evidence are kept locally; they are excluded from the public repository.
- Raw and framed Play screenshots: `store/raw/`, `store/screenshots/`
- Release bundles and mapping: `release/`
- Source license texts: `docs/licenses/`
