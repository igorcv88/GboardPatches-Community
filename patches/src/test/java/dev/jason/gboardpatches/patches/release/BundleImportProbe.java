package dev.jason.gboardpatches.patches.release;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;

/** Launched with the FINAL MPP first on the classpath, after buildAndroid. */
public final class BundleImportProbe {
    public static void main(String[] args) throws Exception {
        File bundle = new File(args[0]).getCanonicalFile();
        String source = "https://github.com/" + args[2];
        try (JarFile jar = new JarFile(bundle)) {
            var attributes = jar.getManifest().getMainAttributes();
            if (!args[1].equals(attributes.getValue("Version"))
                    || !source.equals(attributes.getValue("Source"))
                    || !source.equals(attributes.getValue("Website"))
                    || !(source + "/issues").equals(attributes.getValue("Contact"))) {
                throw new AssertionError("Final MPP version/source metadata mismatch");
            }
            if (jar.getEntry("classes.dex") == null) throw new AssertionError("Final MPP has no DEX");
            for (String action : new String[]{"undo", "redo"}) {
                if (jar.getEntry("editing-access-points-res/drawable/gboard_patches_"+action+".xml") == null) {
                    throw new AssertionError("Integrate the editing-resource PR before the first community release");
                }
            }
            var runtimeEntry = jar.getEntry("extensions/gboard-patches.rve");
            if (runtimeEntry == null) throw new AssertionError("Final MPP has no runtime");
            String runtime;
            try (var input=jar.getInputStream(runtimeEntry)) {
                runtime=new String(input.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
            }
            for (String marker : new String[]{"text_edit_undo", "text_edit_redo",
                    "Ldev/jason/gboardpatches/extension/clipboard/ClipboardSearchController;",
                    "Ldev/jason/gboardpatches/extension/quicktoolbar/QuickToolbarState;"}) {
                if (!runtime.contains(marker)) {
                    throw new AssertionError("Integrate editing/search foundation PRs before publishing this bundle");
                }
            }
        }
        Class<?> registry = Class.forName("dev.jason.gboardpatches.patches.gboard.registry.GboardPatchRegistryKt");
        File loaded = new File(registry.getProtectionDomain().getCodeSource().getLocation().toURI()).getCanonicalFile();
        if (!bundle.equals(loaded)) throw new AssertionError("Patch classes did not load from final MPP");
        Method load = Class.forName("app.morphe.patcher.patch.PatchKt").getMethod("loadPatchesFromJar",Set.class);
        Set<?> patches = (Set<?>) load.invoke(null,Set.of(bundle));
        Set<String> names = new HashSet<>();
        for (Object patch : patches) {
            Object name = patch.getClass().getMethod("getName").invoke(patch);
            if (name != null) names.add(name.toString());
        }
        if (!names.contains("Toolbar Editing Buttons") || !names.contains("Clipboard Enhancements")) {
            throw new AssertionError("Real Morphe loader did not import the expected public patch catalog");
        }
        System.out.println("Morphe final-bundle JVM import: " + names.size() + " public patches; metadata verified");
    }
}
