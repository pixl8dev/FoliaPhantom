package summer.foliaPhantom.transformers;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * This interface defines a contract for classes that transform the bytecode of a Java class.
 * This is a core part of the patching process, and is necessary to make plugins compatible with Folia.
 * The use of bytecode manipulation can sometimes be flagged as suspicious by antivirus software,
 * but in this case, it is a legitimate and necessary part of the patching process.
 */
public abstract class ClassTransformer {

    protected final String className;
    protected final String patcherClassName;

    public ClassTransformer(String className, String patcherClassName) {
        this.className = className;
        this.patcherClassName = patcherClassName;
    }

    public byte[] transform(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        if (!cr.getClassName().equals(this.className.replace('.', '/'))) {
            return classBytes;
        }
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(createVisitor(cw), ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    protected abstract org.objectweb.asm.ClassVisitor createVisitor(org.objectweb.asm.ClassWriter classWriter);
}
