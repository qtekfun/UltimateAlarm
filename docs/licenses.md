# Licence review (Phase 12)

- **App code**: GPL-3.0-or-later (`LICENSE`).
- **Sounds**: 13 tones synthesised by `tools/sounds/generate.py`, project's own work, GPL-3.0-or-later
  (`feature/sounds/src/main/assets/sounds/LICENSES.md`, checked by CI).
- **Icon and screenshots**: project's own work, GPL-3.0-or-later.
- **Holiday data** (downloaded at runtime or embedded):
  - Nager.Date API (https://date.nager.at): public holiday data; the embedded snapshot `assets/holidays/ES-*.json` is the
    API's response, used for offline first start. Nager.Date is open source (MIT).
  - Calendarios Nacionales (https://calendariosnacionales.com): free API, terms require visible attribution with a link
    (shown in Settings → Holidays) and caching; nothing of it is embedded in the APK.
- **Third-party libraries**: see `docs/dependencies.md`; Licensee enforces the allowed licences at build time.
