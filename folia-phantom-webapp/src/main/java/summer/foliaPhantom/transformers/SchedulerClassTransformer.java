/**
 * This transformer modifies calls to the Bukkit scheduler to use the global scheduler in Folia.
 * This is necessary for thread safety and performance on Folia servers.
 */
package summer.foliaPhantom.transformers;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import summer.foliaPhantom.patchers.FoliaPatcher;

import java.util.logging.Logger;

public class SchedulerClassTransformer extends ClassTransformer {
    private static final String SCHEDULER_CLASS = "org/bukkit/scheduler/BukkitScheduler";
    private static final String PATCHER_CLASS = FoliaPatcher.class.getName().replace('.', '/');

    public SchedulerClassTransformer(Logger logger) {
        super(SCHEDULER_CLASS, PATCHER_CLASS);
    }

    @Override
    protected ClassVisitor createVisitor(org.objectweb.asm.ClassWriter classWriter) {
        return new SchedulerVisitor(classWriter);
    }

    private static class SchedulerVisitor extends ClassVisitor {
        public SchedulerVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (FoliaPatcher.REPLACEMENT_MAP.containsKey(name + descriptor)) {
                return new MethodRedirector(mv, name, descriptor, PATCHER_CLASS);
            }
            return mv;
        }
    }

    private static class MethodRedirector extends MethodVisitor {
        private final String newMethodName;
        private final String newMethodDescriptor;
        private final String newClassName;

        public MethodRedirector(MethodVisitor methodVisitor, String methodName, String methodDescriptor, String newClassName) {
            super(Opcodes.ASM9, methodVisitor);
            this.newMethodName = methodName;
            this.newMethodDescriptor = methodDescriptor;
            this.newClassName = newClassName;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKEINTERFACE && owner.equals(SCHEDULER_CLASS) && (name + descriptor).equals(newMethodName + newMethodDescriptor)) {
                // Replace the method call with a call to our patcher class
                super.visitMethodInsn(Opcodes.INVOKESTATIC, newClassName, name, descriptor, false);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
}
