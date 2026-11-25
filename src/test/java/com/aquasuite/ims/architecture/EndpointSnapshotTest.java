package com.aquasuite.ims.architecture;

import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EndpointSnapshotTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void dumpEndpoints() throws IOException {
        List<String> lines = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .map(RequestMappingInfo::toString)
                .sorted()
                .toList();

        Path targetDir = Path.of("target");
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        Path out = targetDir.resolve("endpoints.txt");
        Files.write(out, lines);
        System.out.println("=== Endpoint Snapshot: " + lines.size() + " entries written to " + out.toAbsolutePath() + " ===");
    }
}
