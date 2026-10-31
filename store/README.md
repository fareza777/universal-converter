# Play Store handoff

This folder contains the listing copy and visual assets for **Universal Converter**.

## Upload

1. In Google Play Console, create the app **Universal Converter** with package `com.universalconverter.pro`.
2. Replace the Google **test** AdMob IDs in `../ads.properties` with the owner's production AdMob app ID and banner/interstitial/rewarded unit IDs. Create a UMP consent message for regions that require it.
3. Create a one-time in-app product `remove_ads` at US$4.99 and add the Play licensing public key to `billingPublicKey` in `../ads.properties`.
4. Build a fresh signed AAB/APK (`./gradlew :app:bundleRelease :app:assembleRelease`); upload `../release/UniversalConverterPro-1.0.0.aab` to an internal testing track.
5. Upload `../release/mapping-release.txt` for deobfuscation.
6. Complete Data safety for the Google ad and billing SDKs using the disclosure guidance in `listing.md`; do not declare “No data collected”.
7. Declare notification, foreground-service, network, advertising-ID and billing permissions as described in `listing.md`.
8. Add the five 1080×1920 screenshots, the 512×512 icon and the 1024×500 feature graphic.
9. Paste the hosted privacy-policy URL, support email, and the short/full description from `listing.md`.
10. Install from the internal track on a real phone; verify onboarding, themes, banner above the app bottom bar, capped interstitial, rewarded 24-hour pass, Remove Ads purchase and Restore purchase. Run the in-app Device self-check.
11. Back up the upload keystore and `keystore.properties` offline before promoting to production.

## Monetisation notes

- **Ads:** Google AdMob banner, interstitial and opt-in rewarded. The banner is laid out above the app's bottom navigation and uses the Scaffold's bottom padding, so Android's system navigation buttons remain unobstructed.
- **Interstitial cap:** after three completed conversions while the queue is visible, at least two minutes apart. Not shown during onboarding or an active batch.
- **Rewarded:** opt-in in Settings; grants 24 hours ad-free. It never gates a conversion feature.
- **Remove Ads:** one-time `remove_ads` product, localised Play price with a US$4.99 fallback. Hides banners, interstitials and rewarded prompts. Restore is available in Settings.
- **Consent:** Google UMP is requested before Mobile Ads initialisation. If consent does not allow ad requests, every placement stays hidden.
- **Web/network claims:** conversions are local and never upload files. `INTERNET` and `ACCESS_NETWORK_STATE` exist only for optional ads and Play Billing. The privacy policy and Data Safety form must reflect this.

## Hosting the privacy policy

Google Play requires a public HTTPS URL. Any static host works; a free route is GitHub Pages:

1. Create a repository (public or private with Pages enabled).
2. Publish `privacy-policy.html` at the root (or the whole folder).
3. Enable Pages and use `https://<account>.github.io/<repo>/privacy-policy.html` in Play Console.
4. Replace the support-contact sentence with the real support email if desired. The current wording points to the Play listing's developer contact.

## Asset checklist

- [x] 512×512 app icon (`icon-512.png`, new ink/ember mark)
- [x] 512×512 round icon preview (`icon-512-round.png`)
- [x] 1024×500 feature graphic (`feature-graphic-1024x500.png`, new mark and ads-aware copy)
- [x] Five 1080×1920 phone screenshots from the final UI (home, Studio, themes, vault, onboarding)
- [x] Short description ≤80 characters
- [x] Full description
- [x] What's-new (≤500 characters)
- [x] Data-safety guidance
- [x] Permission declarations
- [x] Signed AAB and mapping (rebuild after production IDs are set)
- [ ] Production AdMob app/unit IDs (owner action)
- [ ] UMP consent message (owner action)
- [ ] `remove_ads` product and Play licensing key (owner action)
- [ ] Hosted privacy-policy URL (owner action)
- [ ] Support email (owner action)
- [ ] Content rating questionnaire and “Contains ads / In-app purchases” (owner action)
- [ ] Internal-track purchase test on a physical device (owner action)

## Known Play policy notes

- Contains ads and in-app purchases; complete the Play Console "Contains ads" and "In-app purchases" declarations.
- Network access is used only for optional ads and Google Play billing; the conversion engine works without network. Do not claim "no INTERNET permission".
- Google Mobile Ads may collect device/other IDs, approximate location, ad interactions and diagnostics; disclose this in Data Safety and the privacy policy.
- Google Play Billing processes purchase history; the app stores only a local entitlement and purchase token.
- Foreground service: local data-sync type; conversion progress notification. This is user-initiated local processing, not background data harvesting.
- The app does not request broad storage permissions; file access uses the system picker and share sheet.
- The file provider is not exported; exports use per-URI grants.
- `allowBackup=false`; app data remains on the device.
- Version 1.0.0 targetSdk 36 satisfies the current Google Play target API requirement as of October 2026.
