package dev.jason.gboardpatches.patches.editing;

import java.nio.file.*;
import java.util.Comparator;
import java.util.jar.JarFile;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real ARSCLib encoder round trip on a resource fixture, not a Gboard UI/device test. */
public class EditingIconEncodingTest {
    @Test public void bothMaterialVectorsAreCompiledAndResolvableFromTheResourceTable() throws Exception {
        Path root=Path.of("").toAbsolutePath();
        while(root!=null && !Files.exists(root.resolve("settings.gradle.kts")))root=root.getParent();
        assertNotNull(root);
        Path temp=Files.createTempDirectory("editing-icon-encoding");
        try {
            Path pkg=temp.resolve("resources/test"),res=pkg.resolve("res");
            Files.createDirectories(res.resolve("drawable"));Files.createDirectories(res.resolve("values"));
            Files.writeString(pkg.resolve("package.json"),"{\"package_id\":127,\"package_name\":\"example.icons\"}");
            // Fixture IDs model the public.xml output of Morphe 1.8 ResourceIdProcessor.
            // Actual Gboard IDs are allocated at patch time, never copied from this fixture.
            Files.writeString(res.resolve("values/public.xml"),"<resources><public type=\"drawable\" name=\"gboard_patches_undo\" id=\"0x7f010000\"/><public type=\"drawable\" name=\"gboard_patches_redo\" id=\"0x7f010001\"/></resources>");
            Files.writeString(temp.resolve("AndroidManifest.xml"),"<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"example.icons\"><uses-sdk android:minSdkVersion=\"33\"/><application/></manifest>");
            for(String action:new String[]{"undo","redo"}) {
                String name="gboard_patches_"+action+".xml";
                Files.copy(root.resolve("patches/src/main/resources/editing-access-points-res/drawable/"+name),res.resolve("drawable/"+name));
            }
            Class<?> encoderType=Class.forName("com.reandroid.apk.ApkModuleXmlEncoder");
            Class<?> moduleType=Class.forName("com.reandroid.apk.ApkModule");
            Object encoder=encoderType.getConstructor().newInstance();
            Path apk=temp.resolve("encoded.apk");
            try(AutoCloseable module=(AutoCloseable)encoderType.getMethod("getApkModule").invoke(encoder)) {
                moduleType.getMethod("setPreferredFramework",Integer.class).invoke(module,36);
                encoderType.getMethod("scanDirectory",java.io.File.class).invoke(encoder,temp.toFile());
                moduleType.getMethod("writeApk",java.io.File.class).invoke(module,apk.toFile());
            }
            try(AutoCloseable module=(AutoCloseable)moduleType.getMethod("loadApkFile",java.io.File.class).invoke(null,apk.toFile());
                JarFile archive=new JarFile(apk.toFile())) {
                Object table=moduleType.getMethod("getTableBlock").invoke(module);
                for(String action:new String[]{"undo","redo"}) {
                    int id=(Integer)table.getClass().getMethod("resolveResourceId",String.class,String.class,String.class)
                            .invoke(table,"example.icons","drawable","gboard_patches_"+action);
                    assertNotEquals(0,id);
                    var entry=archive.getEntry("res/drawable/gboard_patches_"+action+".xml");assertNotNull(entry);
                    try(var input=archive.getInputStream(entry)) {
                        assertArrayEquals(new byte[]{3,0,8,0},input.readNBytes(4)); // Binary Android XML.
                    }
                }
            }
        } finally {
            try(var files=Files.walk(temp)) {
                for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
    }
}
