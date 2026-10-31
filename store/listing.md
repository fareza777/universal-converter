# Google Play listing — Universal Converter

## Core fields

| Field | Value |
| --- | --- |
| App name on device | Universal Converter |
| Play title (max 30) | Universal Converter: 3D PDF |
| Package | `com.universalconverter.pro` |
| Version | 1.0.0 (versionCode 1) |
| Category | Tools |
| Tags | file converter, 3D, CAD, image converter, PDF, audio, video, offline, batch |
| Ads | Yes — Google AdMob banner, interstitial and opt-in rewarded |
| In-app purchases | Yes — one-time `remove_ads` at US$4.99 |
| Account required | No |
| Internet required | No for conversion; used only by optional ads and Google Play billing |
| Support email | Fill with the developer support email on the Play Console account |
| Privacy policy URL | Host `privacy-policy.html`, then paste the HTTPS URL |

## Short description (max 80)

`Offline 3D, CAD, RAW, PDF, docs, audio & video converter. Batch + preview.`

## Full description

Universal Converter is a quiet, local file studio for Android. Convert 3D models, CAD drawings, images and RAW photos, PDFs, documents, spreadsheets, audio, video and archives without uploading a single file.

No account, no subscription, and no file uploads. The conversion engine runs on your phone; optional ads keep the studio free, and one small purchase removes them forever.

BUILT FOR REAL FORMATS

• 3D: OBJ, STL, PLY, OFF, GLB and glTF, with an interactive OpenGL studio, mesh health check and turntable GIF export.
• CAD & vector: DXF drawings to SVG/PDF/PNG/JPG; SVG and SVGZ to raster or PDF.
• Images & RAW: JPEG, PNG, WebP, BMP, GIF, TIFF and DNG camera previews; HEIC/AVIF where the device supports them. Resize, compress, strip metadata or preserve capture details.
• PDF: render every page to a PNG/JPEG page set, extract selectable text, or create a clean PDF from images and documents.
• Documents: DOCX, XLSX, CSV/TSV, JSON, XML, HTML, Markdown and TXT conversions with tables and headings kept intact.
• Audio: MP3, AAC/M4A, FLAC, OGG/Opus, WAV and AIFF converted to AAC M4A or WAV.
• Video: MP4, MOV, MKV, WebM and 3GP to compressed H.264/AAC MP4, looping GIF, contact sheet, M4A or WAV.
• Archives: unpack ZIP, TAR, TAR.GZ, GZ, BZ2, XZ and 7Z, or repack to ZIP/TAR/TAR.GZ. Path-traversal protection is built in.

MORE THAN A FORMAT DROPDOWN

• Smart format detection reads the content, not just the file name, and warns about mismatched extensions.
• Batch queue accepts a mixed folder of files, each keeping its own best target. Progress is visible and cancellable.
• Quality recipes: Archive, Balanced, Web and Tiny, with real controls for image edge/quality, audio bitrate, video size, GIF frame rate, PDF page and compression.
• Conversion X-ray: engine, duration, checksum, verification result, pipeline and warnings for every job.
• Private vault with search, filters, previews, rename, delete and share.
• 3D Studio: orbit, pinch, clay/color/wire, grid, print-readiness prescription and an offline turntable GIF.
• Drag and drop from other apps, Android share sheet in and out, and Save copy to Downloads on Android 10+.

YOUR THEME, YOUR WORKFLOW

• System, Light and Dark themes with four accent colours, including an OLED-friendly pure-black mode.
• Choose a default quality recipe, copy results to Downloads, keep the screen awake while a batch runs and control whether the queue opens automatically.
• Storage tools: inspect the private vault, clear conversion history or delete every converted file with a confirmation step.
• First-run tour explains the format families, the 3D viewport and theme choices.

FREE, WITH OPTIONAL ADS

• A small banner can appear above the bottom navigation; it never covers Android's system buttons.
• A full-screen ad can appear after several completed conversions, capped by frequency so it does not interrupt every file.
• Prefer not to pay? Watch one rewarded ad to unlock 24 hours without banners or interstitials.
• Remove Ads is a one-time US$4.99 purchase that hides every ad and is restorable through Google Play.
• Ads are provided by Google AdMob and are subject to consent choices in supported regions. Ad privacy options are available in Settings when required.

DESIGNED TO STAY LOCAL

Your conversion files are read and processed entirely on-device. Nothing is uploaded to a server. The app requests network access only for optional ads and Google Play billing; the conversion engine itself works in airplane mode. Files are kept in the app's sandbox until you delete them, share them or save a copy yourself.

HONEST LIMITS

Some formats depend on the device's hardware codecs (HEIC/AVIF, audio/video encoding); unsupported targets are hidden. DNG is developed from its embedded camera preview. Mesh textures are not carried between 3D formats. PDF OCR, STEP/IGES, RAR and password-protected archives are not included in version 1.0. Scanned PDFs without embedded text cannot be text-extracted. These limits are documented in the app's Format guide and README.

Universal Converter adapts the format taxonomy and conversion ideas of the MIT-licensed Filee project. The Android engine is native Kotlin.

## What's new (max 500)

Universal Converter 1.0: a new ink-and-ember app icon, branded splash, first-run tour, four accent themes with pure-black OLED mode, a fully rebuilt Settings screen, and honest AdMob support (banner, capped interstitial, opt-in rewarded 24-hour pass) plus a one-time Remove Ads purchase. The native offline 3D/CAD/vector/image/RAW/PDF/document/audio/video/archive engine, 3D Studio, batch queue and private vault are all included.

## Assets

| Asset | Path | Notes |
| --- | --- | --- |
| App icon | `store/icon-512.png` | 512×512, new ink/ember mark |
| Feature graphic | `store/feature-graphic-1024x500.png` | 1024×500, new mark and ads-aware copy |
| Screenshot 1 | `store/screenshots/01-convert-anything.png` | 1080×1920, 2026 UI |
| Screenshot 2 | `store/screenshots/02-3d-studio.png` | 1080×1920 |
| Screenshot 3 | `store/screenshots/03-themes-settings.png` | 1080×1920 |
| Screenshot 4 | `store/screenshots/04-private-vault.png` | 1080×1920 |
| Screenshot 5 | `store/screenshots/05-every-format.png` | 1080×1920 |
| Privacy policy | `store/privacy-policy.html` | Host publicly over HTTPS |
| Bundle | `release/UniversalConverterPro-1.0.0.aab` | Signed |
| Mapping | `release/mapping-release.txt` | Generated locally; upload for deobfuscation (not in the public repo) |

## Data safety answers (recommended)

> Google's Data Safety form must be completed by the Play Console account owner. The statements below follow the behaviour of the configured SDKs in this build; verify them against the current AdMob and Play Billing disclosures before publishing.

- **Does the app collect or share user data?** Yes, through Google SDKs for ads and billing. Conversion files are never collected or uploaded.
- **Data collected/shared by Google Mobile Ads:** device or other identifiers (advertising ID, app set ID), approximate location derived from IP, app interactions with ads, and diagnostics/performance data. Used for advertising, measurement and fraud prevention; shared with Google's advertising partners as described in Google's privacy policy. Encrypted in transit.
- **Data collected by Google Play Billing:** purchase history/product entitlement, processed by Google Play. The app stores only the local "Remove Ads owned" flag and purchase token needed to restore the entitlement.
- **Consent:** Google User Messaging Platform collects consent choices in regions where required. Users can reopen ad privacy options in Settings when the form is available.
- **No data collected by the app itself:** no analytics, crash-reporting, account or file-upload SDK; conversion files and their contents stay on-device.
- **Encryption in transit:** yes, HTTPS for Google SDK traffic; cleartext traffic is disabled.
- **Deletion:** users can clear history and the vault in Settings, reset the advertising ID in Android settings, manage ad privacy options in Settings or the Google consent page, and uninstall the app. There is no account.
- **Children:** the app is a general-purpose utility and is not child-directed; the ad request is tagged as not under the age of consent.

## Permissions declaration

| Permission | Why |
| --- | --- |
| `INTERNET` | Optional Google Mobile Ads and Google Play Billing only |
| `ACCESS_NETWORK_STATE` | Let AdMob/Billing respond to connectivity changes |
| `com.google.android.gms.permission.AD_ID` | Advertising ID for AdMob (metadata added by the Google SDK) |
| `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS` | Privacy Sandbox ad APIs merged by Google Mobile Ads |
| `WAKE_LOCK` | Delivery of ad/billing SDK work (conversion work uses the foreground service) |
| `com.android.vending.BILLING` | One-time Remove Ads purchase (added by the Billing library) |
| `POST_NOTIFICATIONS` | Optional conversion progress notification |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Continue local batch conversion when the app is backgrounded |
| No storage permission | Files are selected via SAF / share sheet |

## Content rating

Tools utility with optional advertising and in-app purchases. No user-generated content feeds, gambling, violence, sexual content, profanity or controlled substances. Declare "Contains ads" and "In-app purchases"; not child-directed.

## Publishing checklist

1. Replace all Google test identifiers in `ads.properties` with the owner's AdMob app ID and banner/interstitial/rewarded unit IDs. Test IDs are not allowed in production and never earn revenue.
2. Create a Google UMP consent message for the regions where consent is required (EEA/UK/Switzerland) and test the form with a debug geography before release.
3. In Play Console, create a one-time in-app product with ID `remove_ads` and price US$4.99. Add the Play licensing public key to `billingPublicKey` in `ads.properties`. Purchases cannot be completed from a sideloaded build; use internal testing with license testers.
4. Upload `release/UniversalConverterPro-1.0.0.aab` to an internal testing track first and attach `release/mapping-release.txt`.
5. Host `store/privacy-policy.html` at a public HTTPS URL and paste the URL into Play Console.
6. Fill the support email, icon, feature graphic and five screenshots.
7. Complete Data safety using the ad/billing disclosures above, not “No data collected”.
8. Complete content rating and tick "Contains ads" and "In-app purchases".
9. Test the banner above the app navigation and the Android system bar, the capped interstitial after several conversions, the rewarded 24-hour pass, the Remove Ads purchase and Restore purchase.
10. Back up the upload keystore and passwords before promoting to production. Losing the key prevents updates.

Publication, AdMob account setup, Play Billing product setup and the licensing key are owned by the developer and are not automated by this repository.
