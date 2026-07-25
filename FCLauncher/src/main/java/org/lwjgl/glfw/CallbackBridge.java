package org.lwjgl.glfw;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Choreographer;

import androidx.annotation.Nullable;

import com.tungsten.fcl.FCLApplication;
import com.tungsten.fclauncher.bridge.FCLBridge;
import com.tungsten.fclauncher.keycodes.LwjglGlfwKeycode;
import com.tungsten.fclauncher.keycodes.LwjglKeycodeMap;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import dalvik.annotation.optimization.CriticalNative;

public class CallbackBridge {
    public static final Choreographer sChoreographer = Choreographer.getInstance();
    private static FCLBridge fclBridge = null;
    private static boolean isGrabbing = false;
    private static final Consumer<Boolean> grabListener = isGrabbing -> CallbackBridge.fclBridge.setCursorMode(isGrabbing ? FCLBridge.CursorDisabled : FCLBridge.CursorEnabled);

    public static final int CLIPBOARD_COPY = 2000;
    public static final int CLIPBOARD_PASTE = 2001;
    public static final int CLIPBOARD_OPEN = 2002;

    public static volatile int windowWidth, windowHeight;
    public static volatile int physicalWidth, physicalHeight;
    public static float mouseX, mouseY;
    public volatile static boolean holdingAlt, holdingCapslock, holdingCtrl,
            holdingNumlock, holdingShift;

    public static void putMouseEventWithCoords(int button, float x, float y) {
        putMouseEventWithCoords(button, true, x, y);
        sChoreographer.postFrameCallbackDelayed(l -> putMouseEventWithCoords(button, false, x, y), 33);
    }

    public static void putMouseEventWithCoords(int button, boolean isDown, float x, float y /* , int dz, long nanos */) {
        sendCursorPos(x, y);
        sendMouseKeycode(button, CallbackBridge.getCurrentMods(), isDown);
    }


    public static void sendCursorPos(float x, float y) {
        mouseX = x;
        mouseY = y;
        nativeSendCursorPos(mouseX, mouseY);
    }

    public static void sendKeycode(int keycode, char keychar, int scancode, int modifiers, boolean isDown) {
        // TODO CHECK: This may cause input issue, not receive input!
        if (keycode != 0) {
            int code = LwjglKeycodeMap.convertKeycode(keycode);
            if (code <= 0) {
                return;
            }
            nativeSendKey(code, scancode, isDown ? 1 : 0, modifiers);
        }
        if (isDown && !Character.isISOControl(keychar)) {
            nativeSendCharMods(keychar, modifiers);
            nativeSendChar(keychar);
        }
    }

    public static void sendChar(char keychar, int modifiers) {
        nativeSendCharMods(keychar, modifiers);
        nativeSendChar(keychar);
    }

    public static void sendKeyPress(int keyCode, int modifiers, boolean status) {
        sendKeyPress(keyCode, 0, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, int scancode, int modifiers, boolean status) {
        sendKeyPress(keyCode, '\u0000', scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, char keyChar, int scancode, int modifiers, boolean status) {
        CallbackBridge.sendKeycode(keyCode, keyChar, scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode) {
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), true);
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), false);
    }

    public static void sendMouseButton(int button, boolean status) {
        CallbackBridge.sendMouseKeycode(button, CallbackBridge.getCurrentMods(), status);
    }

    public static void sendMouseKeycode(int button, int modifiers, boolean isDown) {
        // if (isGrabbing()) DEBUG_STRING.append("MouseGrabStrace: " + android.util.Log.getStackTraceString(new Throwable()) + "\n");
        nativeSendMouseButton(button, isDown ? 1 : 0, modifiers);
    }

    public static void sendMouseKeycode(int keycode) {
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), true);
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), false);
    }

    public static void sendScroll(double xoffset, double yoffset) {
        nativeSendScroll(xoffset, yoffset);
    }

    public static void sendUpdateWindowSize(int w, int h) {
        nativeSendScreenSize(w, h);
    }

    public static boolean isGrabbing() {
        // Avoid going through the JNI each time.
        return isGrabbing;
    }

    // Called from JRE side
    @SuppressWarnings("unused")
    public static @Nullable String accessAndroidClipboard(int type, String copy) {
        Activity activity = FCLApplication.getCurrentActivity();
        AtomicReference<String> result = new AtomicReference<>();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                switch (type) {
                    case CLIPBOARD_COPY:
                        ClipData clip = ClipData.newPlainText("FCL Clipboard", copy);
                        clipboard.setPrimaryClip(clip);
                        break;
                    case CLIPBOARD_PASTE:
                        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                            result.set(clipboard.getPrimaryClip().getItemAt(0).getText().toString());
                        } else {
                            result.set("");
                        }
                        break;
                    case CLIPBOARD_OPEN:
                        FCLBridge.openLink(copy);
                        break;
                }
            });
        }
        return result.get();
    }


    public static int getCurrentMods() {
        int currMods = 0;
        if (holdingAlt) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_ALT;
        }
        if (holdingCapslock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CAPS_LOCK;
        }
        if (holdingCtrl) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CONTROL;
        }
        if (holdingNumlock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_NUM_LOCK;
        }
        if (holdingShift) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_SHIFT;
        }
        return currMods;
    }

    public static void setModifiers(int keyCode, boolean isDown) {
        switch (keyCode) {
            case LwjglGlfwKeycode.KEY_LEFT_SHIFT:
                CallbackBridge.holdingShift = isDown;
                break;

            case LwjglGlfwKeycode.KEY_LEFT_CONTROL:
                CallbackBridge.holdingCtrl = isDown;
                break;

            case LwjglGlfwKeycode.KEY_LEFT_ALT:
                CallbackBridge.holdingAlt = isDown;
                break;

            case LwjglGlfwKeycode.KEY_CAPS_LOCK:
                CallbackBridge.holdingCapslock = isDown;
                break;

            case LwjglGlfwKeycode.KEY_NUM_LOCK:
                CallbackBridge.holdingNumlock = isDown;
                break;
        }
    }

    public static void setFCLBridge(FCLBridge fclBridge) {
        CallbackBridge.fclBridge = fclBridge;
    }

    //Called from JRE side
    @SuppressWarnings("unused")
    private static void onGrabStateChanged(final boolean grabbing) {
        isGrabbing = grabbing;
        sChoreographer.postFrameCallbackDelayed((time) -> {
            // If the grab re-changed, skip notify process
            if (isGrabbing != grabbing) {
                return;
            }
            synchronized (grabListener) {
                grabListener.accept(isGrabbing);
            }
        }, 16);

    }

    @CriticalNative
    public static native void nativeSetUseInputStackQueue(boolean useInputStackQueue);

    @CriticalNative
    private static native boolean nativeSendChar(char codepoint);

    // GLFW: GLFWCharModsCallback deprecated, but is Minecraft still use?
    @CriticalNative
    private static native boolean nativeSendCharMods(char codepoint, int mods);

    @CriticalNative
    private static native void nativeSendKey(int key, int scancode, int action, int mods);

    // private static native void nativeSendCursorEnter(int entered);
    @CriticalNative
    private static native void nativeSendCursorPos(float x, float y);

    @CriticalNative
    private static native void nativeSendMouseButton(int button, int action, int mods);

    @CriticalNative
    private static native void nativeSendScroll(double xoffset, double yoffset);

    @CriticalNative
    private static native void nativeSendScreenSize(int width, int height);

    public static native void nativeSetWindowAttrib(int attrib, int value);

    public static native void setupBridgeWindow(Object surface);

    public static native int getFps();

    // ---- SDL support (Minecraft 26.3+ uses SDL3 for windowing/input) ----

    // Notification types (must match the JRE-side org.lwjgl.glfw.CallbackBridge in LWJGL/3.4.1)
    public static final int SDL = 0;
    // Notification actions
    public static final int INIT = 0;

    /** True once SDL has been initialized and wired up. The ported org.libsdl.app.SDLActivity
     * gates its native callbacks on this flag; it stays false for GLFW-based (older) versions. */
    public static volatile boolean sdlEnabled = false;

    /** The Android Surface the game renders onto, published by JVMActivity so SDL can bind to it. */
    private static android.view.Surface sdlNativeSurface = null;
    private static android.app.Activity sdlActivity = null;

    /** Called by JVMActivity to hand the rendering surface + activity context to the SDL layer. */
    public static void setSdlSurface(android.app.Activity activity, android.view.Surface surface) {
        sdlActivity = activity;
        sdlNativeSurface = surface;
    }

    /**
     * Called from the JRE side (via nativeNotifyLauncher, triggered by the SDL_InitSubSystem
     * bytehook) when the game initializes SDL. Loads libSDL3.so, sets up SDL's JNI, and binds
     * the launcher's Android surface to SDL. Returns whether SDL support was enabled.
     */
    @SuppressWarnings("unused")
    @androidx.annotation.Keep
    public static boolean notifyLauncher(int type, int... action) {
        if (type != SDL || action.length == 0 || action[0] != INIT) {
            return false;
        }
        if (sdlEnabled) {
            return true;
        }
        try {
            System.loadLibrary("SDL3");
            try { System.loadLibrary("SDL2"); } catch (Throwable ignored) {}
            org.libsdl.app.SDL.initialize();
            if (sdlActivity != null) {
                org.libsdl.app.SDL.setContext(sdlActivity);
            }
            org.libsdl.app.SDL.setupJNI();
            org.libsdl.app.SDLSurface sdlSurface = new org.libsdl.app.SDLSurface(sdlActivity);
            org.libsdl.app.SDLActivity.externalInitialize(sdlSurface, null, sdlNativeSurface);
            sdlEnabled = true;
            if (org.libsdl.app.SDLActivity.getSDLSurface() != null) {
                org.libsdl.app.SDLActivity.getSDLSurface().surfaceChanged();
            }
            android.util.Log.i("CallbackBridge", "SDL support enabled");
            return true;
        } catch (Throwable t) {
            android.util.Log.e("CallbackBridge", "Failed to enable SDL support", t);
            return false;
        }
    }

    static {
        System.loadLibrary("pojavexec");
    }
}

