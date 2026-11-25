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
