package com.marvserver.folia_phantom_3.patcher.transformers;

import com.marvserver.folia_phantom_3.patcher.patchers.FoliaPatcher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldGenClassTransformer implements ClassTransformer {
    private final Logger logger = LoggerFactory.getLogger(WorldGenClassTransformer.class);

    public WorldGenClassTransformer() {
        // No-arg constructor
    }

    @Override
    public byte[] transform(byte[] originalBytes) {
        String className = "Unknown";
        try {
            ClassReader cr = new ClassReader(originalBytes);
            className = cr.getClassName();
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new WorldGenClassVisitor(cw);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            return cw.toByteArray();
        } catch (Exception e) {
            logger.warn("[Phantom-extra] Failed to transform class {} for WorldGen. Returning original bytes.", className, e);
            return originalBytes;
        }
    }

    private static class WorldGenClassVisitor extends ClassVisitor {
        public WorldGenClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            return new WorldGenMethodVisitor(super.visitMethod(access, name, desc, sig, ex));
        }
    }

    private static class WorldGenMethodVisitor extends MethodVisitor {
        private static final String PATCHER_INTERNAL_NAME = Type.getInternalName(FoliaPatcher.class);

        public WorldGenMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            String methodKey = name + desc;
            if (FoliaPatcher.REPLACEMENT_MAP.containsKey(methodKey)) {
                if ("createWorld".equals(name) && "(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;".equals(desc) && "org/bukkit/Server".equals(owner)) {
                    super.visitInsn(Opcodes.SWAP);
                    super.visitInsn(Opcodes.POP);
                    String newDesc = "(Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;";
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER_INTERNAL_NAME, name, newDesc, false);
                    return;
                }

                if ("getDefaultWorldGenerator".equals(name) && "(Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/generator/ChunkGenerator;".equals(desc)) {
                    String newDesc = "(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/generator/ChunkGenerator;";
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER_INTERNAL_NAME, name, newDesc, false);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
