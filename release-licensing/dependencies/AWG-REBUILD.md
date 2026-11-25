# AmneziaWG Android 2.3.7 source and build

The distributed APK includes `libam-go.so` and selected classes from the
AmneziaWG Android AAR. The original AAR matches the Maven Central release.
Vexis excludes `libam.so` and `libam-quick.so` from its APK.

The source checkout is `ffb71fb3bcd15ac011ad7cfbe05f76d150fc37f9` in
[amneziawg-android](https://github.com/zaneschepke/amneziawg-android/tree/ffb71fb3bcd15ac011ad7cfbe05f76d150fc37f9).
`amneziawg-android-v2.3.7-source.zip` contains that snapshot, including
the JNI source, `go.mod`, `go.sum`, CMake files, Gradle wrapper and the
`goruntime-boottime-over-monotonic.diff` patch applied to Go. The checkout
uses the submodule references in `.gitmodules`. A recursive clone at the
pinned commit obtains those references, including the helpers used by the
upstream build.

Use Linux, JDK 17, the Android SDK and NDK `28.2.13676358` (`r28c`). Install
the SDK platform requested by the source checkout and CMake, GNU `make`,
`patch`, `flock`, `curl` and `sha256sum`. Build locally without publishing.

```sh
git clone https://github.com/zaneschepke/amneziawg-android.git
cd amneziawg-android
git checkout ffb71fb3bcd15ac011ad7cfbe05f76d150fc37f9
git submodule update --init --recursive
./gradlew :tunnel:assembleRelease
```

`tunnel/tools/libwg-go/Makefile` pins Go 1.24.4 for the native library,
checks the toolchain download, and applies the included runtime patch.
The unmodified Go 1.24.4 source archive is included with these materials.
The 12 dependency archives in this bundle match the Go `h1:` sums in the
distributed libraries. Use `go.mod` and `go.sum` from the pinned checkout
when allowing the normal Go build to obtain modules. The additional
Android and Go source files retain their original license headers.

The resulting AAR should be checked with Vexis before substituting it.
The upstream build recipe, embedded dependency hashes and source revisions
are recorded here. An independent rebuild of the upstream AAR has not been
compared byte for byte to the Maven artifact.

## Signed Vexis 1.0 APK check (2026-11-25)

The inspected `app/release/Vexis-1.0.apk` has SHA-256
`48b1a16928a75c39e76b3b74f5e6d4c6b48e4854cf0f580fd2563c112fe2c0d7`.
Its four `libam-go.so` entry contents are byte-identical to the corresponding
entries of the cached Maven AAR (whose SHA-256 is documented in README.md):

| ABI | libam-go.so SHA-256 |
| --- | --- |
| arm64-v8a | `cc119dbc6140f65d225d597852eb16848d011f09582392f1de422242ac348aa7` |
| armeabi-v7a | `1cba1ca8eb1eb4ed47e25b86c59cfae84b609f9de8e600082d609ada92ead21f` |
| x86 | `64907812fd058fbc842b47966aa1f013d22b35f78626771013ceaf07113ed2ca` |
| x86_64 | `5625357f01cb2b82ada60ab52de164b246d6a436c115a6f087bf585b2a859343` |

Neither `libam.so` nor `libam-quick.so` occurs in that APK. This comparison
links the shipped binaries to the AAR, not independently to its source.
A byte-identical rebuilt `.so` would be strong evidence of correspondence;
a mismatch alone would not establish different source because build paths,
compiler, linker, build IDs, stripping and archive metadata can differ.
A meaningful independent comparison must preserve its build environment,
commands and logs, compare each uncompressed ABI entry, and investigate any
semantic differences, not merely compare whole AAR ZIP hashes.

A preliminary scan of the retained module archives found additional
file-level issues requiring review before claiming a complete notice audit:
`dns.zip` includes a separate `COPYRIGHT` naming Cloudflare in addition to
its root LICENSE (the latter is included in the APK); the gVisor archive
contains files such as `pkg/hostarch/hostarch.go` and `pkg/sync/sync.go`
with gVisor Authors copyright and BSD-style license references while the
archive's root LICENSE is Apache-2.0. A later upstream clarification and the original headers are provided with
the companion materials, as explained in `AWG-notice-audit.md`. The exact
set of compiled files has not been established. A source scan
is not proof that every scanned file is included in the native binary.
The signed APK was not changed by this audit.
