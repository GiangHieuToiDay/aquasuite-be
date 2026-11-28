package com.aquasuite.ims.repository.iam.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RotateRefreshLuaScriptTest {

    private static final List<String> SESSION_LUA_SCRIPTS = List.of(
            "redis/create-session.lua",
            "redis/validate-session.lua",
            "redis/rotate-refresh.lua",
            "redis/revoke-session.lua"
    );

    private static final List<String> REDIS_6_PLUS_ONLY_TOKENS = List.of(
            "KEEPTTL",
            "GETDEL",
            "GETEX",
            "EXAT",
            "PXAT",
            "UNLINK"
    );

    @Test
    @DisplayName("All session Lua scripts stay compatible with Redis 3.0+ (no KEEPTTL or Redis 6+ flags)")
    void sessionLuaScriptsDoNotUseRedis6PlusOnlyFlags() throws IOException {
        for (String path : SESSION_LUA_SCRIPTS) {
            String executableCode = readScript(path).replaceAll("(?m)--.*$", "");
            String upper = executableCode.toUpperCase();
            for (String forbidden : REDIS_6_PLUS_ONLY_TOKENS) {
                assertThat(upper)
                        .as("Script %s must not contain Redis 6+-only token %s", path, forbidden)
                        .doesNotContain(forbidden);
            }
        }
    }

    @Test
    @DisplayName("rotate-refresh.lua preserves session TTL via explicit PX sessionPttl instead of KEEPTTL")
    void rotateRefreshScriptUpdatesSessionWithExplicitPxTtl() throws IOException {
        String script = readScript("redis/rotate-refresh.lua");

        assertThat(script)
                .contains("redis.call('SET', sessionKey, cjson.encode(session), 'PX', sessionPttl)");
        assertThat(script)
                .contains("redis.call('SET', usedOldKey, cjson.encode(usedData), 'PX', markerPttl)");
    }

    private String readScript(String classpathLocation) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
