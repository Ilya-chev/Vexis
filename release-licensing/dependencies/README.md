# Dependency licensing evidence — incomplete release audit

This directory is an evidence bundle, not a statement that the APK is ready
for distribution. Inspect the final APK and obtain the exact corresponding
sources and applicable notices before release. Entries explicitly marked as
matching Maven Central were independently checked by SHA-256 on 2026-11-25.

## ZXing core

- Local input `app/libs/zxing-core-3.5.3.jar`, SHA-256
  `8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82`.
  Its embedded Maven metadata identifies `com.google.zxing:core:3.5.3`.
- Upstream license: Apache-2.0, see `Apache-2.0.txt` and
  https://github.com/zxing/zxing/blob/zxing-3.5.3/LICENSE . The upstream
  release NOTICE is reproduced in `ZXing-3.5.3-NOTICE.txt` from
  https://github.com/zxing/zxing/blob/zxing-3.5.3/NOTICE .
- The local JAR matches the [published Maven artifact](https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar) by SHA-256.

## AndroidSVG

- Local input `app/libs/androidsvg-1.4.aar`, SHA-256
  `02a5b08a2b35d2d58eb2eaca9d84ac00fb341da725fdbd653ea3ed130437e95a`.
- Upstream identifies its license as Apache-2.0, see `Apache-2.0.txt` and
  https://github.com/BigBadaboom/androidsvg/blob/master/LICENSE .
- The local AAR matches the [published Maven artifact](https://repo.maven.apache.org/maven2/com/caverock/androidsvg-aar/1.4/androidsvg-aar-1.4.aar) by SHA-256.

## AmneziaWG Android

- Gradle declares `com.zaneschepke:amneziawg-android:2.3.7@aar`. Cached AAR
  SHA-256 `b08c96190d34244626cad1db79618f1658481974a829a2c42527d7a6989c3429`
  matches both the cached Gradle module metadata included here and the
  [published AAR on Maven Central](https://repo.maven.apache.org/maven2/com/zaneschepke/amneziawg-android/2.3.7/amneziawg-android-2.3.7.aar).
  The cached POM declares Apache-2.0 for the library; see `Apache-2.0.txt`.
  The upstream [v2.3.7 source tag](https://github.com/zaneschepke/amneziawg-android/tree/v2.3.7) points to commit `ffb71fb3bcd15ac011ad7cfbe05f76d150fc37f9`.
- The AAR contains native `libam-go.so`, `libam-quick.so`, and `libam.so`
  for arm64-v8a, armeabi-v7a, x86 and x86_64. The source tag's
  [submodule configuration](https://github.com/zaneschepke/amneziawg-android/blob/v2.3.7/.gitmodules)
  points to [amneziawg-tools at `5d6179a`](https://github.com/amnezia-vpn/amneziawg-tools/tree/5d6179a6d0842e98dfb349c28cf1bd8e4b9d1079)
  and [termux-elf-cleaner at `7efc050`](https://github.com/termux/termux-elf-cleaner/tree/7efc05090675ec6161b7def862728086a26c3b1f).
  The arm64 `libam-go.so` Go build information lists `amneziawg-go` v0.2.16,
  `wireproxy-awg` v1.0.12, `go-socks5` v0.1.0 and gVisor at `1f7806d17489`,
  among other modules. Check replacements, exact native build inputs and the
  applicable notices before distributing. The cached Java source jar alone
  does not cover all native code.
- Published metadata lists runtime dependencies `androidx.collection:collection:1.5.0`,
  `com.zaneschepke:hevtunnel:1.0.1`, `androidx.annotation:annotation:1.9.1`,
  `com.getkeepsafe.relinker:relinker:1.4.5`,
  `com.squareup.okhttp3:okhttp-dnsoverhttps:5.3.0` and
  `com.squareup.okhttp3:okhttp:5.3.0`. These are metadata declarations, not
  proof that they are resolved or shipped: the project's explicit `@aar`
  declaration is significant. Verify the actual resolved release graph and
  APK contents, including transitive and native dependencies, and collect
  their exact source, licenses and notices for anything included.

## desugar_jdk_libs

- `com.android.tools:desugar_jdk_libs:2.1.5` is configured for core library
  desugaring. Its cached POM (included) identifies GPL version 2 with the
  Classpath Exception, and declares `desugar_jdk_libs_configuration:2.1.5`.
  See `GPL-2.0.txt`, `Classpath-exception.txt` and upstream
  https://github.com/google/desugar_jdk_libs/blob/master/LICENSE .
  The exception applies only where the relevant source file says so.
- Upstream lists additional independent licenses and file-specific notices:
  https://github.com/google/desugar_jdk_libs/blob/master/ADDITIONAL_LICENSE_INFO .
  The archived source snapshot and retained `java.time` notices were verified
  for this release. See [the rebuild and bytecode comparison](DESUGAR-REBUILD.md).

## Outstanding before distribution

Identify exact native source
revisions and licenses; obtain corresponding sources and third-party notices;
review final APK and resolved dependency graph; provide all applicable license
texts and notices with the distribution. This inventory alone does not satisfy
those requirements.

Verification update: `:app:dependencies --configuration releaseRuntimeClasspath`
reported only `com.zaneschepke:amneziawg-android:2.3.7` as a Maven module,
without children. File dependencies do not appear as Maven modules and
`coreLibraryDesugaring` is a separate configuration. This resolves the stated
Maven runtime graph for this build but does not establish the native components
or prove final APK contents.

## Country flag artwork

The current app ships the MIT-licensed 4:3 SVGs from flag-icons 7.5.0. The previous artwork described in the historical paragraph below was replaced. See `app/src/main/assets/licenses/Country-flags-SOURCE.txt` for the released package and checksum.

The 255 files under `app/src/main/assets/flags` were compared by SHA-256 to `flag-source/country-flags-main/svg`: 254 are byte-identical; `fr.svg` has changed red/blue colours and an added outline. The local `flags-source.zip` has SHA-256 `1780ad98c95d9436620967d4ab3e88919efd0a0deba8e773d2eeb0d4f5c5fd88`. Its package identifies version 1.2.10 and `PD`, and its README identifies https://github.com/hampusborgos/country-flags as source and states that files came from Wikimedia Commons and flags are public domain, with possible other usage restrictions. A source notice is included at `app/src/main/assets/licenses/Country-flags-SOURCE.txt`. All 255 SVGs, README and package metadata in the local archive match Git blob IDs at upstream commit `c09927e63705529bbf59ca6684cd9b23225dddad` (2026-03-10); the non-truncated GitHub tree response is saved as `country-flags-main-tree.json`. Individual Wikimedia source pages and artwork rights have not been verified. The general upstream claim does not resolve every image or jurisdiction. Verify individual provenance and applicable rights before distribution.

## Additional native evidence

Vexis excludes the AAR's root-only `libam.so` and `libam-quick.so` from the APK. Its two userspace backends use `libam-go.so`. For all four shipped ABIs, that library's ELF `DT_NEEDED` entries name Android platform libraries `liblog.so`, `libdl.so`, and `libc.so` only. The original Maven AAR remains a build-time input.

The exact amneziawg-tools gitlink's upstream `COPYING` is GPL version 2: https://github.com/amnezia-vpn/amneziawg-tools/blob/5d6179a6d0842e98dfb349c28cf1bd8e4b9d1079/COPYING . A GPL-2.0 text is already included in the app assets. This identifies a source-tree license, not a complete accounting of shipped native inputs or compliance with corresponding-source requirements. The actual source and build scripts used for all native libraries, dependency replacements and notices remain to be verified.

The upstream desugar changelog identifies 2.1.5 as prepared on 2025-02-14; commit `73170c345e6a762fc6a1f0301bb15218850023ef` is titled "Prepare for jdk-11 based desugared library version 2.1.5" (https://github.com/google/desugar_jdk_libs/commit/73170c3) and contains `VERSION_JDK11_NIO.txt` with `2.1.5`. Google Maven's binary returned `Last-Modified: Tue, 25 Feb 2025 10:10:56 GMT`. The next commit listed upstream is dated March 8. The source snapshot was subsequently rebuilt and checked against all published class files.

## Embedded Go metadata and packaged native license files

`AWG-arm64-go-build-info.txt` preserves the full metadata extracted from the debug merged arm64 `libam-go.so`, including its SHA-256, module hashes and build settings. Three apparent upstream modules are replaced: amneziawg-go by `github.com/wgtunnel/amneziawg-go` at `0569d899c9bf`, wireproxy-awg by `github.com/wgtunnel/wireproxy-awg` at `ff4200f20ff2`, and go-socks5 by `github.com/wgtunnel/go-socks5` at `86f8d93b9534`. Do not substitute the unreplaced upstream versions as corresponding sources. The binary contains the Go version string `go1.24.4`.

The matching Go proxy archive of the go-socks5 replacement is retained as `wgtunnel-go-socks5-source.zip`; its computed Go module h1 digest matches embedded metadata (`hpF1TN8okGWv9j4lwPcWVMNDeu+1o52HB6NcEYw0WPs=`). Its LICENSE and the other identified module root licenses and available Go PATENTS files are packaged in `app/src/main/assets/licenses`; see `AWG-native-SOURCES.txt` for version-specific URLs. The remaining eleven module archives were also downloaded from Go proxy; every computed Go module h1 matches arm64 embedded metadata and each packaged root license/PATENTS file matches its archive. The release APK contained identical Go dependency metadata in all four ABIs. Individual source-file licenses and notices, C/toolchain dependencies and exact build reproduction remain open. `amneziawg-tools` file `src/wg.c`, for example, identifies `GPL-2.0 OR MIT`, so its root COPYING alone is not a complete file-level assessment.

The candidate desugar commit's `ADDITIONAL_LICENSE_INFO` is retained here for audit, not packaged; it warns that independent programs and file-specific notices may apply. It does not prove which notices apply to code in the final APK.

The Go 1.24.4 source archive is available as `sources/awg/go1.24.4.src.tar.gz` in the release materials. It matches the Go project's published SHA-256 `5a86a83a31f9fa81490b8c5420ac384fd3d95a3e71fba665c7b3f95d1dfef2b4`. The final APK retains `libam-go.so` for four ABIs and excludes both root-only AWG tools.


## Further verification of the release build inspected

The unsigned release APK inspected had SHA-256 `568737df19077827679ebc290184867a47db44ffcb6d39b43c9eaa613e130002`. Its four `libam-go.so` files contain identical dependency and replacement lists. The three replacement source archives and nine other Go proxy archives have h1 digests matching the embedded metadata. `go-modules-manifest.txt` lists the nine archives, their SHA-256 and discovered license files; the three replacement archive SHA-256 values are respectively `850ba15ac1082039ecab06a7a30ff70cc3d96578a3abf589e93a392a17fccc6a`, `1e573e28f34618b47d1f6ff8e11e489c3327650219ecc2fff690d41eedf9f395`, `c3df6d2c2fd941766052b3f87cef63c6d9c4bada938ecf4ddc37f81b47b32dc6`. These are source inputs, not proof of reproducible corresponding builds. The official v2.3.7 Android source `go.mod` contains the same replacements and Go 1.24.4, though this alone does not prove the published AAR was built from that precise checkout. Source snapshots for Android, tools and the build-time termux cleaner are retained with SHA-256 `02f2dfaa5c5d410f950b94ed9c481762c53ebda3de73a7e5961460e18c3c0c7d`, `b5ee094c9541e9915d765a5f1f9258b00122a0b67a2e522c4105505738fddc7b`, `18f3fcb1a70637aaf8b62c6cae7814c1e08cc154c46eeeedd3a660d417e94d27`. GitHub ZIP downloads do not populate git submodules. Cleaner COPYING is GPL-3.0; it is a build tool and its inclusion in shipped runtime is not established.

The cached desugar 2.1.5 JAR matches Google's published binary by SHA-256 `d8044befae095781b9a80bf1faa92edc30382d75d437476784c1bf991598a976`; its conventional `-sources.jar` URL returned 404. Source snapshot commit `73170c345e6a762fc6a1f0301bb15218850023ef` is retained locally as `desugar-candidate-73170c3.zip`, SHA-256 `cf0b48cb046b6517ed8e2a816c125116a907e13e48e341882ba6f4016a155d9f`. Rebuilding this snapshot and applying the documented R8 method-call rewrites reproduced the contents of all 1,459 published class files. The inspected release APK's `classes2.dex` matches the L8 output `classes1000.dex`, SHA-256 `54472d5c988071fef63eac3536f28fec663715a4904da61139a8871d444f5e34`; its 127 class descriptors are saved in `desugar-release-l8-classes.txt`. The L8 keep rules reference `j$.time.Duration`, `Instant`, and `temporal.Temporal`.


## Desugar file notices and reconstruction assessment

For the inspected release, all 127 L8 class descriptors are under `j$.time`. The candidate source snapshot contains 389 Java files under JDK 11 `java/time` in its multiple source trees. Their 85 distinct complete pre-package headers, with paths and upstream text, are packaged conservatively in `app/src/main/assets/licenses/Desugar-java-time-source-headers.txt`; this includes files that may not have survived L8. For example, candidate `Duration`, `Instant`, and `Temporal` retain Oracle GPL-2-only-with-Classpath notices and earlier Stephen Colebourne and Michael Nascimento Santos notices including binary redistribution conditions. Existing `GPL-2.0.txt`, `Classpath-exception.txt` and `Apache-2.0.txt` supply full license texts. The source headers do not assert that every listed file is shipped, and do not by themselves prove exact binary-to-source correspondence.

The source snapshot was rebuilt with Bazel 6.5.0. See [DESUGAR-REBUILD.md](DESUGAR-REBUILD.md) for the Windows packaging adjustment, SDK setup and post-Bazel verification steps.
