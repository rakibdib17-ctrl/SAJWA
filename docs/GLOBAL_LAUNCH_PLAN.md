# SAJWA Global Launch & Implementation Plan

**Product direction:** Hybrid premium Islamic lifestyle app  
**Launch:** Android + iOS together (subject to platform-ready builds)  
**Growth:** Organic-only marketing; no paid acquisition budget  
**Support model:** Free app with contextual, non-intrusive ads plus voluntary one-time and monthly support  
**Six-month objective:** 10,000 daily active users (DAU) — an ambitious target, not a guarantee

## 1. Release principles

- Keep Quran reading, prayer-time screens, Adhan playback and Tasbeeh free of intrusive ads.
- No forced interstitial ads, deceptive donation prompts, or ads interrupting worship.
- Verify Quran Arabic text, page ordering, translations, Hadith references and audio licensing before release.
- Collect only data needed for app functions, explain it clearly, and obtain consent where required.
- Do not claim offline availability unless the relevant text/audio assets are actually bundled or downloaded and tested.

## 2. Current baseline (from repository README and latest commits)

The repository describes an Android WebView prototype with prayer calculations, Qibla sensor, alarm scheduling, Adhan media service, online Quran/translation/audio, Hadith entry points, Hijri date and local Tasbeeh storage.

Known release blockers/limitations:
- Quran and Quran audio are not yet a complete offline pack.
- Adhan audio is not bundled; offline playback needs a licensed asset.
- Hadith and Dua libraries are incomplete and require source/numbering/translation review.
- Physical-device verification is required for compass, alarms, audio, notifications and exact-alarm permission.
- Mosque finder, fasting/prayer tracking, Hajj/Umrah guides, advanced Hifz, sync and widgets remain future work.
- Current documented CI output is a debug APK; public distribution requires signed production builds.
- iOS implementation and iOS release pipeline are not yet established in the repository baseline.

## 3. Workstreams and release gates

### A. Product quality and accessibility
- Fix and verify Bengali font shaping, Arabic typography, text sizing, safe areas/display cutouts and scrolling behavior.
- Keep the agreed hybrid visual system and balanced information density.
- Verify startup animation timing and Mushaf-style Quran page reader on real devices.
- Test language switching (Arabic, Bengali, English) and RTL/LTR layout.
- Add loading, empty, offline, permission-denied and error states to every core screen.

**Exit gate:** No critical UI defects on supported small/large Android and iOS screen sizes; readable Arabic/Bengali; no clipped content.

### B. Quran, Hadith and Islamic content integrity
- Validate the 114-surah index, Arabic text, page mapping and translation attribution against trusted sources.
- Confirm Mushaf page layout uses a consistent, documented edition/page mapping; do not imply a printed-Mushaf match unless verified.
- Provide clear translation names, translator/source attribution and disclaimers that translations are interpretations.
- For each Hadith, store collection, book/chapter, number, Arabic/source text, translation source and grading only when verifiable.
- Complete Dua/Azkar with source references and reviewed translations.
- Build offline text packs only after integrity checks; provide pack download size and update version.
- Obtain and document rights for all bundled/streamed recitations and Adhan audio.

**Exit gate:** Content audit signed off; no placeholder or unverified religious text presented as authoritative.

### C. Prayer, Qibla and notifications
- Test calculation methods, Asr madhhab, high-latitude handling, timezone/DST and location permission flows.
- Test compass calibration and sensor behavior across device orientations; show a calibration hint when accuracy is poor.
- Verify exact-alarm permission, notification permission, reboot rescheduling, battery restrictions and daylight/timezone changes.
- Test Adhan playback while app is backgrounded, screen locked, audio focus changes and Bluetooth/headphones connected.

**Exit gate:** Repeatable device test results across a representative Android and iPhone test matrix; clear fallback when permissions/sensors are unavailable.

### D. Offline-first behavior
- Separate online and offline status in the UI.
- Bundle essential app shell and selected text assets; make optional Quran/audio packs downloadable.
- Verify app remains useful with airplane mode enabled and does not show false offline indicators.
- Add download progress, retry, storage estimate, checksum/version validation and safe update behavior.

**Exit gate:** Documented airplane-mode test passes for every feature labelled offline.

### E. Ads, donations and privacy
- Add AdMob only after consent/privacy architecture is in place; configure age/region handling and applicable consent messaging.
- Use small native/banner placements only in non-worship content areas; no ads on Quran reading, Tasbeeh, prayer time or during Adhan.
- Add one-time and monthly voluntary support options with transparent provider fees and cancellation instructions.
- Confirm payment/donation provider availability and legal/tax requirements in the publisher's country before enabling payments.
- Publish a privacy policy; complete Google Play Data safety and Apple App Privacy disclosures from actual SDK/data behavior.
- Add account/data deletion flow if accounts or personal data are introduced.
- Use analytics with data minimization and consent as required; track DAU, retention, crash-free sessions, feature use and ad performance without collecting unnecessary sensitive data.

**Exit gate:** Consent, privacy disclosures, store declarations and donation flow reviewed; test ads only until production approval.

### F. Android + iOS delivery
- Keep a shared feature specification and platform-specific acceptance tests.
- Android: signed AAB, Play App Signing, versioning, release notes, internal/closed testing and production access.
- iOS: implement and test a native iOS shell/port, signing, provisioning, App Store Connect metadata, TestFlight and review compliance.
- Create store assets: icon, screenshots, localized descriptions, keywords, support URL and privacy URL.
- Verify app name, trademark/domain availability and publisher account ownership before launch.

**Exit gate:** Both stores accept the submitted production builds; no debug APK distributed as a public release.

## 4. Six-month organic growth plan

| Month | DAU milestone | Focus |
|---|---:|---|
| 1 | 300 | Private beta, feedback, crash and usability fixes |
| 2 | 800 | Public launch if release gates pass; ASO and community outreach |
| 3 | 1,800 | Short-form educational content and referral sharing |
| 4 | 3,500 | Localized community partnerships and language expansion |
| 5 | 6,500 | Retention improvements, seasonal content and creator outreach |
| 6 | 10,000 | Double down on channels with measurable retained users |

These are stretch milestones. Measure 1-day, 7-day and 30-day retention; installs alone are not success.

### Organic weekly cadence
- 3–5 useful short videos/posts per week, localized where practical.
- 2 community education/demo posts per week, following group rules and avoiding spam.
- Monthly release notes and public changelog.
- Referral/share action that shares the app link only when the user chooses.
- Review feedback and crash reports weekly; ship small quality releases.

## 5. Revenue model and measurement

- Keep core Islamic features free.
- Use conservative, frequency-capped ads only in appropriate non-worship areas.
- Offer voluntary one-time and monthly support; never imply donation is required for religious benefit.
- Report gross revenue separately from platform fees, taxes, refunds and operating costs.
- Forecast revenue from actual country mix, ad impressions, fill rate and eCPM after beta; do not promise a fixed income per DAU.

Dashboard metrics:
- DAU / MAU and DAU-to-MAU ratio
- D1 / D7 / D30 retention
- Organic install source and store conversion
- Crash-free users and ANR rate
- Prayer notification delivery success
- Offline pack completion/failure
- Ad impressions per active user and effective revenue
- Donation conversion, refunds and recurring-support cancellations

## 6. Immediate next execution order

1. Audit repository and CI; establish a release branch and issue tracker.
2. Reproduce and fix the currently reported Bengali font, safe-area/cutout, home-banner scrolling and startup issues.
3. Verify the latest Mushaf page reader and Quran content on physical Android devices.
4. Close Quran/Hadith/Dua content and audio licensing gaps.
5. Complete device testing for prayer, Qibla, alarms and Adhan.
6. Establish the iOS implementation and TestFlight pipeline.
7. Add privacy/consent, ads and voluntary support only after the above foundations pass.
8. Prepare store listings and staged rollout.

**Launch decision:** Do not publish globally until both platform builds, content integrity, privacy disclosures and core worship flows pass their release gates.
