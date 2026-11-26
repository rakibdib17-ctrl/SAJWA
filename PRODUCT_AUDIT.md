# SAJWA competitor feature audit

Date: 2026-11-26

This is a desk-research benchmark based on published app feature descriptions and comparison pages, not a claim of hands-on testing of every app build. Feature availability can vary by platform, region, subscription, and app version.

## Ten reference apps

| Reference app | Strengths to learn from | SAJWA product response |
|---|---|---|
| Muslim Pro | Prayer times, Adhan, Quran, duas, Qibla, tasbih, Hijri calendar, fasting and lifestyle tools | A unified daily worship dashboard with transparent settings and optional lifestyle tools |
| Pillars | Focused prayer times, Qibla, prayer/fasting tracking, privacy-conscious experience | Accurate prayer engine, prayer log, privacy-first local storage |
| Quran.com | Quran reading, translations, tafsir and recitation | Full Quran reader with Arabic, Bengali and English, reciters, bookmarks, search and tafsir |
| Tarteel | Recitation-led memorisation and mistake feedback | Hifz mode, repeat/loop, memorisation plans; AI feedback only after a safe and reliable speech pipeline exists |
| Athan by IslamicFinder | Prayer alerts, Quran, duas/azkar, Qibla, tasbih, Ramadan and Hajj/Umrah guides | Prayer and seasonal worship hub with configurable Adhan and Ramadan/Hajj guides |
| Sajda | Prayer/Qibla utilities and searchable Islamic content | Fast prayer/Qibla access and referenced content search |
| Quran for Android | Offline Quran reading, audio and bookmarks | Downloadable Quran text/audio packs and reliable offline reading |
| Quran Majeed | Mushaf, recitation, translation and prayer utility combination | Mushaf-style reading, reciter playlists and optional prayer tools |
| Al Quran (Tafsir & by Word) | Word-by-word translation and tafsir | Word-level Arabic/Bengali/English study and tafsir navigation |
| Ayat (King Saud University) | Mushaf presentation, recitation and Quran study | Clean page-based Mushaf mode and recitation controls |

## Consolidated SAJWA feature scope

### Core worship
- Prayer times with automatic calculation method, madhhab selection, location refresh, manual correction and mosque-time override.
- Full Adhan audio playback through SAJWA's own Android media playback service, with per-prayer audio choices, volume, silent/vibration options, stop/snooze controls and playback notification.
- Qibla compass with true/magnetic heading handling, screen-rotation remapping, calibration guidance and a map/bearing fallback.
- Quran: all 114 surahs, Arabic text, Bengali and English translations, audio recitation, repeat, bookmarks, last-read position, search, juz/page navigation, tafsir and word-by-word study.
- Hadith: source-labelled collections, book/chapter/number, grading where available, language filters, search, favourites and daily hadith.
- Dua and Azkar: morning/evening, after salah, sleep, travel, protection and daily-life categories, Arabic/transliteration/translation, references and counters.
- Tasbeeh with custom dhikr, targets, haptic feedback and history.

### Planning and learning
- Hijri calendar, Ramadan timetable, Suhoor/Iftar, fasting tracker, Hajj/Umrah guide, prayer tracker, qada tracker, learning plans, beginner salah/wudu guides and progress summaries.
- Widgets, reminders, saved content, local notifications and offline content packs.
- Accessibility, multilingual UI (Arabic/Bengali/English), light/dark themes, large text and right-to-left layout.

### Quality and trust requirements
- No invented religious text. Every Quran/Hadith/Dua item must carry a traceable source; translations and grading must be labelled.
- No promise that all features can be completed in one code pass. Each module needs functional implementation, content licensing, tests and device validation.
- Keep private worship history on-device by default; sync/account features must be opt-in.
- Keep the app usable without requiring users to install another Islamic utility app, while clearly marking features that need internet, downloaded content, sensors or optional permissions.

## Implementation order

1. Repair and validate prayer/Qibla/Adhan foundations.
2. Complete Quran reader and verified offline/audio pipeline.
3. Complete Hadith and Dua/Azkar libraries with source metadata.
4. Add trackers, Hijri/Ramadan, learning and Hajj/Umrah modules.
5. Add widgets, accessibility, backup/sync and release hardening.

## Important Adhan implementation note

Android must wake the app at the prayer time when the UI is closed; the scheduled event is only the trigger. The sound itself must be played by SAJWA's own foreground media-playback service, not by the system alarm ringtone. Android requires the foreground playback service to show an ongoing media notification and observe platform audio-focus/background rules. Exact timing may require the user's Android alarm permission and can be affected by OEM battery restrictions.
