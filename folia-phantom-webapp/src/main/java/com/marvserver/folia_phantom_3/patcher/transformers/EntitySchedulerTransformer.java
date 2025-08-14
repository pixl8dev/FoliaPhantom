package com.marvserver.folia_phantom_3.patcher.transformers;

import com.marvserver.folia_phantom_3.patcher.patchers.FoliaPatcher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class EntitySchedulerTransformer implements ClassTransformer {

    private final Logger logger = LoggerFactory.getLogger(EntitySchedulerTransformer.class);

    public EntitySchedulerTransformer() {
        // No-arg constructor
    }

    @Override
    public byte[] transform(byte[] originalBytes) {
        String className = "Unknown";
        try {
            ClassReader cr = new ClassReader(originalBytes);
            className = cr.getClassName();
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassVisitor cv = new EntitySchedulerClassVisitor(cw);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            return cw.toByteArray();
        } catch (Exception e) {
            logger.warn("[Phantom-extra] Failed to transform class {} for EntityScheduler. Returning original bytes.", className, e);
            return originalBytes;
        }
    }

    private static class EntitySchedulerClassVisitor extends ClassVisitor {
        public EntitySchedulerClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            Type[] argTypes = Type.getArgumentTypes(desc);

            if ((access & Opcodes.ACC_PUBLIC) != 0 && (access & Opcodes.ACC_STATIC) == 0 &&
                argTypes.length == 1 && Type.getReturnType(desc).equals(Type.VOID_TYPE)) {

                String eventType = argTypes[0].getInternalName();
                Map<String, String[]> eventMap = new HashMap<>();
                eventMap.put("org/bukkit/event/player/PlayerEvent", new String[]{"getPlayer", "()Lorg/bukkit/entity/Player;"});
                eventMap.put("org/bukkit/event/entity/EntityEvent", new String[]{"getEntity", "()Lorg/bukkit/entity/Entity;"});

                for (Map.Entry<String, String[]> entry : eventMap.entrySet()) {
                    if (eventType.contains(entry.getKey().substring(15))) { // Heuristic check
                        return new EntitySchedulerMethodVisitor(mv, access, name, desc, eventType, entry.getValue()[0], entry.getValue()[1]);
                    }
                }
            }
            return mv;
        }
    }

    private static class EntitySchedulerMethodVisitor extends AdviceAdapter {
        private final String eventTypeInternalName;
        private final String entityGetterName;
        private final String entityGetterDesc;
        private static final String PATCHER_INTERNAL_NAME = Type.getInternalName(FoliaPatcher.class);

        protected EntitySchedulerMethodVisitor(MethodVisitor mv, int access, String name, String desc, String eventType, String getterName, String getterDesc) {
            super(Opcodes.ASM9, mv, access, name, desc);
            this.eventTypeInternalName = eventType;
            this.entityGetterName = getterName;
            this.entityGetterDesc = getterDesc;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if ("org/bukkit/scheduler/BukkitScheduler".equals(owner) && opcode == Opcodes.INVOKEINTERFACE &&
                FoliaPatcher.REPLACEMENT_MAP.containsKey(name + desc) &&
                !name.contains("Async")) {

                Type[] argTypes = Type.getArgumentTypes(desc);
                int[] localVars = new int[argTypes.length];
                for (int i = argTypes.length - 1; i >= 0; i--) {
                    localVars[i] = newLocal(argTypes[i]);
                    storeLocal(localVars[i]);
                }

                pop();

                loadArg(0);
                visitMethodInsn(Opcodes.INVOKEVIRTUAL, this.eventTypeInternalName, this.entityGetterName, this.entityGetterDesc, false);

                for (int localVar : localVars) {
                    loadLocal(localVar);
                }

                String newPatcherMethodName = "folia_" + name;
                String newPatcherDesc = "(Lorg/bukkit/entity/Entity;" + desc.substring(1);

                super.visitMethodInsn(Opcodes.INVOKESTATIC, PATCHER_INTERNAL_NAME, newPatcherMethodName, newPatcherDesc, false);
            } else {
                super.visitMethodInsn(opcode, owner, name, desc, isInterface);
            }
        }
    }
}
