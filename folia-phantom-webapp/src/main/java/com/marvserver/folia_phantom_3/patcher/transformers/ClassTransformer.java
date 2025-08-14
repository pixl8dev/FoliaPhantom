package com.marvserver.folia_phantom_3.patcher.transformers;

public interface ClassTransformer {
    byte[] transform(byte[] originalBytes);
}
