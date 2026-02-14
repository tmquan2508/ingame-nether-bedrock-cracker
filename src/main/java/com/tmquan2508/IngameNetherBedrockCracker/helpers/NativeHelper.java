package com.tmquan2508.IngameNetherBedrockCracker.helpers;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class NativeHelper {

    private final Arena arena;
    private final MethodHandles.Lookup lookup;

    public NativeHelper(Arena arena) {
        this.arena = arena;
        this.lookup = MethodHandles.lookup();
    }

    public MemorySegment createUpcall(Class<?> targetClass, String methodName, MethodType methodType,
            FunctionDescriptor nativeDescriptor) {
        try {
            MethodHandle mh = lookup.findStatic(targetClass, methodName, methodType);
            return Linker.nativeLinker().upcallStub(mh, nativeDescriptor, arena);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create native upcall for " + methodName, e);
        }
    }

    public MemorySegment createUpcall(MethodHandle methodHandle, FunctionDescriptor nativeDescriptor) {
        return Linker.nativeLinker().upcallStub(methodHandle, nativeDescriptor, arena);
    }
}
