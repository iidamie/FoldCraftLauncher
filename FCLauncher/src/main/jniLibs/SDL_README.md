# SDL3 native library (libSDL3.so) — 放置说明

FCL 的 LWJGL 3.4.1 已经带上了完整的 SDL3 **Java 绑定**(`org.lwjgl.sdl.*`，打包在
`FCL/src/main/assets/app_runtime/lwjgl/lwjgl.jar` 里）。要让依赖 SDL 的模组在运行时真正工作，
还需要把 SDL3 的**原生库** `libSDL3.so` 放进本目录对应的 ABI 子目录，它会被打进 APK。

## 放在哪

把各 ABI 的 `libSDL3.so` 放到：

```
FCLauncher/src/main/jniLibs/arm64-v8a/libSDL3.so
FCLauncher/src/main/jniLibs/armeabi-v7a/libSDL3.so
FCLauncher/src/main/jniLibs/x86/libSDL3.so
FCLauncher/src/main/jniLibs/x86_64/libSDL3.so
```

至少要提供 **arm64-v8a**（绝大多数现代设备）。缺某个 ABI 只会让该 ABI 的设备无法用 SDL，
不影响其它功能。

## 从哪来

SDL 官方在 GitHub 提供预编译的 Android 产物：

- 仓库：https://github.com/libsdl-org/SDL
- 在 Releases 里找 `SDL3-devel-<version>-android.zip`（或 SDL3 的 `.aar`）。
- 解压后，各 ABI 的 `libSDL3.so` 在 `lib/<abi>/` 或 `.aar` 内的 `jni/<abi>/` 下。
- 版本尽量对齐 LWJGL 3.4.1 绑定所针对的 **SDL 3.4.x**（`SDLVersion` 常量为 3.4.1）。
  次要版本内 ABI 兼容，用最接近的 3.4.x 即可。

> SDL3 是通用开源库，不含任何 FCL/Amethyst 定制，任何官方或自编译的、ABI 匹配的
> `libSDL3.so` 都可用。

## 运行时如何被加载

1. 依赖 SDL 的模组（或 LWJGL 内部）调用 `org.lwjgl.sdl.SDLInit.SDL_Init(...)`。
2. patched 的 `SDLInit` 会先调用 `CallbackBridge.nativeNotifyLauncher(SDL, INIT)`（JRE 侧）。
3. 该 native 方法（`libpojavexec`）跳到 Android/Dalvik 侧的
   `CallbackBridge.notifyLauncher(...)`，后者执行 `System.loadLibrary("SDL3")`，
   从 APK 的 `nativeLibraryDir` 加载本目录提供的 `libSDL3.so`。
4. 之后 LWJGL 的 `SDL.getLibrary()`（`Library.loadNative("SDL3")`）即可解析到已加载的库，
   SDL 的原生函数正常调用。

## 可选：自定义库名/路径

LWJGL 支持用系统属性覆盖 SDL 库名：

```
-Dorg.lwjgl.sdl.libname=/绝对路径/libSDL3.so
```

一般无需设置——放进 jniLibs 后 `System.loadLibrary("SDL3")` 即可找到。

## 说明

- 当前实现为「仅 SDL 核心」：保证 `libSDL3.so` 被加载、SDL 初始化通知链打通。
  不包含 SDL 直连输入/手柄（FCL 已有自己的 `@CriticalNative` 输入与 `Gamepad.kt`）。
- 若某些旧模组还需要 SDL2 兼容层（`libSDL2.so`，即 sdl2-compat），可同法放入并在
  `CallbackBridge.notifyLauncher` 里追加 `System.loadLibrary("SDL2")`。
