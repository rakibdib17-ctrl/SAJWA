# SAJWA — Find Peace in Faith

SAJWA is an early-stage Islamic lifestyle app prototype. This repository will contain the Android project and automated debug APK build workflow.

## Current prototype status

The Android app is a WebView wrapper around a lightweight offline-capable interface. Currently implemented: English/Bengali/Arabic interface switching, light/dark theme, and locally stored Tasbeeh counter.

Not yet implemented: verified Quran text/audio, sourced Hadith and Dua library, accurate prayer-time engine, Adhan notifications, Qibla compass, Hijri calendar, account sync, and full offline content packs. Religious content and calculations must be reviewed and verified before release.

## Build

A GitHub Actions workflow will build a debug APK and publish it as a downloadable workflow artifact. A debug APK is for testing, not a signed Play Store release.
