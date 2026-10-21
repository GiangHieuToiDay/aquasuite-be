package com.aquasuite.ims.module.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogArchitectureBoundaryTest {

    private static final String FORBIDDEN_IMPORT_PREFIX = "com.aquasuite.ims.module.iam.internal.";

    @Test
    @DisplayName("Architecture Boundary: Catalog module (main and test) must not import IAM internal classes")
    void testNoIamInternalImportsInCatalogModule() throws IOException, URISyntaxException {
        Path projectRoot = resolveProjectRoot();

        // Fail-closed checks on project structure
        Path pomXml = projectRoot.resolve("pom.xml");
        assertThat(pomXml)
                .withFailMessage("pom.xml must exist at resolved project root: %s", projectRoot)
                .exists()
                .isRegularFile();

        Path mainJavaRoot = projectRoot.resolve(Paths.get("src", "main", "java"));
        Path testJavaRoot = projectRoot.resolve(Paths.get("src", "test", "java"));
        assertThat(mainJavaRoot).withFailMessage("src/main/java must exist at %s", mainJavaRoot).exists().isDirectory();
        assertThat(testJavaRoot).withFailMessage("src/test/java must exist at %s", testJavaRoot).exists().isDirectory();

        Path catalogMainPath = mainJavaRoot.resolve(Paths.get("com", "aquasuite", "ims", "module", "catalog"));
        Path catalogTestPath = testJavaRoot.resolve(Paths.get("com", "aquasuite", "ims", "module", "catalog"));
        assertThat(catalogMainPath).withFailMessage("Catalog main root must exist at %s", catalogMainPath).exists().isDirectory();
        assertThat(catalogTestPath).withFailMessage("Catalog test root must exist at %s", catalogTestPath).exists().isDirectory();

        List<Path> offendingFiles = new ArrayList<>();
        int mainScannedCount = scanDirectoryForForbiddenImports(catalogMainPath, offendingFiles);
        int testScannedCount = scanDirectoryForForbiddenImports(catalogTestPath, offendingFiles);

        // Fail-closed assertions: must have scanned files
        assertThat(mainScannedCount)
                .withFailMessage("Expected at least one Java file in catalog main source root %s, but scanned 0 files", catalogMainPath)
                .isGreaterThan(0);
        assertThat(testScannedCount)
                .withFailMessage("Expected at least one Java file in catalog test source root %s, but scanned 0 files", catalogTestPath)
                .isGreaterThan(0);

        // Assert zero boundary violations
        assertThat(offendingFiles)
                .withFailMessage("The following Catalog files violate module boundary by importing IAM internal classes: %s", offendingFiles)
                .isEmpty();
    }

    @Test
    @DisplayName("Architecture Boundary: Guard must detect forbidden import in synthetic source tree")
    void testGuardDetectsForbiddenImportInSyntheticSource(@TempDir Path tempDir) throws IOException {
        Path dummyFile = tempDir.resolve("DummyClass.java");
        Files.writeString(dummyFile, "package dummy;\nimport com.aquasuite.ims.module.iam.internal.entity.User;\npublic class DummyClass {}\n");

        List<Path> offending = new ArrayList<>();
        int scanned = scanDirectoryForForbiddenImports(tempDir, offending);

        assertThat(scanned).isEqualTo(1);
        assertThat(offending).containsExactly(dummyFile);
    }

    private Path resolveProjectRoot() throws URISyntaxException {
        Path testClassLocation = Paths.get(
                CatalogArchitectureBoundaryTest.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI()
        );

        Path current = testClassLocation;
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.exists(pom) && Files.isRegularFile(pom)) {
                return current;
            }
            current = current.getParent();
        }

        // Fallback: target -> projectRoot
        if (testClassLocation.getParent() != null && testClassLocation.getParent().getParent() != null) {
            return testClassLocation.getParent().getParent();
        }

        throw new IllegalStateException("Could not resolve project root containing pom.xml from: " + testClassLocation);
    }

    private int scanDirectoryForForbiddenImports(Path dir, List<Path> offendingFiles) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IllegalStateException("Scan root directory does not exist or is not a directory: " + dir);
        }

        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(dir)) {
            javaFiles = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }

        for (Path file : javaFiles) {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import") && trimmed.contains(FORBIDDEN_IMPORT_PREFIX)) {
                    offendingFiles.add(file);
                    break;
                }
            }
        }

        return javaFiles.size();
    }
}
