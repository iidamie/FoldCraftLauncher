package org.lwjgl.glfw;

import static org.lwjgl.system.APIUtil.apiCreateCIF;
import static org.lwjgl.system.libffi.LibFFI.FFI_DEFAULT_ABI;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_pointer;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_uint32;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_void;

import java.lang.invoke.MethodHandles;

import org.lwjgl.system.Callback;
import org.lwjgl.system.CallbackI;
import org.lwjgl.system.NativeType;

@FunctionalInterface
    @NativeType("FCLinjectorfun")
    public interface FCLInjectorCallbackI extends CallbackI {

        Callback.Descriptor DESCRIPTOR = new Callback.Descriptor(
                MethodHandles.lookup(),
                apiCreateCIF(
                        FFI_DEFAULT_ABI,
                        ffi_type_void,
                        ffi_type_pointer, ffi_type_uint32
                )
        );

        @Override
        default Callback.Descriptor getDescriptor() { return DESCRIPTOR; }

        @Override
        default void callback(long ret, long args) {
            invoke();
        }

        void invoke();

    }
