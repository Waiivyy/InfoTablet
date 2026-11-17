# Third-party notices

The app bundles the following components. Their licences permit redistribution in the APK.

| Component | Version | Licence | Source |
|---|---|---|---|
| OkHttp | 3.12.13 | Apache License 2.0 | https://github.com/square/okhttp |
| Okio | 1.15.0 | Apache License 2.0 | https://github.com/square/okio |
| Conscrypt | 2.5.3 | Apache License 2.0 | https://github.com/google/conscrypt |
| jsoup | 1.15.3 | MIT License | https://jsoup.org |
| NanoHTTPD | 2.3.1 | BSD 3-Clause License | https://github.com/NanoHttpd/nanohttpd |
| ZXing core (QR codes) | 3.3.3 | Apache License 2.0 | https://github.com/zxing/zxing |
| Roboto | 3.015 | SIL Open Font License 1.1 | https://github.com/googlefonts/roboto-classic |
| Roboto Mono | 3.001 | SIL Open Font License 1.1 | https://github.com/googlefonts/robotomono |

The full text of the SIL Open Font License ships with the app in `app/src/main/assets/licenses/OFL.txt`, and the NanoHTTPD licence in `app/src/main/assets/licenses/NanoHTTPD-BSD-3-Clause.txt`.

`app/src/main/assets/certs/roots.pem` contains public root certificates of certificate authorities, included only so that HTTPS works on Android 4.2, whose built-in list is from 2013.

Data source attributions (Open-Meteo, CAMS, MeteoAlarm, FHMZ BiH, CBBH and others) are listed in `docs/sources.md` and in the app under Postavke, O aplikaciji.
