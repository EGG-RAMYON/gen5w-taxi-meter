# gen5w-taxi-meter

A personal Gen5W taxi meter simulator and read-only HMG-API diagnostic app targeting Android 4.4.2 / API 19 and HMG-API v1.0.2. Install and verify the diagnostic mod first.

This project adapts the taxi meter from **Veloster N Dashboard by reikop and contributors**. It reuses the fare engine, receipts, area data, and renderer, with Gen5W-specific speed input and layout changes. The upstream MIT notice is included. [Veloster N Dashboard](https://github.com/reikop/veloster-n-dashboard) · [Third-party notices](THIRD_PARTY_NOTICES.md)

The two HMG-API Mod ZIPs each contain a signed APK. The separately supplied APKs require the corresponding enabled mod for access to vehicle readings. See the [vehicle checklist](docs/VEHICLE_TEST.md) and [upstream analysis](docs/UPSTREAM.md).

Build from WSL2 on Windows, or Linux, using a JDK, Android Platform 19, Android Build Tools 34.0.0, zip and unzip. The [Korean README](README.md) contains the commands and environment variable setup. Keep your APK and HMG-API author keys for future updates. The test releases and independently signed local builds have different identities.

The meter keeps the upstream renderer, fare engine and boundary data with attribution. It ships with a new eight-frame horse sprite. Regenerate it with `python3 scripts/draw_horse.py` using Pillow. See [third-party notices](THIRD_PARTY_NOTICES.md).
