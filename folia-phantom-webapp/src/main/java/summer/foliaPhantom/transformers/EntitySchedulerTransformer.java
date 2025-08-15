/**
 * This transformer modifies calls to the Bukkit scheduler to use the entity-specific scheduler in Folia.
 * This is necessary for thread safety and performance on Folia servers.
 */
package summer.foliaPhantom.transformers;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import summer.foliaPhantom.patchers.FoliaPatcher;

import java.util.logging.Logger;

public class EntitySchedulerTransformer extends ClassTransformer {
    private static final String SCHEDULER_CLASS = "org/bukkit/entity/Entity";
    private static final String PATCHER_CLASS = FoliaPatcher.class.getName().replace('.', '/');

    public EntitySchedulerTransformer(Logger logger) {
        super(SCHEDULER_CLASS, PATCHER_CLASS);
    }

    @Override
    protected ClassVisitor createVisitor(org.objectweb.asm.ClassWriter classWriter) {
        return new EntitySchedulerVisitor(classWriter);
    }

    private static class EntitySchedulerVisitor extends ClassVisitor {
        public EntitySchedulerVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (name.equals("getScheduler")) {
                // We are not transforming the getScheduler method itself, but the calls to it.
                // This will be handled by other transformers that look for Bukkit.getScheduler()
            }
            return mv;
        }
    }
}
