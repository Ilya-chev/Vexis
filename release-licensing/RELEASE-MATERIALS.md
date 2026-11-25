# Vexis release materials

- `LICENSE` contains the MIT License for Vexis's own code.
- `licenses/` contains the notices that accompany the APK.
- `THIRD-PARTY.md` is the component index and links to the provenance records.
- `sources/Vexis-Xray-sources.zip` contains the AndroidLibXrayLite source,
  the upstream Java bindings source JAR, build instructions, Go 1.27.3,
  46 verified dependencies identified in the binary and six more Go modules
  declared by the wrapper.
- `sources/awg/` contains the pinned AmneziaWG Android source, native build
  helpers, the 12 verified Go dependency archives and Go 1.24.4. See
  [AWG build instructions](release-licensing/dependencies/AWG-REBUILD.md)
  and the [supplemental DNS and gVisor notices](release-licensing/dependencies/AWG-notice-audit.md).
- `sources/desugar/` contains the source snapshot for 2.1.5. The rebuild and
  comparison verify all 1,459 classes in the Google Maven artifact.
  See [desugar source verification](release-licensing/dependencies/DESUGAR-REBUILD.md).
- `SHA256SUMS.txt` records the SHA-256 of every entry in the archive.

Keep the matching Vexis relink archive next to these materials and the
APK for the same version. It contains the compiled application classes,
resources and build files needed to substitute the library. The relink
archive has its own instructions and checksums. The different-library VPN
test is recorded in the [Xray audit](release-licensing/XRAY.md).

For Vexis 1.0, the final relink kit was used to rebuild the unsigned APK
on JDK 17 with the original filtered Xray AAR. Each of the 359 APK entries
matched the primary build by SHA-256 of the entry contents.
