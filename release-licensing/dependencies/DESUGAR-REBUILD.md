# desugar_jdk_libs 2.1.5 source verification

The Google Maven 2.1.5 binary was compared against a rebuild from
[`73170c345e6a762fc6a1f0301bb15218850023ef`](https://github.com/google/desugar_jdk_libs/tree/73170c345e6a762fc6a1f0301bb15218850023ef).
This is the snapshot in `sources/desugar/desugar-73170c3.zip`.

The Bazel 6.5.0 build used Azul Zulu 11.0.15 and the local Android SDK.
On Windows the SDK was exposed at a path without Cyrillic characters.
The temporary checkout's `jdk11/src/BUILD` packaging step used Python
`zipfile` in place of the missing Info-ZIP utility. It changed only how
the generated files were archived. The Bazel target was
`//:desugar_jdk_libs`, producing `bazel-bin/desugar_jdk_libs.jar`.

The intermediate JAR contained all 1,459 published class names and four
extra helper classes. Of the published classes, 1,431 were already
byte-identical. Google's [publishing script](https://r8.googlesource.com/r8/+/c38abd2564c3d05f243ec922274a8a621303d56c/tools/archive_desugar_jdk_libs.py)
performs another transformation after the Bazel build. The checks below
apply the method-call transformations described in the upstream
[R8 undesugarer](https://r8.googlesource.com/r8/+/c06ab1d37c54d61924eea974e26fe8a1f6382326/src/test/testbase/java/com/android/tools/r8/desugar/desugaredlibrary/jdk11/DesugaredLibraryJDK11Undesugarer.java)
to compare class contents. They do not try to reproduce ZIP timestamps,
ordering, or the omitted helper classes.

```sh
bazel build //:desugar_jdk_libs
java -cp asm-9.0.jar CheckDesugar.java desugar_jdk_libs-2.1.5.jar bazel-bin/desugar_jdk_libs.jar
```

The check using ASM 9.0 and Java 17 found **1,459 byte-identical published
class files** after the transformation, with no missing or differing class.
The entire source snapshot, file notices and underlying GPL 2.0 and
Classpath Exception texts are included in the license materials. The APK
also contains the collected copyright and license headers for `java.time`,
the package retained by L8 in this release.
