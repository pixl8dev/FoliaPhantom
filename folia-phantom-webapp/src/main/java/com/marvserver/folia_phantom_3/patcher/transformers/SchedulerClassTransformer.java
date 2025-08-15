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

public class SchedulerClassTransformer implements ClassTransformer {
    private final Logger logger = LoggerFactory.getLogger(SchedulerClassTransformer.class);

    public SchedulerClassTransformer() {
        // No-arg constructor
    }

    @Override
    public byte[] transform(byte[] originalBytes) {
        String className = "Unknown";
        try {
            ClassReader cr = new ClassReader(originalBytes);
            className = cr.getClassName();
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new SchedulerClassVisitor(cw);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            return cw.toByteArray();
        } catch (Exception e) {
            logger.warn("[Phantom-extra] Failed to transform class {}. Returning original bytes.", className, e);
            return originalBytes;
        }
    }

    private static class SchedulerClassVisitor extends ClassVisitor {
        public SchedulerClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            return new SchedulerMethodVisitor(super.visitMethod(access, name, desc, sig, ex));
        }
    }

    private static class SchedulerMethodVisitor extends MethodVisitor {
        private static final String PATCHER_INTERNAL_NAME = Type.getInternalName(FoliaPatcher.class);

        public SchedulerMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            String methodKey = name + desc;

            if (FoliaPatcher.REPLACEMENT_MAP.containsKey(methodKey)) {
                if ("org/bukkit/scheduler/BukkitScheduler".equals(owner) && opcode == Opcodes.INVOKEINTERFACE) {
                    String newDesc = "(Lorg/bukkit/scheduler/BukkitScheduler;" + desc.substring(1);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER_INTERNAL_NAME, name, newDesc, false);
                    return;
                }

                if ("org/bukkit/scheduler/BukkitRunnable".equals(owner) && opcode == Opcodes.INVOKEVIRTUAL) {
                    String newName = name + "_onRunnable";
                    String newDesc = "(Lorg/bukkit/scheduler/BukkitRunnable;" + desc.substring(1);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER_INTERNAL_NAME, newName, newDesc, false);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
