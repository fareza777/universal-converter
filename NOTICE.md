# Third-party notices

Universal Converter includes and adapts work from the open-source community, plus Google advertising and billing SDKs.

## Filee

This application adapts the format taxonomy, conversion concepts and selected algorithm ideas from **Filee**:

- Copyright (c) 2026 Myeonggu Kim (KnifeLemon) and Filee contributors
- Repository: https://github.com/KnifeLemon/Filee
- License: MIT

The Filee desktop application is written in C#. This Android application is a native Kotlin implementation; the Filee binary/code is not executed on Android. The full MIT text is included at [`docs/licenses/Filee-MIT.txt`](docs/licenses/Filee-MIT.txt) and inside the app (`Settings → Open-source licenses`).

## Runtime libraries

| Library | License | Copyright / project |
| --- | --- | --- |
| AndroidX, Jetpack Compose, Material Components | Apache-2.0 | The Android Open Source Project / Google |
| Kotlin standard library, kotlinx.coroutines | Apache-2.0 | JetBrains s.r.o. |
| PDFBox-Android | Apache-2.0 | Tom Roush / Apache PDFBox contributors |
| AndroidSVG | Apache-2.0 | Paul LeBeau / caverock |
| Apache Commons Compress | Apache-2.0 | The Apache Software Foundation |
| XZ for Java | Public domain | Lasse Collin / Tukaani |
| Jsoup | MIT | Jonathan Hedley |
| Google Mobile Ads SDK | Google APIs Terms of Service | Google LLC |
| Google Play Billing Library | Google Play Terms of Service | Google LLC |
| Google User Messaging Platform | Google APIs Terms of Service | Google LLC |
| Instrument Serif | SIL Open Font License 1.1 | The Instrument Serif Project Authors |
| Manrope | SIL Open Font License 1.1 | The Manrope Project Authors |

The full Apache-2.0, MIT (Jsoup), OFL and Google SDK notices are bundled in [`docs/licenses`](docs/licenses) and inside the app (`Settings → Open-source licenses`). Google SDK terms: <https://developers.google.com/terms> and <https://play.google.com/about/play-terms/>. Google privacy policy: <https://policies.google.com/privacy>.

## Deliberately not shipped

- No FFmpeg or FFmpegKit binary (no GPL/LGPL native transcoder is bundled).
- No RAR decompressor.
- No separate analytics, attribution or crash-reporting SDK. Google Mobile Ads, Google Play Billing and Google User Messaging Platform are used only for optional ads, consent and the Remove Ads purchase; they are not part of the conversion engine.

## Fonts

Instrument Serif and Manrope are used under the SIL Open Font License. They may be bundled and redistributed with this application; they are not sold separately. The OFL texts are included verbatim.
