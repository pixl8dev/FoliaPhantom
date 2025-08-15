/**
 * This transformer modifies calls to the Bukkit world generation API to ensure they are executed on a dedicated thread.
 * This is necessary for thread safety and performance on Folia servers.
 */
package summer.foliaPhantom.transformers;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import summer.foliaPhantom.patchers.FoliaPatcher;

import java.util.logging.Logger;

public class WorldGenClassTransformer extends ClassTransformer {

    private static final String WORLD_CREATOR_CLASS = "org/bukkit/WorldCreator";
    private static final String PLUGIN_CLASS = "org/bukkit/plugin/Plugin";
    private static final String PATCHER_CLASS = FoliaPatcher.class.getName().replace('.', '/');

    public WorldGenClassTransformer(Logger logger) {
        super(PLUGIN_CLASS, PATCHER_CLASS); // We are transforming the Plugin class to intercept world creation
    }

    @Override
    protected ClassVisitor createVisitor(org.objectweb.asm.ClassWriter classWriter) {
        return new WorldGenVisitor(classWriter);
    }

    private static class WorldGenVisitor extends ClassVisitor {
        public WorldGenVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodRedirector(mv);
        }
    }

    private static class MethodRedirector extends MethodVisitor {
        public MethodRedirector(MethodVisitor methodVisitor) {
            super(Opcodes.ASM9, methodVisitor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // Intercept calls to Bukkit.createWorld(WorldCreator)
            if (opcode == Opcodes.INVOKESTATIC && owner.equals("org/bukkit/Bukkit") && name.equals("createWorld") && descriptor.equals("(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER_CLASS, "createWorld", descriptor, false);
            }
            // Intercept calls to Plugin.getDefaultWorldGenerator(String, String)
            else if (opcode == Opcodes.INVOKEINTERFACE && owner.equals(PLUGIN_CLASS) && name.equals("getDefaultWorldGenerator") && descriptor.equals("(Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/generator/ChunkGenerator;")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER_CLASS, "getDefaultWorldGenerator", "(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/generator/ChunkGenerator;", false);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }
}
