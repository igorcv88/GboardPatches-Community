package dev.jason.gboardpatches.patches.release;

import java.nio.file.Files;
import java.nio.file.Path;
import kotlinx.datetime.serializers.LocalDateTimeIso8601Serializer;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class MorpheManifestTimestampTest {
    @Test public void actualPythonManifestParsesWithMorpheLocalDateTimeDeserializer() throws Exception {
        Path root=Path.of("").toAbsolutePath();
        while(root!=null && !Files.exists(root.resolve("tools/release_bundle.py"))) root=root.getParent();
        assertNotNull(root);
        Path temporary=Files.createTempDirectory("morphe-manifest-contract");
        Path notes=temporary.resolve("notes.md"), manifest=temporary.resolve("patches-bundle.json");
        try {
            Files.writeString(notes,"### Gboard\nRelease contract test");
            Process process=new ProcessBuilder("python3",root.resolve("tools/release_bundle.py").toString(),
                    "metadata","--repository","igorcv88/GboardPatches-Community","--version","3.12.1",
                    "--notes",notes.toString(),"--output",manifest.toString()).inheritIO().start();
            assertEquals(0,process.waitFor());
            JsonObject data=(JsonObject)Json.Default.parseToJsonElement(Files.readString(manifest));
            assertNotNull(Json.Default.decodeFromString(LocalDateTimeIso8601Serializer.INSTANCE,
                    data.get("created_at").toString()));
        } finally {
            Files.deleteIfExists(manifest);Files.deleteIfExists(notes);Files.deleteIfExists(temporary);
        }
    }
    @Test public void offsetBearingTimestampIsRejectedByTheActualMorpheFieldDeserializer() {
        assertThrows(Exception.class,()->Json.Default.decodeFromString(
                LocalDateTimeIso8601Serializer.INSTANCE,"\"2026-10-15T20:39:00+00:00\""));
    }
}
