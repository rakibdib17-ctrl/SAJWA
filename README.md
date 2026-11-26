# SAJWA — Find Peace in Faith

SAJWA is an Android Islamic lifestyle app prototype using a premium emerald-and-gold visual language, with English, Bengali and Arabic interface options.

## Current implementation

- Android WebView shell with local HTML interface.
- Location-based prayer-time calculations using the Adhan library.
- Qibla bearing and rotation-vector compass sensor with display-rotation remapping.
- Exact prayer alarm scheduling and boot rescheduling.
- SAJWA foreground media playback service for Adhan audio; it does not use the system alarm ringtone.
- Quran reader using AlQuran Cloud, Bengali/English translation selection and Alafasy surah audio streaming.
- Hadith collection entry points with a safe fallback if a provider does not return verifiable content.
- Hijri date display, theme/language controls and locally stored Tasbeeh count.

## Important limitations before public release

- No Adhan audio file is bundled yet. The service can play a licensed local file named `sajwa_adhan.mp3` in the app music directory or a supplied audio URI. It intentionally does not substitute a notification/alarm sound. Offline Adhan requires a licensed audio file to be downloaded or bundled.
- Quran content/audio is not yet a complete offline pack.
- Hadith and Dua libraries are not complete; every narration requires source, numbering and translation review.
- Compass accuracy, alarm delivery, audio playback, notification permission and exact-alarm access require physical-device testing.
- Mosque finder, fasting and prayer tracking, Hajj/Umrah guides, advanced Hifz, full offline audio packs, sync and widgets remain future work.

## Build

GitHub Actions builds a debug APK artifact. A debug APK is for testing, not a signed Play Store release.
