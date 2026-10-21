package com.aquasuite.ims.testsupport;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@ActiveProfiles("test")
public abstract class AuthIntegrationTestSupport extends PostgreSqlIntegrationTestSupport {

    private static final String REDIS_IMAGE = "redis:7.2-alpine";
    private static final GenericContainer<?> redisContainer;
    private static final String EXTERNAL_REDIS_HOST = System.getenv("TEST_REDIS_HOST");
    private static final String EXTERNAL_REDIS_PORT = System.getenv("TEST_REDIS_PORT");

    private static final RespTransportFaultProxy faultProxy;
    private static StringRedisTemplate directControlTemplate;

    static {
        if (EXTERNAL_REDIS_HOST != null
                && !EXTERNAL_REDIS_HOST.isBlank()
                && EXTERNAL_REDIS_PORT != null
                && !EXTERNAL_REDIS_PORT.isBlank()) {
            redisContainer = null;
            faultProxy =
                    new RespTransportFaultProxy(
                            EXTERNAL_REDIS_HOST, Integer.parseInt(EXTERNAL_REDIS_PORT));
        } else {
            redisContainer =
                    new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                            .withExposedPorts(6379);
            redisContainer.start();
            faultProxy =
                    new RespTransportFaultProxy(
                            redisContainer.getHost(), redisContainer.getMappedPort(6379));
        }
        faultProxy.start();
    }

    @DynamicPropertySource
    static void configureRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", faultProxy::getListeningPort);
        registry.add("spring.data.redis.ssl.enabled", () -> "false");
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.data.redis.timeout", () -> "3000ms");

        registry.add(
                "aquasuite.security.jwt.secret",
                () ->
                        "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtNjQtYnl0ZXMtbG9uZy1zby10aGlzLXNob3VsZC13b3JrIQ==");
    }

    public static GenericContainer<?> getRedisContainer() {
        return redisContainer;
    }

    public static RespTransportFaultProxy getFaultProxy() {
        return faultProxy;
    }

    public static synchronized StringRedisTemplate getDirectControlTemplate() {
        if (directControlTemplate == null) {
            String host = redisContainer != null ? redisContainer.getHost() : EXTERNAL_REDIS_HOST;
            int port =
                    redisContainer != null
                            ? redisContainer.getMappedPort(6379)
                            : Integer.parseInt(EXTERNAL_REDIS_PORT);
            LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);
            factory.afterPropertiesSet();
            directControlTemplate = new StringRedisTemplate(factory);
        }
        return directControlTemplate;
    }

    public static String computeSha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public enum FaultMode {
        PASS_THROUGH,
        BEFORE_FORWARD,
        AFTER_FORWARD_BEFORE_REPLY,
        AFTER_BACKEND_REPLY
    }

    public static class ProxyEventRecord {
        public volatile boolean targetMatched;
        public volatile boolean forwarded;
        public volatile boolean backendReply;
        public volatile boolean clientReplyWithheld;
        public volatile boolean clientClosedBeforeReplyObservation;
        public volatile boolean backendReplyComplete;
        public volatile int duplicateTargetCount;
        public volatile String commandKind;
        public volatile String targetScriptSha;
        public volatile int frameByteCount;
    }

    public static class RespTransportFaultProxy {
        private final String backendHost;
        private final int backendPort;
        private ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final AtomicBoolean running = new AtomicBoolean(false);

        private volatile FaultMode mode = FaultMode.PASS_THROUGH;
        private volatile String targetScriptSha = null;
        private final AtomicInteger targetExecutionCount = new AtomicInteger(0);
        private final List<ProxyEventRecord> recordedEvents = new CopyOnWriteArrayList<>();

        public RespTransportFaultProxy(String backendHost, int backendPort) {
            this.backendHost = backendHost;
            this.backendPort = backendPort;
        }

        public synchronized void start() {
            if (running.get()) return;
            try {
                serverSocket = new ServerSocket(0);
                running.set(true);
                executor.submit(this::acceptConnections);
            } catch (IOException e) {
                throw new RuntimeException("Failed to bind proxy ServerSocket", e);
            }
        }

        public int getListeningPort() {
            return serverSocket != null ? serverSocket.getLocalPort() : -1;
        }

        public void armFault(FaultMode faultMode, String scriptSha) {
            this.mode = faultMode;
            this.targetScriptSha = scriptSha != null ? scriptSha.toLowerCase() : null;
            this.targetExecutionCount.set(0);
            this.recordedEvents.clear();
        }

        public void disarm() {
            this.mode = FaultMode.PASS_THROUGH;
            this.targetScriptSha = null;
            this.targetExecutionCount.set(0);
        }

        public FaultMode getMode() {
            return mode;
        }

        public List<ProxyEventRecord> getRecordedEvents() {
            return Collections.unmodifiableList(recordedEvents);
        }

        public int getTargetExecutionCount() {
            return targetExecutionCount.get();
        }

        private void acceptConnections() {
            while (running.get() && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                    Socket backendSocket = new Socket(backendHost, backendPort);
                    backendSocket.setTcpNoDelay(true);

                    executor.submit(() -> handleClientToBackend(clientSocket, backendSocket));
                    executor.submit(() -> handleBackendToClient(clientSocket, backendSocket));
                } catch (IOException e) {
                    if (!running.get()) break;
                }
            }
        }

        private void handleClientToBackend(Socket clientSocket, Socket backendSocket) {
            try (InputStream clientIn = clientSocket.getInputStream();
                    OutputStream backendOut = backendSocket.getOutputStream()) {

                while (running.get() && !clientSocket.isClosed() && !backendSocket.isClosed()) {
                    byte[] frame = readNextRespCommandFrame(clientIn);
                    if (frame == null || frame.length == 0) {
                        break;
                    }

                    List<String> commandArgs = parseRespArrayStrings(frame);
                    String commandName =
                            !commandArgs.isEmpty() ? commandArgs.get(0).toUpperCase() : "";

                    boolean isTarget = false;
                    String targetSha = this.targetScriptSha;
                    if (targetSha != null) {
                        if ("EVALSHA".equals(commandName) && commandArgs.size() > 1) {
                            String sha = commandArgs.get(1).toLowerCase();
                            if (targetSha.equals(sha)) {
                                isTarget = true;
                            }
                        } else if ("EVAL".equals(commandName) && commandArgs.size() > 1) {
                            String sha = computeSha1Hex(commandArgs.get(1));
                            if (targetSha.equals(sha)) {
                                isTarget = true;
                            }
                        }
                    } else {
                        if ("EVALSHA".equals(commandName)
                                || "EVAL".equals(commandName)
                                || "SET".equals(commandName)
                                || "GET".equals(commandName)
                                || "DEL".equals(commandName)) {
                            isTarget = true;
                        }
                    }

                    if (isTarget && mode != FaultMode.PASS_THROUGH) {
                        int count = targetExecutionCount.incrementAndGet();
                        ProxyEventRecord event = new ProxyEventRecord();
                        event.targetMatched = true;
                        event.commandKind = commandName;
                        event.targetScriptSha = targetSha;
                        event.frameByteCount = frame.length;
                        event.duplicateTargetCount = count;
                        recordedEvents.add(event);

                        if (mode == FaultMode.BEFORE_FORWARD) {
                            event.forwarded = false;
                            event.backendReply = false;
                            event.clientReplyWithheld = true;
                            closeQuietly(clientSocket);
                            closeQuietly(backendSocket);
                            return;
                        } else if (mode == FaultMode.AFTER_FORWARD_BEFORE_REPLY) {
                            backendOut.write(frame);
                            backendOut.flush();
                            event.forwarded = true;
                            event.clientClosedBeforeReplyObservation = true;
                            closeQuietly(clientSocket);
                            return;
                        } else if (mode == FaultMode.AFTER_BACKEND_REPLY) {
                            backendOut.write(frame);
                            backendOut.flush();
                            event.forwarded = true;
                            // Thread 2 (Backend -> Client) will withhold the reply
                            clientSocket.setSoTimeout(5000);
                        }
                    } else {
                        backendOut.write(frame);
                        backendOut.flush();
                    }
                }
            } catch (Exception ignored) {
            } finally {
                closeQuietly(clientSocket);
                closeQuietly(backendSocket);
            }
        }

        private void handleBackendToClient(Socket clientSocket, Socket backendSocket) {
            try (InputStream backendIn = backendSocket.getInputStream();
                    OutputStream clientOut = clientSocket.getOutputStream()) {

                byte[] buffer = new byte[8192];
                while (running.get() && !backendSocket.isClosed() && !clientSocket.isClosed()) {
                    int read = backendIn.read(buffer);
                    if (read == -1) break;

                    if (mode == FaultMode.AFTER_BACKEND_REPLY && !recordedEvents.isEmpty()) {
                        ProxyEventRecord lastEvent = recordedEvents.get(recordedEvents.size() - 1);
                        if (lastEvent.targetMatched
                                && lastEvent.forwarded
                                && !lastEvent.backendReplyComplete) {
                            lastEvent.backendReplyComplete = true;
                            lastEvent.backendReply = true;
                            lastEvent.clientReplyWithheld = true;
                            closeQuietly(clientSocket);
                            return;
                        }
                    }

                    clientOut.write(buffer, 0, read);
                    clientOut.flush();
                }
            } catch (Exception ignored) {
            } finally {
                closeQuietly(clientSocket);
                closeQuietly(backendSocket);
            }
        }

        private void closeQuietly(Socket socket) {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception ignored) {
            }
        }

        private byte[] readNextRespCommandFrame(InputStream in) throws IOException {
            int first = in.read();
            if (first == -1) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(first);

            if (first == '*') {
                String line = readLine(in, baos);
                int count = Integer.parseInt(line.trim());
                for (int i = 0; i < count; i++) {
                    int prefix = in.read();
                    if (prefix == -1) break;
                    baos.write(prefix);
                    if (prefix == '$') {
                        String lenStr = readLine(in, baos);
                        int len = Integer.parseInt(lenStr.trim());
                        if (len >= 0) {
                            byte[] data = in.readNBytes(len);
                            baos.write(data);
                            int cr = in.read();
                            int lf = in.read();
                            if (cr != -1) baos.write(cr);
                            if (lf != -1) baos.write(lf);
                        }
                    } else if (prefix == '+' || prefix == '-' || prefix == ':') {
                        readLine(in, baos);
                    }
                }
            } else {
                readLine(in, baos);
            }
            return baos.toByteArray();
        }

        private String readLine(InputStream in, ByteArrayOutputStream mirror) throws IOException {
            StringBuilder sb = new StringBuilder();
            int prev = -1;
            while (true) {
                int b = in.read();
                if (b == -1) break;
                mirror.write(b);
                if (prev == '\r' && b == '\n') {
                    break;
                }
                if (b != '\r') {
                    sb.append((char) b);
                }
                prev = b;
            }
            return sb.toString();
        }

        private List<String> parseRespArrayStrings(byte[] frame) {
            List<String> result = new ArrayList<>();
            if (frame == null || frame.length == 0 || frame[0] != '*') {
                return result;
            }
            String s = new String(frame, StandardCharsets.UTF_8);
            String[] lines = s.split("\r\n");
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].startsWith("$")) {
                    if (i + 1 < lines.length) {
                        result.add(lines[i + 1]);
                        i++;
                    }
                }
            }
            return result;
        }
    }
}
