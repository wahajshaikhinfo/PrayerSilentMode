# Prayer Silent Mode

An Android app that automatically switches the phone to Silent mode at each of the
five daily prayer times (Fajr, Dhuhr, Asr, Maghrib, Isha) and switches it back to
Normal after a duration you choose in the app.

Location is set manually: pick your country, then your city, from a built-in list.
Prayer times are then calculated **on-device** (no internet required) using the
standard sun-position / hour-angle method, with:
- A selectable calculation method (Muslim World League, ISNA, Egyptian, Karachi,
  Umm al-Qura, Gulf) — defaults to **Karachi**.
- A selectable Asr convention (Standard / Hanafi) — defaults to **Hanafi**, per your
  earlier choice.

## What you need before building

- **Android Studio** (Koala/2024.1 or newer recommended) — free, from
  https://developer.android.com/studio
- That's it. Android Studio bundles the JDK and Gradle it needs.

## How to build and run it

1. Unzip the project and open the `PrayerSilentMode` folder in Android Studio
   (`File > Open`, then select the folder).
2. Let Gradle sync — Android Studio will download the Android/Kotlin build tools the
   first time (this needs internet access; it does **not** need any special network
   configuration beyond what Android Studio already uses).
3. Plug in a phone (with USB debugging on) or start an emulator, then press **Run ▶**.
4. To get a shareable APK instead: **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
   The APK appears under `app/build/outputs/apk/debug/`.

## Using the app

1. Open the app, pick your **Country** and **City**.
2. Check the **Calculation method** and **Asr convention** (Karachi + Hanafi are
   pre-selected).
3. Set how many minutes the phone should stay silent after each prayer time with the
   slider.
4. Toggle off any individual prayer you don't want silenced.
5. Tap **Grant Do Not Disturb access** and **Allow exact alarms** if those buttons
   appear (Android hides ringer-mode changes and precise alarm timing behind these
   two special permissions — see below).
6. Tap **Save & schedule**.

From then on, the app runs quietly in the background: at each enabled prayer time it
silences the phone, and after your chosen number of minutes it restores the normal
ringer. It also re-schedules itself automatically every day and after a reboot.

## Two permissions Android requires you to grant by hand

Android treats "change the ringer mode" and "wake the phone at an exact minute" as
sensitive, so they can't be auto-granted like a normal permission popup — the app
will show you a button for each when needed:

- **Do Not Disturb access** — required for the app to actually change the ringer
  mode. Without it, the app will still calculate and display prayer times but won't
  be able to silence the phone.
- **Alarms & reminders (exact alarms)** — required on Android 12+ so the silence/
  restore alarms fire at the exact minute rather than being delayed by the system.

## A real-device reliability tip

Some phone brands (Xiaomi/MIUI, Huawei, OnePlus/OxygenOS, Oppo, Vivo, and some
Samsung configurations) aggressively kill background apps to save battery, which can
delay or drop alarms. If prayer times seem to fire late or not at all on your device,
go to **Settings > Apps > Prayer Silent Mode > Battery** and set it to
**Unrestricted** / disable battery optimization for the app.

## Adding more cities

Prayer times need a latitude, longitude, and time zone. These live in
`app/src/main/assets/cities.json`, grouped by country:

```json
"Country Name": [
  {"city": "City Name", "lat": 12.34, "lng": 56.78, "tz": "Region/City"}
]
```

`tz` must be a valid IANA time zone id (e.g. `Asia/Karachi`, `Europe/London`) —
this is what makes the times correctly follow daylight saving where applicable.
Add as many entries as you like; no code changes are needed.

## Project structure

```
app/src/main/java/com/prayersilent/app/
  MainActivity.kt            – settings screen (location, method, per-prayer toggles)
  PrayerTimeCalculator.kt    – the astronomical prayer-time calculation
  CityRepository.kt          – loads cities.json
  Prefs.kt                   – SharedPreferences-backed settings
  AlarmScheduler.kt          – schedules/cancels the AlarmManager alarms
  SilentModeReceiver.kt      – fires at prayer time -> silences the ringer
  RestoreModeReceiver.kt     – fires after the duration -> restores the ringer
  DailyRecomputeReceiver.kt  – fires just after midnight -> reschedules tomorrow
  BootReceiver.kt            – reschedules everything after a reboot
  NotificationHelper.kt      – small "silent mode on/off" notification
app/src/main/assets/cities.json  – built-in city/location database
app/src/main/res/                – layout, strings, colors, launcher icon
```

## Known limitations / good next steps

- The launcher icon is a simple placeholder — swap it via Android Studio's
  **Image Asset** tool (`res > New > Image Asset`) whenever you like.
- Latitudes very close to the poles can make Fajr/Isha angles mathematically
  undefined for part of the year; the calculator returns `NaN` for those and the
  app simply skips scheduling that prayer that day. This won't affect any of the
  built-in cities.
- There's no manual "silence right now" override button yet — easy to add as an
  extra button that calls the same ringer-mode code directly.
