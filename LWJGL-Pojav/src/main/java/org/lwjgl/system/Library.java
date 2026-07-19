/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 *
 * FCL patch: this is the stock LWJGL 3.4.1 org.lwjgl.system.Library. Its static
 * initializer keeps the early `System.loadLibrary("pojavexec")` (Linux) that Minecraft
 * 26.3+ needs — LWJGL core native symbols (libffi/Upcalls) are resolved before the GLFW
 * class loads. Unlike upstream Amethyst, the GLFW-side JNI bridge setup is NOT done inside
 * libpojavexec's JNI_OnLoad (which would crash here, before the GLFW class exists); it is
 * deferred to GLFW.nativeInitializeGLFWNativeBridge(). The macOS AngelAuraAmethyst branch is
 * dropped (FCL targets Android/Linux). This source shadows the Library.class shipped in the
 * merged LWJGL module jars.
 */
package org.lwjgl.system;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.lwjgl.Version;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.Checks;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.Platform;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.SharedLibraryLoader;
import org.lwjgl.system.linux.LinuxLibrary;
import org.lwjgl.system.windows.WindowsLibrary;

public final class Library {
    public static final String JNI_LIBRARY_NAME = (String)Configuration.LIBRARY_NAME.get(Platform.mapLibraryNameBundled((String)"lwjgl"));
    static final String JAVA_LIBRARY_PATH = "java.library.path";
    private static final Pattern PATH_SEPARATOR = Pattern.compile(File.pathSeparator);
    private static final Pattern NATIVES_JAR = Pattern.compile("/[\\w-]+?-natives-\\w+.jar!/");
    private static final String EXPECTED_MANIFEST_PLATFORM = Platform.get().name().toLowerCase() + '/' + Platform.getArchitecture().name().toLowerCase();

    private Library() {
    }

    public static void initialize() {
    }

    public static void loadSystem(String module, String name) throws UnsatisfiedLinkError {
        Library.loadSystem(System::load, System::loadLibrary, Library.class, module, name);
    }

    public static void loadSystem(Consumer<String> load, Consumer<String> loadLibrary, Class<?> context, String module, String name) throws UnsatisfiedLinkError {
        boolean bundledWithLWJGL;
        String libName;
        block22: {
            if (Checks.DEBUG) {
                APIUtil.DEBUG_STREAM.print("[LWJGL] Loading JNI library: " + name + "\n\tModule: " + module + "\n");
            }
            if (Paths.get(name, new String[0]).isAbsolute()) {
                load.accept(name);
                APIUtil.apiLogMore((CharSequence)"Success");
                return;
            }
            libName = Platform.get().mapLibraryName(name);
            URL libURL = Library.findResource(context, module, libName, bundledWithLWJGL = name.contains("lwjgl"));
            if (libURL == null) {
                if (Library.loadSystemFromLibraryPath(load, context, module, libName, bundledWithLWJGL)) {
                    return;
                }
            } else {
                boolean debugLoader = (Boolean)Configuration.DEBUG_LOADER.get(false);
                try {
                    String regular;
                    if (!((Boolean)Configuration.SHARED_LIBRARY_EXTRACT_FORCE.get(false)).booleanValue() && (regular = Library.getRegularFilePath(libURL)) != null) {
                        load.accept(regular);
                        APIUtil.apiLogMore((CharSequence)("Loaded from classpath: " + regular));
                        return;
                    }
                    if (debugLoader) {
                        APIUtil.apiLogMore((CharSequence)"Using SharedLibraryLoader...");
                    }
                    try (FileChannel ignored = SharedLibraryLoader.load((String)name, (String)libName, (URL)libURL, load);){
                        if (Library.loadSystemFromLibraryPath(load, context, module, libName, bundledWithLWJGL)) {
                            return;
                        }
                    }
                }
                catch (Exception e) {
                    if (!debugLoader) break block22;
                    e.printStackTrace(APIUtil.DEBUG_STREAM);
                }
            }
        }
        String javaLibraryPath = System.getProperty(JAVA_LIBRARY_PATH);
        if (bundledWithLWJGL && javaLibraryPath != null && Library.loadSystem(load, context, module, Library.getBundledPath(module, libName), false, JAVA_LIBRARY_PATH, javaLibraryPath)) {
            return;
        }
        try {
            Path libFile;
            loadLibrary.accept(name);
            Path path = libFile = javaLibraryPath == null ? null : Library.findFile(javaLibraryPath, module, libName, bundledWithLWJGL);
            if (libFile != null) {
                APIUtil.apiLogMore((CharSequence)String.format("Loaded from %s: %s", JAVA_LIBRARY_PATH, libFile));
                if (bundledWithLWJGL) {
                    Library.checkHash(context, libFile, module, libName);
                }
            } else {
                APIUtil.apiLogMore((CharSequence)"Loaded from a ClassLoader provided path.");
            }
            return;
        }
        catch (Throwable t) {
            APIUtil.apiLogMore((CharSequence)(libName + " not found in " + JAVA_LIBRARY_PATH));
            Library.detectPlatformMismatch(context, module);
            Library.printError(true);
            throw new UnsatisfiedLinkError("Failed to locate library: " + libName);
        }
    }

    private static boolean loadSystemFromLibraryPath(Consumer<String> load, Class<?> context, String module, String libName, boolean bundledWithLWJGL) {
        String paths = (String)Configuration.LIBRARY_PATH.get();
        return paths != null && Library.loadSystem(load, context, module, libName, bundledWithLWJGL, Configuration.LIBRARY_PATH.getProperty(), paths);
    }

    private static boolean loadSystem(Consumer<String> load, Class<?> context, String module, String libName, boolean bundledWithLWJGL, String property, String paths) {
        Path libFile = Library.findFile(paths, module, libName, bundledWithLWJGL);
        if (libFile == null) {
            APIUtil.apiLogMore((CharSequence)(libName + " not found in " + property + "=" + paths));
            return false;
        }
        load.accept(libFile.toAbsolutePath().toString());
        APIUtil.apiLogMore((CharSequence)("Loaded from " + property + ": " + libFile));
        if (bundledWithLWJGL) {
            Library.checkHash(context, libFile, module, libName);
        }
        return true;
    }

    public static SharedLibrary loadNative(String module, String name) {
        return Library.loadNative(Library.class, module, name);
    }

    public static SharedLibrary loadNative(Class<?> context, String module, String name) {
        return Library.loadNative(context, module, name, false);
    }

    public static SharedLibrary loadNative(Class<?> context, String module, String name, boolean bundledWithLWJGL) {
        return Library.loadNative(context, module, name, bundledWithLWJGL, true);
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private static SharedLibrary loadNative(Class<?> context, String module, String name, boolean bundledWithLWJGL, boolean printError) {
        String paths;
        SharedLibrary lib;
        String libName;
        block23: {
            if (Checks.DEBUG) {
                APIUtil.DEBUG_STREAM.print("[LWJGL] Loading library: " + name + "\n\tModule: " + module + "\n");
            }
            if (Paths.get(name, new String[0]).isAbsolute()) {
                SharedLibrary lib2 = APIUtil.apiCreateLibrary((String)name);
                APIUtil.apiLogMore((CharSequence)"Success");
                return lib2;
            }
            libName = Platform.get().mapLibraryName(name);
            URL libURL = Library.findResource(context, module, libName, bundledWithLWJGL);
            if (libURL == null) {
                lib = Library.loadNativeFromLibraryPath(context, module, libName, bundledWithLWJGL);
                if (lib != null) {
                    return lib;
                }
            } else {
                boolean debugLoader = (Boolean)Configuration.DEBUG_LOADER.get(false);
                try {
                    String regular;
                    if (!((Boolean)Configuration.SHARED_LIBRARY_EXTRACT_FORCE.get(false)).booleanValue() && (regular = Library.getRegularFilePath(libURL)) != null) {
                        SharedLibrary lib3 = APIUtil.apiCreateLibrary((String)regular);
                        APIUtil.apiLogMore((CharSequence)("Loaded from classpath: " + regular));
                        return lib3;
                    }
                    if (debugLoader) {
                        APIUtil.apiLogMore((CharSequence)"Using SharedLibraryLoader...");
                    }
                    try (FileChannel ignored = SharedLibraryLoader.load((String)name, (String)libName, (URL)libURL, null);){
                        lib = Library.loadNativeFromLibraryPath(context, module, libName, bundledWithLWJGL);
                        if (lib != null) {
                            SharedLibrary sharedLibrary = lib;
                            return sharedLibrary;
                        }
                    }
                }
                catch (Exception e) {
                    if (!debugLoader) break block23;
                    e.printStackTrace(APIUtil.DEBUG_STREAM);
                }
            }
        }
        if (!bundledWithLWJGL && (lib = Library.loadNativeFromSystem(libName)) != null) {
            return lib;
        }
        if (((Boolean)Configuration.EMULATE_SYSTEM_LOADLIBRARY.get(false)).booleanValue()) {
            try {
                Method findLibrary = ClassLoader.class.getDeclaredMethod("findLibrary", String.class);
                findLibrary.setAccessible(true);
                String libPath = (String)findLibrary.invoke(context.getClassLoader(), name);
                if (libPath != null) {
                    lib = APIUtil.apiCreateLibrary((String)libPath);
                    APIUtil.apiLogMore((CharSequence)("Loaded from ClassLoader provided path: " + libPath));
                    return lib;
                }
            }
            catch (Exception findLibrary) {
                // empty catch block
            }
        }
        if ((paths = System.getProperty(JAVA_LIBRARY_PATH)) != null && (lib = Library.loadNative(context, module, libName, bundledWithLWJGL, JAVA_LIBRARY_PATH, paths)) != null) {
            return lib;
        }
        if (bundledWithLWJGL && (lib = Library.loadNativeFromSystem(libName)) != null) {
            return lib;
        }
        if (!printError) throw new UnsatisfiedLinkError("Failed to locate library: " + libName);
        Library.detectPlatformMismatch(context, module);
        Library.printError(bundledWithLWJGL);
        throw new UnsatisfiedLinkError("Failed to locate library: " + libName);
    }

    private static @Nullable SharedLibrary loadNativeFromSystem(String libName) {
        SharedLibrary lib;
        try {
            lib = APIUtil.apiCreateLibrary((String)libName);
            String path = lib.getPath();
            APIUtil.apiLogMore((CharSequence)(path == null ? "Loaded from system paths" : "Loaded from system paths: " + path));
        }
        catch (UnsatisfiedLinkError e) {
            lib = null;
            APIUtil.apiLogMore((CharSequence)(libName + " not found in system paths"));
        }
        return lib;
    }

    private static @Nullable SharedLibrary loadNativeFromLibraryPath(Class<?> context, String module, String libName, boolean bundledWithLWJGL) {
        String paths = (String)Configuration.LIBRARY_PATH.get();
        if (paths == null) {
            return null;
        }
        return Library.loadNative(context, module, libName, bundledWithLWJGL, Configuration.LIBRARY_PATH.getProperty(), paths);
    }

    private static @Nullable SharedLibrary loadNative(Class<?> context, String module, String libName, boolean bundledWithLWJGL, String property, String paths) {
        Path libFile = Library.findFile(paths, module, libName, bundledWithLWJGL);
        if (libFile == null) {
            APIUtil.apiLogMore((CharSequence)(libName + " not found in " + property + "=" + paths));
            return null;
        }
        SharedLibrary lib = APIUtil.apiCreateLibrary((String)libFile.toAbsolutePath().toString());
        APIUtil.apiLogMore((CharSequence)("Loaded from " + property + ": " + libFile));
        if (bundledWithLWJGL) {
            Library.checkHash(context, libFile, module, libName);
        }
        return lib;
    }

    public static SharedLibrary loadNative(Class<?> context, String module, @Nullable Configuration<String> name, String ... defaultNames) {
        return Library.loadNative(context, module, name, null, defaultNames);
    }

    public static SharedLibrary loadNative(Class<?> context, String module, @Nullable Configuration<String> name, @Nullable Supplier<SharedLibrary> fallback, String ... defaultNames) {
        String libraryName;
        if (name != null && (libraryName = (String)name.get()) != null) {
            return Library.loadNative(context, module, libraryName, false);
        }
        if (defaultNames.length == 0) {
            if (fallback == null) {
                throw new IllegalArgumentException("No fallback library supplier specified.");
            }
            return fallback.get();
        }
        if (fallback == null && defaultNames.length == 1) {
            return Library.loadNative(context, module, defaultNames[0], false);
        }
        try {
            return Library.loadNative(context, module, defaultNames[0], false, false);
        }
        catch (Throwable t) {
            for (int i = 1; i < defaultNames.length; ++i) {
                try {
                    return Library.loadNative(context, module, defaultNames[i], false, fallback == null && i == defaultNames.length - 1);
                }
                catch (Throwable throwable) {
                    continue;
                }
            }
            if (fallback != null) {
                return fallback.get();
            }
            throw t;
        }
    }

    public static SharedLibrary createFromHandle(String name, long handle) {
        if (Platform.get() == Platform.WINDOWS) {
            return new WindowsLibrary(name, handle);
        }
        if (Platform.get() == Platform.LINUX) {
            return new LinuxLibrary(name, handle);
        }
        throw new IllegalStateException();
    }

    private static String getBundledPath(String module, String resource) {
        return Platform.mapLibraryPathBundled((String)(module.replace('.', '/') + "/" + resource));
    }

    static @Nullable URL findResource(Class<?> context, String module, String resource, boolean bundledWithLWJGL) {
        String bundledResource;
        URL url = null;
        if (bundledWithLWJGL && !(bundledResource = Library.getBundledPath(module, resource)).equals(resource)) {
            url = context.getClassLoader().getResource(bundledResource);
        }
        return url == null ? context.getClassLoader().getResource(resource) : url;
    }

    static @Nullable String getRegularFilePath(URL url) {
        if (url.getProtocol().equals("file")) {
            try {
                Path path = Paths.get(url.toURI());
                if (path.isAbsolute() && Files.isReadable(path)) {
                    return path.toString();
                }
            }
            catch (URISyntaxException uRISyntaxException) {
                // empty catch block
            }
        }
        return null;
    }

    static @Nullable Path findFile(String path, String module, String file, boolean bundledWithLWJGL) {
        Path p;
        String bundledFile;
        if (bundledWithLWJGL && !(bundledFile = Library.getBundledPath(module, file)).equals(file) && (p = Library.findFile(path, bundledFile)) != null) {
            return p;
        }
        return Library.findFile(path, file);
    }

    private static @Nullable Path findFile(String path, String file) {
        for (String directory : PATH_SEPARATOR.split(path)) {
            Path p = Paths.get(directory, file);
            if (!Files.isReadable(p)) continue;
            return p;
        }
        return null;
    }

    private static void detectPlatformMismatch(Class<?> context, String module) {
        if (!module.startsWith("org.lwjgl")) {
            return;
        }
        String moduleTitle = module.equals("org.lwjgl") ? "lwjgl" : "lwjgl-" + module.substring("org.lwjgl.".length());
        ArrayList<String> platforms = new ArrayList<String>(8);
        try {
            Enumeration<URL> manifests = context.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (manifests.hasMoreElements()) {
                InputStream is = manifests.nextElement().openStream();
                try {
                    String jarPlatform;
                    Manifest manifest = new Manifest(is);
                    Attributes attribs = manifest.getMainAttributes();
                    if (!moduleTitle.equals(attribs.getValue("Implementation-Title")) || (jarPlatform = attribs.getValue("LWJGL-Platform")) == null || EXPECTED_MANIFEST_PLATFORM.equals(jarPlatform)) continue;
                    platforms.add(jarPlatform);
                }
                finally {
                    if (is == null) continue;
                    is.close();
                }
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
        if (!platforms.isEmpty()) {
            APIUtil.DEBUG_STREAM.print("[LWJGL] Platform/architecture mismatch detected for module: " + module + "\n\tJVM platform:\t\t" + Platform.get().getName() + " " + System.getProperty("os.arch") + " " + System.getProperty("java.version") + "\n\t\t" + System.getProperty("java.vm.name") + " v" + System.getProperty("java.vm.version") + " by " + System.getProperty("java.vm.vendor") + "\n\tPlatform" + (platforms.size() == 1 ? "" : "s") + " available on classpath:\n\t\t" + String.join((CharSequence)"\n\t\t", platforms) + "\n");
        }
    }

    private static void printError(boolean bundledWithLWJGL) {
        Library.printError("[LWJGL] Failed to load a library. Possible solutions:\n" + (bundledWithLWJGL ? "\ta) Add the directory that contains the shared library to -Djava.library.path or -Dorg.lwjgl.librarypath.\n\tb) Add the JAR that contains the shared library to the classpath." : "\ta) Install the library or the driver that provides the library.\n\tb) Ensure that the library is accessible from the system library paths."));
    }

    static void printError(String message) {
        StringBuilder sb = new StringBuilder(message);
        sb.append("\n");
        if (!Checks.DEBUG) {
            sb.append("[LWJGL] Enable debug mode with -Dorg.lwjgl.util.Debug=true for better diagnostics.\n");
            if (!((Boolean)Configuration.DEBUG_LOADER.get(false)).booleanValue()) {
                sb.append("[LWJGL] Enable the SharedLibraryLoader debug mode with -Dorg.lwjgl.util.DebugLoader=true for better diagnostics.\n");
            }
        }
        APIUtil.DEBUG_STREAM.print(sb);
    }

    private static void checkHash(Class<?> context, Path libFile, String module, String libName) {
        block7: {
            if (((Boolean)Configuration.DISABLE_HASH_CHECKS.get(!Checks.CHECKS)).booleanValue()) {
                return;
            }
            try {
                byte[] actual;
                URL classesURL = null;
                URL nativesURL = null;
                Enumeration<URL> resources = context.getClassLoader().getResources("META-INF/" + Library.getBundledPath(module, libName) + ".sha1");
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    if (NATIVES_JAR.matcher(url.toExternalForm()).find()) {
                        nativesURL = url;
                        continue;
                    }
                    classesURL = url;
                }
                if (classesURL == null) {
                    return;
                }
                byte[] expected = Library.getSHA1(classesURL);
                byte[] byArray = actual = Checks.DEBUG || nativesURL == null ? Library.getSHA1(libFile) : Library.getSHA1(nativesURL);
                if (!Arrays.equals(expected, actual)) {
                    APIUtil.DEBUG_STREAM.println("[LWJGL] [ERROR] Incompatible Java and native library versions detected.\nPossible reasons:\n\ta) -Djava.library.path is set to a folder containing shared libraries of an older LWJGL version.\n\tb) The classpath contains jar files of an older LWJGL version.\nPossible solutions:\n\ta) Make sure to not set -Djava.library.path (it is not needed for developing with LWJGL 3) or make\n\t   sure the folder it points to contains the shared libraries of the correct LWJGL version.\n\tb) Check the classpath and make sure to only have jar files of the same LWJGL version in it.");
                }
            }
            catch (Throwable t) {
                if (!Checks.DEBUG) break block7;
                APIUtil.apiLog((CharSequence)"Failed to verify native library.");
                t.printStackTrace();
            }
        }
    }

    private static byte[] getSHA1(URL hashURL) throws IOException {
        byte[] hash = new byte[20];
        try (InputStream sha1 = hashURL.openStream();){
            for (int i = 0; i < 20; ++i) {
                hash[i] = (byte)(Character.digit(sha1.read(), 16) << 4 | Character.digit(sha1.read(), 16));
            }
        }
        return hash;
    }

    private static byte[] getSHA1(Path libFile) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream input = Files.newInputStream(libFile, new OpenOption[0]);){
            int n;
            byte[] buffer = new byte[8192];
            while ((n = input.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        return digest.digest();
    }

    static {
        if (Checks.DEBUG) {
            APIUtil.DEBUG_STREAM.print("[LWJGL] Version: " + Version.getVersion() + "\n\t OS: " + System.getProperty("os.name") + " v" + System.getProperty("os.version") + "\n\tJRE: " + Platform.get().getName() + " " + System.getProperty("os.arch") + " " + System.getProperty("java.version") + "\n\tJVM: " + System.getProperty("java.vm.name") + " v" + System.getProperty("java.vm.version") + " by " + System.getProperty("java.vm.vendor") + "\n");
        }
        // FCL: load libpojavexec as early as LWJGL's Library class initializes.
        // Minecraft 26.3+ resolves LWJGL core native symbols (libffi via org.lwjgl.system.Upcalls,
        // e.g. from RenderSystem.initBackendSystem) BEFORE the GLFW class is initialized, so
        // libpojavexec (which provides those symbols) must already be loaded here. The paired
        // GLFW-side JNI bridge setup is deferred to GLFW.nativeInitializeGLFWNativeBridge(),
        // which the GLFW class calls once it is ready, so libpojavexec's JNI_OnLoad must NOT
        // touch the GLFW class. (Upstream Amethyst injects the same early load; the macOS
        // AngelAuraAmethyst branch is dropped as FCL targets Android/Linux.)
        try {
            if (Platform.get() == Platform.LINUX) {
                System.loadLibrary("pojavexec");
            }
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
        Library.loadSystem("org.lwjgl", JNI_LIBRARY_NAME);
    }
}
