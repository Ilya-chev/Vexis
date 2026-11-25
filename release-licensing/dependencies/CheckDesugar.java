// Verification implementation of the call rewrites described by Google's R8
// DesugaredLibraryJDK11Undesugarer. See DESUGAR-REBUILD.md for the source link.
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

class CheckDesugar {
    static final Map<String, String> owners = Map.ofEntries(
        Map.entry("java/util/DesugarTimeZone", "java/util/TimeZone"),
        Map.entry("java/lang/DesugarLong", "java/lang/Long"),
        Map.entry("java/lang/DesugarInteger", "java/lang/Integer"),
        Map.entry("java/lang/DesugarDouble", "java/lang/Double"),
        Map.entry("java/util/DesugarArrays", "java/util/Arrays"),
        Map.entry("java/lang/DesugarMath", "java/lang/Math"),
        Map.entry("java/io/DesugarBufferedReader", "java/io/BufferedReader"),
        Map.entry("java/io/DesugarInputStream", "java/io/InputStream"),
        Map.entry("sun/misc/DesugarUnsafe", "jdk/internal/misc/Unsafe"),
        Map.entry("wrapper/adapter/HybridFileSystemProvider", "sun/nio/fs/DefaultFileSystemProvider"),
        Map.entry("wrapper/adapter/HybridFileTypeDetector", "sun/nio/fs/DefaultFileTypeDetector"));

    static byte[] rewrite(byte[] definition) {
        var reader = new ClassReader(definition);
        var writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && owners.containsKey(owner)) {
                            owner = owners.get(owner);
                            String arg = "(L" + owner + ";";
                            if (descriptor.startsWith(arg)) {
                                opcode = Opcodes.INVOKEVIRTUAL;
                                descriptor = "(" + descriptor.substring(arg.length());
                            }
                        }
                        super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    public static void main(String[] args) throws IOException {
        try (var reference = new ZipFile(args[0]); var rebuilt = new ZipFile(args[1])) {
            int matching = 0;
            var differences = new ArrayList<String>();
            for (var entry : Collections.list(reference.entries())) {
                String name = entry.getName();
                if (!name.endsWith(".class")) continue;
                var rebuiltEntry = rebuilt.getEntry(name);
                if (rebuiltEntry == null) {
                    differences.add("Missing: " + name);
                    continue;
                }
                try (var expected = reference.getInputStream(entry); var actual = rebuilt.getInputStream(rebuiltEntry)) {
                    byte[] expectedBytes = expected.readAllBytes();
                    byte[] actualBytes = actual.readAllBytes();
                    if (Arrays.equals(expectedBytes, actualBytes) || Arrays.equals(expectedBytes, rewrite(actualBytes))) matching++;
                    else differences.add(name);
                }
            }
            System.out.println("Matching after method rewrite: " + matching + "; remaining: " + differences.size());
            differences.forEach(System.out::println);
            if (!differences.isEmpty()) System.exit(1);
        }
    }
}
