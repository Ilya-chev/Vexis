# AWG companion notices: DNS and gVisor

These supplemental materials accompany Vexis 1.0 and its signed APK. They
preserve upstream notices conservatively; their presence does not establish
which individual source files were linked or complete the entire audit.

## miekg/dns v1.1.68

`AWG-dns-COPYRIGHT.txt` is the byte-for-byte `COPYRIGHT` file from the
retained Go proxy module archive at v1.1.68, which has the module h1 recorded
in the shipped binary. It names the Go Authors, Miek Gieben and CloudFlare.
The version's full BSD 3-Clause LICENSE is already included as
`licenses/AWG-dns-LICENSE.txt`. Its binary-redistribution condition calls
for the copyright notice, conditions and disclaimer to be reproduced in the
documentation or other materials. The separate COPYRIGHT is included here
without asserting all listed source code is present in the executable.
Upstream: https://github.com/miekg/dns/blob/v1.1.68/COPYRIGHT .

## gVisor 1f7806d17489

The retained exact module snapshot is
`v0.0.0-20231202080848-1f7806d17489`, commit
`1f7806d17489620e45d46c33933cf8f7010a1436`. Its root LICENSE,
already provided in `licenses/AWG-gvisor-LICENSE.txt`, contains Apache-2.0
and a separately marked MIT permission notice, but no BSD text. The source
archive itself retains the original headers. `AWG-gvisor-BSD-headers.txt`
collects all 24 original source headers in that archive that explicitly say
BSD-style, including their respective copyright statements. Inclusion in
this collection does not prove inclusion in the binary.

Upstream later addressed the discrepancy: commit
[e7720fd214e51f1a732e987179a2fa6da139c2c1](https://github.com/gvisor/gvisor/commit/e7720fd214e51f1a732e987179a2fa6da139c2c1)
added a BSD notice to the root LICENSE and updated headers of the same files,
including `pkg/hostarch/hostarch.go` and `pkg/sync/sync.go`, to reference
that license or Google's BSD license URL. `AWG-gvisor-2025-LICENSE.txt` is
the complete, unmodified LICENSE from that later commit, included as
upstream clarification alongside the original, not represented as the
original 2023 file. Its BSD text requires preserving copyright, conditions
and disclaimer for source redistribution and reproducing them with binary
distribution, and restricts use of Google Inc. and contributor names for
endorsement. The full upstream terms and disclaimer are in that file.

The pinned snapshot and all its module source are already retained. The
remaining uncertainty concerns exact compiled file selection and a full
file-by-file notice review; it is not a finding that corresponding source
is missing, nor proof that independent byte reproduction is required by the
identified permissive licenses. See `AWG-REBUILD.md` for provenance limits.
