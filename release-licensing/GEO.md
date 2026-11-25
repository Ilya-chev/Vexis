# Geo data and flags: provenance audit

The Happ files previously shipped by the app match the public download endpoints as of 2026-11-25, but their redistribution terms were not established. They were removed from application assets. The built-in preset now uses the bundled V2Fly files and no longer includes `ru-available-only-inside`; consequently its domain coverage may differ. It retains `category-ru`, the other listed categories and explicit domains. Existing private files are not deleted or overwritten.

## Application assets

SHA-256 of the current V2Fly files and the two removed Happ files:

| File | SHA-256 |
| --- | --- |
| happ-default-geoip.dat | 8a3a63ec1a25c507b7cead5173ee8d3d9ca1c321ae02bf414e621fa9ab0cc50b |
| happ-default-geosite.dat | 5a8ee197e7ad45d50c6f0c75f3975c6c6d1935a746e3d15b44102022fe8f3a82 |
| v2ray-geoip.dat | eedb24b3b9fcba27e06b92c0428dab90d9496083091ae4cf39e11387fd99d0e2 |
| v2ray-geosite.dat | 94ad31b62a6e977599a5ac7489e94f8e1fe2c4a2b679336ca954b33bdf6aadf4 |

The SHA-256 digests of [Happ GeoIP](https://happ.haus/geo/base/geoip.dat) and [Happ GeoSite](https://happ.haus/geo/base/geosite.dat) exactly match the two `happ-default` files above.

The `v2ray-geosite.dat` digest matches `dlc.dat` in [domain-list-community release 20261115151618](https://github.com/v2fly/domain-list-community/releases/tag/20261115151618), whose [license](https://github.com/v2fly/domain-list-community/blob/20261115151618/LICENSE) is MIT. The `v2ray-geoip.dat` digest matches `geoip.dat` in [V2Fly GeoIP release 202611080027](https://github.com/v2fly/geoip/releases/tag/202611080027); its [configuration](https://github.com/v2fly/geoip/blob/202611080027/config.json) uses DB-IP Country MMDB, private, and test inputs. The project states CC BY-SA 4.0 and attributes DB-IP data under CC BY 4.0. The app includes the respective notices and license texts in `assets/licenses`. The matched binaries are retained, so existing category references and installed-file migration remain unchanged.

When routing is enabled `RoutingPresets.installBundledAssets` copies V2Fly files to app storage, including the default names used by imported rules, only when absent. The built-in direct preset references private and Russian IPs, and the `private`, `tld-ru`, `category-ru`, `category-gov-ru`, `category-bank-ru`, and `category-ecommerce-ru` site categories. Imported rules may use other categories and independently downloaded files; these are not rewritten. Fresh imports without geo URLs are checked against the bundled category names and reject unavailable categories with an explanatory message.

## Assets embedded in app/libs/libv2ray.aar

| AAR entry | SHA-256 |
| --- | --- |
| assets/geoip-only-cn-private.dat | 597cddf20e767b9c581a299197d8bb59bee5298fae7fe35b9f65a8f1de105106 |
| assets/geoip.dat | 90b78ab861c1cdc3f871f305f9ceedfb8d6d6329e3a40295e6773e057297384c |
| assets/geosite.dat | bd2b6303c4ad7cef801744d2ecb03323a665605834c2b6f8f6348bd18df448e5 |

The official AndroidLibXrayLite v26.11.9 [workflow](https://github.com/2dust/AndroidLibXrayLite/blob/v26.11.9/.github/workflows/main.yml) runs [gen_assets.sh](https://github.com/2dust/AndroidLibXrayLite/blob/v26.11.9/gen_assets.sh). Both standard file hashes match [Loyalsoldier/v2ray-rules-dat 202611092204](https://github.com/Loyalsoldier/v2ray-rules-dat/releases/tag/202611092204). The CN/private file matches [Loyalsoldier/geoip 202611050019](https://github.com/Loyalsoldier/geoip/releases/tag/202611050019). Gradle excludes all three entries from the AAR used to build the APK. The original AAR remains a private build input. When routing is enabled, the app supplies default GeoIP and GeoSite files in its private directory from the packaged V2Fly releases and preserves existing copies.

The [Loyalsoldier GeoIP documentation](https://github.com/Loyalsoldier/geoip) describes MaxMind GeoLite2 Country as its default country input, with additional sources and modifications. Redistribution of these upstream inputs was not established. This is why the files are excluded from the distribution.

## Candidate replacements and migration

[V2Fly GeoIP](https://github.com/v2fly/geoip) states CC-BY-SA-4.0 for its project and attributes its free IP geolocation data to DB-IP under CC-BY-4.0. Its generator supports multiple input providers, so identify the exact input and release before relying on that statement for a particular binary. [V2Fly domain-list-community](https://github.com/v2fly/domain-list-community) has an MIT license, but each chosen snapshot and its source inputs must likewise be reviewed. These are candidates, not verified drop-in replacements for the four app databases.

The built-in rule selects the attributed V2Fly databases directly. On upgrades, the installer preserves nonempty copies of the defaults and previously downloaded data, so older imported rules retain their local inputs. On clean installs, default imports use V2Fly. An import can specify its own dataset URLs if it needs a different category list.

An inspection of the protobuf entry names in the four existing files found 2 categories in Happ GeoIP (`private`, `ru`), 26 in Happ GeoSite (including all seven categories referenced by the built-in preset), 253 in v2ray GeoIP, and 1,556 in v2ray GeoSite. The latter includes the other six built-in site categories but does not contain `ru-available-only-inside`. V2Fly's source has, for example, [bank](https://github.com/v2fly/domain-list-community/blob/master/data/category-bank-ru) and [e-commerce](https://github.com/v2fly/domain-list-community/blob/master/data/category-ecommerce-ru) categories. A separate [runetfreedom list](https://github.com/runetfreedom/russia-domains-list/blob/main/ru-available-only-inside) provides the missing name, but its redistribution terms were not established. Names alone do not establish identical contents or behavior. A minimal replacement could also remove categories selected in existing custom presets.

Concrete candidate check: [domain-list-community 20261124090120 dlc.dat](https://github.com/v2fly/domain-list-community/releases/tag/20261124090120) has SHA-256 `76d5685565ee572e6446130a0512f2bc393c4a5bd7addf2c841e738822d1c565` and 1,557 protobuf category names. It contains all 1,556 names in the current v2ray GeoSite, but lacks Happ's `ru-available-only-inside` and `win-spy`. [V2Fly GeoIP 202610050438](https://github.com/v2fly/geoip/releases/tag/202610050438) `geoip.dat` has SHA-256 `4ff4b2ec242a9e87b296ba27ddb85f8311e8d3e071eaebac5be9231c60e408dd`, 253 names, and contains every category name in both current app GeoIP files. This only checks entry names; the contents, precise input terms and runtime compatibility still need verification before substitution.

Fetching the Happ files from the vendor on first use instead of shipping them would introduce network and offline failure modes, and would not address the other application files or the AAR defaults. The vendor's terms for automated retrieval and use have not been established; this is not yet a verified resolution.

## Flags

The original `svg-country-flags` artwork was replaced with 255 SVGs from [flag-icons 7.5.0](https://github.com/lipis/flag-icons/tree/7aa5b2bdddd570ece62c812c0cb588ccdc099e2e). The new files match the 4:3 source assets in the published package. The app includes the MIT license and source notice.
