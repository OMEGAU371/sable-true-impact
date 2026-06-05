package io.github.omegau371.trueimpact.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the deploy-filter algorithm used by the Gradle deploy task.
 *
 * The algorithm (mirrored in build.gradle's isOwnJar closure):
 *   A JAR is eligible for deletion iff it:
 *     1. Has a name starting with "true_impact-" and ending with ".jar"
 *     2. Is NOT a sources/javadoc JAR (no "-sources" or "-javadoc" in name)
 *     3. Has manifest attribute TI-Project-Line = "rewrite2"
 *
 * These tests prove that neither legacy v1.x JARs nor other mods are ever deleted.
 */
class DeployFilterTest {

    @TempDir
    Path tempDir;

    // ── isOwnJar ─────────────────────────────────────────────────────────────

    @Test
    void rewrite2_main_jar_is_own() throws IOException {
        File jar = jar("true_impact-0.1.0-foundation.jar", "rewrite2");
        assertTrue(isOwnJar(jar), "Main JAR with rewrite2 marker must be identified as own");
    }

    @Test
    void rewrite2_jar_from_future_version_is_own() throws IOException {
        File jar = jar("true_impact-0.2.0-alpha.jar", "rewrite2");
        assertTrue(isOwnJar(jar), "Future 0.x JAR with rewrite2 marker must be identified as own");
    }

    @Test
    void legacy_v1_jar_without_marker_is_not_own() throws IOException {
        File jar = jar("true_impact-1.1.9.jar", null);
        assertFalse(isOwnJar(jar), "Legacy v1 JAR (no manifest marker) must NOT be deleted");
    }

    @Test
    void legacy_v1_jar_with_wrong_marker_is_not_own() throws IOException {
        File jar = jar("true_impact-1.2.30.jar", "legacy");
        assertFalse(isOwnJar(jar), "Legacy JAR with different marker must NOT be deleted");
    }

    @Test
    void sable_jar_is_not_own() throws IOException {
        File jar = jar("sable-neoforge-1.21.1-1.2.2.jar", null);
        assertFalse(isOwnJar(jar), "Sable JAR must never be deleted");
    }

    @Test
    void create_jar_is_not_own() throws IOException {
        File jar = jar("create-1.21.1-6.0.10.jar", null);
        assertFalse(isOwnJar(jar), "Create JAR must never be deleted");
    }

    @Test
    void sources_jar_is_not_own_even_with_marker() throws IOException {
        // sources JARs should not be deleted from mods dir (they shouldn't be there, but guard anyway)
        File jar = jar("true_impact-0.1.0-foundation-sources.jar", "rewrite2");
        assertFalse(isOwnJar(jar), "Sources JAR must not be targeted for deletion");
    }

    @Test
    void javadoc_jar_is_not_own_even_with_marker() throws IOException {
        File jar = jar("true_impact-0.1.0-foundation-javadoc.jar", "rewrite2");
        assertFalse(isOwnJar(jar), "Javadoc JAR must not be targeted for deletion");
    }

    @Test
    void corrupted_jar_is_not_own() throws IOException {
        File f = tempDir.resolve("true_impact-broken.jar").toFile();
        f.createNewFile(); // empty file — not a valid JAR
        assertFalse(isOwnJar(f), "Corrupt/invalid JAR must not throw — returns false");
    }

    // ── isMainJar (what gets copied to mods) ─────────────────────────────────

    @Test
    void sources_jar_is_not_main() {
        assertFalse(isMainJar(new File("true_impact-0.1.0-foundation-sources.jar")));
    }

    @Test
    void javadoc_jar_is_not_main() {
        assertFalse(isMainJar(new File("true_impact-0.1.0-foundation-javadoc.jar")));
    }

    @Test
    void plain_jar_is_main() {
        assertTrue(isMainJar(new File("true_impact-0.1.0-foundation.jar")));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Mirror of the isOwnJar closure in build.gradle.
     * A JAR is "own" if it has the TI-Project-Line: rewrite2 manifest attribute
     * and is not a sources/javadoc JAR.
     */
    static boolean isOwnJar(File f) {
        if (!f.getName().startsWith("true_impact-")) return false;
        if (!f.getName().endsWith(".jar")) return false;
        if (!isMainJar(f)) return false;
        try {
            try (var jf = new java.util.jar.JarFile(f)) {
                String attr = jf.getManifest() == null ? null
                        : jf.getManifest().getMainAttributes().getValue("TI-Project-Line");
                return "rewrite2".equals(attr);
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Mirror of the isMainJar predicate in build.gradle.
     * Returns false for sources/javadoc JARs.
     */
    static boolean isMainJar(File f) {
        String name = f.getName();
        return !name.contains("-sources") && !name.contains("-javadoc");
    }

    private File jar(String name, String tiProjectLine) throws IOException {
        File f = tempDir.resolve(name).toFile();
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (tiProjectLine != null) {
            mf.getMainAttributes().putValue("TI-Project-Line", tiProjectLine);
        }
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(f), mf)) {
            // intentionally empty JAR body
        }
        return f;
    }
}
