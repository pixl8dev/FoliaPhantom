/**
 * This transformer wraps calls to thread-unsafe Bukkit API methods to ensure they are executed on the main thread.
 * This is necessary for thread safety on Folia servers.
 */
package summer.foliaPhantom.transformers;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import summer.foliaPhantom.patchers.FoliaPatcher;

import java.util.logging.Logger;

public class ThreadSafetyTransformer extends ClassTransformer {
    private static final String BLOCK_CLASS = "org/bukkit/block/Block";
    private static final String PATCHER_CLASS = FoliaPatcher.class.getName().replace('.', '/');

    public ThreadSafetyTransformer(Logger logger) {
        super(BLOCK_CLASS, PATCHER_CLASS);
    }

    @Override
    protected ClassVisitor createVisitor(org.objectweb.asm.ClassWriter classWriter) {
        return new ThreadSafetyVisitor(classWriter);
    }

    private static class ThreadSafetyVisitor extends ClassVisitor {
        public ThreadSafetyVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (FoliaPatcher.UNSAFE_METHOD_MAP.containsKey(name + descriptor)) {
                return new MethodRedirector(mv, name, descriptor, PATCHER_CLASS, FoliaPatcher.UNSAFE_METHOD_MAP.get(name + descriptor));
            }
            return mv;
        }
    }

    private static class MethodRedirector extends MethodVisitor {
        private final String originalMethodName;
        private final String originalMethodDescriptor;
        private final String patcherClassName;
        private final String patcherMethodName;

        public MethodRedirector(MethodVisitor methodVisitor, String originalMethodName, String originalMethodDescriptor, String patcherClassName, String patcherMethodName) {
            super(Opcodes.ASM9, methodVisitor);
            this.originalMethodName = originalMethodName;
            this.originalMethodDescriptor = originalMethodDescriptor;
            this.patcherClassName = patcherClassName;
            this.patcherMethodName = patcherMethodName;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKEINTERFACE && owner.equals(BLOCK_CLASS) && (name + descriptor).equals(originalMethodName + originalMethodDescriptor)) {
                // Replace the method call with a call to our patcher class
                super.visitMethodInsn(Opcodes.INVOKESTATIC, patcherClassName, patcherMethodName, descriptor, false);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
}
