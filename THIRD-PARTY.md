# Third-party components — publication audit

This is an inventory, not a completed license compliance notice. Verify the
exact source revisions, bundled binary checksums, transitive dependencies and
corresponding license and notice files before distributing source or APKs.

| Component | Used by project | Upstream license / status |
| --- | --- | --- |
| AndroidLibXrayLite | `app/libs/libv2ray.aar` | Matches the SHA-256 published for [v26.11.9](https://github.com/2dust/AndroidLibXrayLite/releases/tag/v26.11.9). See the [license at that tag](https://github.com/2dust/AndroidLibXrayLite/blob/v26.11.9/LICENSE). |
| Xray-core | Included by the Xray library | [MPL-2.0](https://github.com/XTLS/Xray-core/blob/676eaf66f8f0/LICENSE). The native libraries identify revision `676eaf66f8f0` in their Go build information. |
| AmneziaWG Android | Maven `com.zaneschepke:amneziawg-android:2.3.7` | [Apache-2.0](https://github.com/wgtunnel/amneziawg-android/blob/master/LICENSE). Audit native and transitive components separately. |
| ZXing core 3.5.3 | `app/libs/zxing-core-3.5.3.jar` | SHA-256 matches the artifact on Maven Central. [Apache-2.0](https://github.com/zxing/zxing/blob/zxing-3.5.3/LICENSE) and upstream NOTICE. |
| AndroidSVG 1.4 | `app/libs/androidsvg-1.4.aar` | SHA-256 matches the AndroidSVG 1.4 AAR on Maven Central. [Apache-2.0](https://github.com/BigBadaboom/androidsvg/blob/master/LICENSE). |
| Country flags | `app/src/main/assets/flags/` | The 255 SVGs match 4:3 artwork in [flag-icons 7.5.0](https://github.com/lipis/flag-icons/tree/7aa5b2bdddd570ece62c812c0cb588ccdc099e2e). The MIT license and source notice ship in the APK. |
| GeoIP and GeoSite data | `app/src/main/assets/v2ray-geoip.dat`, `v2ray-geosite.dat` | Pinned V2Fly releases. GeoIP is CC BY-SA 4.0 with DB-IP Country Lite attribution under CC BY 4.0. GeoSite is MIT. The APK build excludes the old data from the Xray AAR. |

In particular, LGPL obligations for the actual Xray binding and its native
linkage need review, including corresponding source, notices and the ability
to modify or replace the library as applicable. License names alone do not
establish that distribution requirements are satisfied.

Confirmed license texts and notices are bundled in `assets/licenses/` in the APK.
See the release records for [Xray](release-licensing/XRAY.md),
[geographic data and flags](release-licensing/GEO.md) and
[other dependencies](release-licensing/dependencies/README.md).

The [MIT License](LICENSE) applies to Vexis's own code. The components listed
above retain their respective licenses.
