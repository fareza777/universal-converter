# Universal Converter — Privacy Policy

**Effective date:** 31 October 2026
**Applies to:** Universal Converter for Android, package `com.universalconverter.pro`

> Host this document (or the styled `privacy-policy.html`) at a public HTTPS URL and provide that URL in Google Play Console. The app itself works offline; the policy URL is only needed for the store listing.

## Summary

Universal Converter converts files on your device. Conversion files and their contents are **never uploaded**. The app has no account system and no analytics or crash-reporting SDK of its own.

The free version is supported by **Google AdMob** advertising and offers an optional one-time **Remove Ads** purchase through **Google Play Billing**. Those Google services process limited data as described below. You can control advertising consent and privacy options in Settings where required.

## Information the app accesses

- **Files you choose.** Files are opened only through the Android system picker, the share sheet, or a drag-and-drop gesture. Selected files are copied into the app's private storage (the vault) so conversion can continue reliably. The source remains on your device.
- **File metadata needed for conversion.** Name, size, detected format and, for JPEG captures, optional EXIF metadata (date, camera, GPS) when you enable “Keep capture metadata.” This stays on the device.
- **Conversion reports.** A local history record (source name, target format, output size, duration, SHA-256 checksum, pipeline steps and warnings) is stored in a local database so you can inspect and repeat conversions.
- **Preferences.** Theme, accent, defaults and onboarding state are stored in Android SharedPreferences on the device.

None of the above is sent to the developer or to an ad server.

## Conversion processing

The conversion engine is native code running on your phone. It reads only the files you select and writes outputs to the app's private vault (and optionally a copy to Downloads). Files are not uploaded, and the conversion engine does not require network access.

## Advertising (Google AdMob)

The free version can show:

- an adaptive banner above the app's bottom navigation,
- a full-screen interstitial after several completed conversions, capped by frequency, and
- an opt-in rewarded ad that unlocks 24 hours without banners or interstitials.

Google Mobile Ads may process:

- device or other identifiers (such as the advertising ID and app set ID),
- approximate location derived from the IP address,
- app and ad interactions (which ad was shown, clicks, impressions),
- diagnostics and performance information.

This data is used by Google for advertising, measurement, fraud prevention and service operation, and may be shared with Google's advertising partners. Data is transmitted over HTTPS. The developer does not receive your conversion files or their contents.

Consent is collected through **Google User Messaging Platform (UMP)** where required (for example the EEA, UK and Switzerland). You can reopen advertising privacy options from Settings when the form is available. You can also reset or delete your advertising ID in Android system settings.

Google privacy policy: https://policies.google.com/privacy

## Purchases (Google Play Billing)

The optional **Remove Ads** purchase (one-time, US$4.99 or the local Play Store price) is processed by Google Play. Google handles payment details; the app never sees your card or bank information. The app receives the product ID, purchase state and purchase token so it can confirm and restore the entitlement. The app stores only a local “Remove Ads owned” flag and the purchase token; the purchase token is not sent to the developer's servers (there are none).

Google Play Terms of Service: https://play.google.com/about/play-terms/

## Sharing and saving

When you tap Share, the Android system share sheet opens and you choose the destination. The file is exposed through a scoped FileProvider grant. “Save copy” uses Android MediaStore to write a copy to `Downloads/Universal Converter` on Android 10 and later. These are local system operations.

## Notifications and foreground service

Notification permission is optional and is used for conversion progress and completion. A foreground service keeps long conversions running when you leave the app; Android displays the required ongoing notification. Both operate only on local files.

## Retention and deletion

- Conversion files, the inbox, history and preferences remain in the app's private storage until you delete them in the Library/History, clear the vault or history in Settings, clear app data, or uninstall the app.
- Copies you save to Downloads remain under your control.
- Advertising consent choices can be changed in Settings (Ad privacy options) where available.
- There is no account to delete. For Google-held ad or purchase data, use your Google account privacy controls or contact Google.

## Children

Universal Converter is a general-purpose utility and is not directed to children. The app does not knowingly collect personal information from children. Advertising requests are tagged as not directed to children under the age of consent (`setTagForUnderAgeOfConsent(false)`). If you believe a child has provided personal information, contact the developer through the Play Store support channel.

## Security

Files are protected by Android's app sandbox. Shared-storage access uses the system picker and FileProvider. Cleartext network traffic is disabled; Google SDK traffic uses HTTPS. The app does not declare or use a server.

## Changes

If data practices change, this policy will be updated with a new effective date and included with the app update. Material changes to advertising, accounts or data collection will be reflected in the Google Play Data Safety section.

## Contact

For privacy questions, use the developer support email displayed on the Universal Converter Google Play listing. The developer responds through the Play Store support channel.
