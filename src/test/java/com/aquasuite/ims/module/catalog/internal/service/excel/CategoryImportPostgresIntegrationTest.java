package com.aquasuite.ims.module.catalog.internal.service.excel;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.excel.storage.TempExcelFileManager;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class CategoryImportPostgresIntegrationTest extends PostgreSqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired com.aquasuite.ims.module.catalog.internal.controller.CategoryImportController controller;
    @Autowired com.aquasuite.ims.module.catalog.internal.service.CategoryService categoryService;

    @Test void templateRequiresCategoryCreateAuthority() {
        try {
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("actor", "n/a", java.util.List.of()));
            assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> controller.template());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("actor", "n/a",
                            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("CATALOG_CATEGORY_CREATE"))));
            assertEquals(200, controller.template().getStatusCode().value());
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @SuppressWarnings("unchecked")
    private CategoryImportService service(java.util.concurrent.Executor executor) {
        return service(jdbc, executor);
    }

    @SuppressWarnings("unchecked")
    private CategoryImportService service(JdbcTemplate template, java.util.concurrent.Executor executor) {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        IamApi iam = mock(IamApi.class);
        when(iam.getCurrentUserId()).thenReturn(UUID.randomUUID());
        return new CategoryImportService(redis, new TempExcelFileManager(), template, transactions, iam, executor);
    }

    private MockMultipartFile upload(String[]... lines) throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet();
            String[] headers = {"code", "name", "parent_code", "description", "status"};
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            for (int r = 0; r < lines.length; r++) {
                var row = sheet.createRow(r + 1);
                for (int c = 0; c < lines[r].length; c++) row.createCell(c).setCellValue(lines[r][c]);
            }
            book.write(out);
            return new MockMultipartFile("file", "categories.xlsx", "application/octet-stream", out.toByteArray());
        }
    }

    @Test void sameFileParentIsNotAllowedButExistingInactiveParentIsAllowed() throws Exception {
        String prefix = "C17_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String existing = prefix + "_P", root = prefix + "_ROOT", child = prefix + "_CHILD", later = prefix + "_LATER";
        UUID parentId = UUID.randomUUID();
        jdbc.update("insert into public.categories(category_id,category_code,category_name,status) values (?,?,?,?)", parentId, existing, prefix + " parent", "INACTIVE");
        try {
            CategoryImportJob job = service(Runnable::run).submit(upload(
                    new String[]{root, prefix + " root", "", "", ""},
                    new String[]{child, prefix + " child", existing, "", "ACTIVE"},
                    new String[]{later, prefix + " later", root, "", "ACTIVE"}));
            assertEquals("COMPLETED_WITH_ERRORS", job.status);
            assertEquals(2, job.createdRows);
            assertEquals(1, job.errorRows);
            assertEquals("PARENT_NOT_FOUND", job.rows.get(0).reasonCodes());
            assertEquals(parentId, jdbc.queryForObject("select parent_id from public.categories where category_code=?", UUID.class, child));
        } finally {
            jdbc.update("delete from public.categories where category_code in (?,?,?)", child, root, later);
            jdbc.update("delete from public.categories where category_id=?", parentId);
        }
    }

    @Test void sameNameIsAllowedUnderDifferentParentsButSkippedUnderSameParent() throws Exception {
        String prefix = "C17_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        jdbc.update("insert into public.categories(category_id,category_code,category_name,status) values (?,?,?,?)", first, prefix + "_P1", prefix + " p1", "ACTIVE");
        jdbc.update("insert into public.categories(category_id,category_code,category_name,status) values (?,?,?,?)", second, prefix + "_P2", prefix + " p2", "ACTIVE");
        try {
            CategoryImportJob job = service(Runnable::run).submit(upload(
                    new String[]{prefix + "_A", prefix + " name", prefix + "_P1", "", ""},
                    new String[]{prefix + "_B", prefix + " name", prefix + "_P2", "", ""},
                    new String[]{prefix + "_C", prefix + " name", prefix + "_P1", "", ""}));
            assertEquals(2, job.createdRows);
            assertEquals(1, job.skippedRows);
            assertEquals("FILE_DUPLICATE_NAME", job.rows.get(0).reasonCodes());
            assertEquals(2, job.rows.get(0).firstNameRow());
        } finally {
            jdbc.update("delete from public.categories where category_code in (?,?,?)", prefix + "_A", prefix + "_B", prefix + "_C");
            jdbc.update("delete from public.categories where category_id in (?,?)", first, second);
        }
    }

    @Test void parentCreatedAfterSnapshotIsNotAccepted() throws Exception {
        String prefix = "C17_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        AtomicReference<Runnable> pending = new AtomicReference<>();
        CategoryImportJob job = service(pending::set).submit(upload(new String[]{prefix + "_CHILD", prefix + " child", prefix + "_P", "", ""}));
        UUID parent = UUID.randomUUID();
        jdbc.update("insert into public.categories(category_id,category_code,category_name,status) values (?,?,?,?)", parent, prefix + "_P", prefix + " parent", "ACTIVE");
        try {
            pending.get().run();
            assertEquals(0, job.createdRows);
            assertEquals("PARENT_NOT_FOUND", job.rows.get(0).reasonCodes());
        } finally { jdbc.update("delete from public.categories where category_id=?", parent); }
    }

    @Test void snapshotLockSerializesConcurrentCategoryCreate() throws Exception {
        String prefix = "C17_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        var queryReached = new java.util.concurrent.CountDownLatch(1);
        var releaseSnapshot = new java.util.concurrent.CountDownLatch(1);
        var crudStarted = new java.util.concurrent.CountDownLatch(1);
        var pending = new AtomicReference<Runnable>();
        JdbcTemplate intercept = new JdbcTemplate(dataSource) {
            @Override public void query(String sql, org.springframework.jdbc.core.RowCallbackHandler callback) {
                if (sql.equals("select category_code, category_id from public.categories")) {
                    queryReached.countDown();
                    try {
                        if (!releaseSnapshot.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("snapshot timeout");
                    } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                }
                super.query(sql, callback);
            }
        };
        var threads = Executors.newFixedThreadPool(2);
        try {
            var upload = upload(new String[]{prefix + "_CHILD", prefix + " child", prefix + "_P", "", ""});
            var jobFuture = threads.submit(() -> service(intercept, pending::set).submit(upload));
            assertTrue(queryReached.await(10, TimeUnit.SECONDS));
            var parentFuture = threads.submit(() -> {
                crudStarted.countDown();
                return categoryService.createCategory(com.aquasuite.ims.module.catalog.internal.payload.req.CreateCategoryRequest.builder()
                        .code(prefix + "_P").name(prefix + " parent").build());
            });
            assertTrue(crudStarted.await(5, TimeUnit.SECONDS));
            assertTrue(waitForHierarchyLockWaiter(), "CRUD must be waiting on the category advisory lock");
            assertFalse(parentFuture.isDone());
            releaseSnapshot.countDown();
            CategoryImportJob job = jobFuture.get(10, TimeUnit.SECONDS);
            parentFuture.get(10, TimeUnit.SECONDS);
            pending.get().run();
            assertEquals(0, job.createdRows);
            assertEquals("PARENT_NOT_FOUND", job.rows.get(0).reasonCodes());
        } finally {
            releaseSnapshot.countDown();
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
            jdbc.update("delete from public.categories where category_code in (?,?)", prefix + "_CHILD", prefix + "_P");
        }
    }

    @Test void workerLockSerializesConcurrentParentDeactivation() throws Exception {
        String prefix = "C17_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        UUID parent = UUID.randomUUID();
        jdbc.update("insert into public.categories(category_id,category_code,category_name,status) values (?,?,?,?)",
                parent, prefix + "_P", prefix + " parent", "ACTIVE");
        var parentRead = new java.util.concurrent.CountDownLatch(1);
        var releaseWorker = new java.util.concurrent.CountDownLatch(1);
        var updateStarted = new java.util.concurrent.CountDownLatch(1);
        var pending = new AtomicReference<Runnable>();
        JdbcTemplate intercept = new JdbcTemplate(dataSource) {
            @Override public java.util.List<java.util.Map<String,Object>> queryForList(String sql, Object... args) {
                if (sql.equals("select category_code, parent_id from public.categories where category_id=?")) {
                    parentRead.countDown();
                    try {
                        if (!releaseWorker.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("worker timeout");
                    } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                }
                return super.queryForList(sql, args);
            }
        };
        var threads = Executors.newFixedThreadPool(2);
        try {
            CategoryImportJob job = service(intercept, pending::set).submit(upload(
                    new String[]{prefix + "_CHILD", prefix + " child", prefix + "_P", "", ""}));
            var worker = threads.submit(pending.get());
            assertTrue(parentRead.await(10, TimeUnit.SECONDS));
            var updater = threads.submit(() -> {
                updateStarted.countDown();
                return categoryService.updateCategory(parent,
                        com.aquasuite.ims.module.catalog.internal.payload.req.UpdateCategoryRequest.builder()
                                .name(prefix + " parent").status("INACTIVE").build());
            });
            assertTrue(updateStarted.await(5, TimeUnit.SECONDS));
            assertTrue(waitForHierarchyLockWaiter(), "CRUD must be waiting on the category advisory lock");
            assertFalse(updater.isDone());
            releaseWorker.countDown();
            worker.get(10, TimeUnit.SECONDS);
            updater.get(10, TimeUnit.SECONDS);
            assertEquals(1, job.createdRows);
            assertEquals("INACTIVE", jdbc.queryForObject("select status from public.categories where category_id=?", String.class, parent));
        } finally {
            releaseWorker.countDown();
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(10, TimeUnit.SECONDS));
            jdbc.update("delete from public.categories where category_code=?", prefix + "_CHILD");
            jdbc.update("delete from public.categories where category_id=?", parent);
        }
    }

    @Test void deletedSnapshotParentIsRejected() throws Exception {
        String prefix = "C17_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        UUID parent = UUID.randomUUID();
        jdbc.update("insert into public.categories(category_id,category_code,category_name,status) values (?,?,?,?)",
                parent, prefix + "_P", prefix + " parent", "ACTIVE");
        AtomicReference<Runnable> pending = new AtomicReference<>();
        CategoryImportJob job = service(pending::set).submit(upload(
                new String[]{prefix + "_CHILD", prefix + " child", prefix + "_P", "", ""}));
        jdbc.update("delete from public.categories where category_id=?", parent);
        pending.get().run();
        assertEquals(0, job.createdRows);
        assertEquals("PARENT_NOT_FOUND_OR_INVALID", job.rows.get(0).reasonCodes());
    }

    @Test void rejectedDispatchMarksJobFailed() throws Exception {
        CategoryImportJob job = service(task -> { throw new java.util.concurrent.RejectedExecutionException(); })
                .submit(upload(new String[]{"CAT_VALID", "Valid", "", "", ""}));
        assertEquals("FAILED", job.status);
        assertEquals("PROCESSING_FAILED", job.jobReason);
    }

    private boolean waitForHierarchyLockWaiter() throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            Boolean waiting = jdbc.queryForObject("select exists(select 1 from pg_locks where locktype='advisory' "
                    + "and classid=1128354892::oid and objid=1::oid and not granted)", Boolean.class);
            if (Boolean.TRUE.equals(waiting)) return true;
            Thread.sleep(50);
        }
        return false;
    }

    @Test void concurrentJobsCreateOnlyOneCategory() throws Exception {
        String code = "C17_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = service(workers).submit(upload(new String[]{code, code + " name", "", "", ""}));
            var second = service(workers).submit(upload(new String[]{code, code + " name", "", "", ""}));
            workers.shutdown();
            assertTrue(workers.awaitTermination(30, TimeUnit.SECONDS));
            assertEquals(1, first.createdRows + second.createdRows);
            assertEquals(1, first.skippedRows + second.skippedRows);
            assertEquals(1, jdbc.queryForObject("select count(*) from public.categories where category_code=?", Integer.class, code));
        } finally {
            workers.shutdownNow();
            jdbc.update("delete from public.categories where category_code=?", code);
        }
    }

    @Test void failedInsertRollsBackAndDoesNotIncrementCreated() throws Exception {
        String code = "C17_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        JdbcTemplate failing = new JdbcTemplate(dataSource) {
            @Override public int update(String sql, Object... args) {
                int count = super.update(sql, args);
                if (sql.startsWith("insert into public.categories"))
                    throw new org.springframework.dao.DataAccessResourceFailureException("injected failure after insert");
                return count;
            }
        };
        CategoryImportJob job = service(failing, Runnable::run).submit(upload(new String[]{code, code + " name", "", "", ""}));
        assertEquals("FAILED", job.status);
        assertEquals("PROCESSING_FAILED", job.jobReason);
        assertEquals(0, job.createdRows);
        assertEquals(0, jdbc.queryForObject("select count(*) from public.categories where category_code=?", Integer.class, code));
    }

    @Test void duplicateCodeReservesNewNameAndReportsBothFirstRows() throws Exception {
        String prefix = "C17_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String a = prefix + "_A", b = prefix + "_B";
        try {
            CategoryImportJob job = service(Runnable::run).submit(upload(
                    new String[]{a, prefix + " first", "", "", ""},
                    new String[]{a, prefix + " second", "", "", ""},
                    new String[]{b, prefix + " second", "", "", ""}));
            assertEquals(1, job.createdRows);
            assertEquals(2, job.skippedRows);
            assertEquals("FILE_DUPLICATE_CODE", job.rows.get(0).reasonCodes());
            assertEquals(2, job.rows.get(0).firstCodeRow());
            assertEquals("FILE_DUPLICATE_NAME", job.rows.get(1).reasonCodes());
            assertEquals(3, job.rows.get(1).firstNameRow());
        } finally { jdbc.update("delete from public.categories where category_code in (?,?)", a, b); }
    }

    @Test void hashCollisionsUsePostgresEqualityInsteadOfSkippingDifferentValues() throws Exception {
        String prefix = "C17_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        JdbcTemplate collisions = new JdbcTemplate(dataSource) {
            @Override public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
                if (sql.startsWith("select hashtextextended")) return requiredType.cast(1L);
                return super.queryForObject(sql, requiredType, args);
            }
        };
        try {
            CategoryImportJob job = service(collisions, Runnable::run).submit(upload(
                    new String[]{prefix + "_A", prefix + " first", "", "", ""},
                    new String[]{prefix + "_B", prefix + " second", "", "", ""},
                    new String[]{prefix + "_C", prefix + " SECOND", "", "", ""}));
            assertEquals(2, job.createdRows);
            assertEquals(1, job.skippedRows);
            assertEquals("FILE_DUPLICATE_NAME", job.rows.get(0).reasonCodes());
            assertEquals(3, job.rows.get(0).firstNameRow());
        } finally { jdbc.update("delete from public.categories where category_code in (?,?,?)", prefix + "_A", prefix + "_B", prefix + "_C"); }
    }

    @SuppressWarnings("unchecked")
    @Test void redisFailureAfterCommitKeepsCreatedCategory() throws Exception {
        String code = "C17_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            if (writes.incrementAndGet() == 4)
                throw new org.springframework.dao.DataAccessResourceFailureException("injected Redis failure");
            return null;
        }).when(values).set(anyString(), any(), any());
        IamApi iam = mock(IamApi.class);
        when(iam.getCurrentUserId()).thenReturn(UUID.randomUUID());
        try {
            CategoryImportJob job = new CategoryImportService(redis, new TempExcelFileManager(), jdbc,
                    transactions, iam, Runnable::run).submit(upload(new String[]{code, code + " name", "", "", ""}));
            assertEquals("FAILED", job.status);
            assertEquals("PROCESSING_FAILED", job.jobReason);
            assertEquals(1, jdbc.queryForObject("select count(*) from public.categories where category_code=?", Integer.class, code));
        } finally { jdbc.update("delete from public.categories where category_code=?", code); }
    }

    @Test void corruptWorkbookFailsAfterJobHasBeenCreated() {
        var job = service(Runnable::run).submit(new MockMultipartFile("file", "broken.xlsx",
                "application/octet-stream", new byte[]{1, 2, 3}));
        assertNotNull(job.jobId);
        assertEquals("FAILED", job.status);
        assertEquals("INVALID_FILE", job.jobReason);
        assertEquals(0, job.createdRows);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "category.capacity.test", matches = "true")
    void capacityTenThousandDistinctRootCategories() throws Exception {
        String prefix = "C17_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String[][] lines = new String[10_000][5];
        for (int i = 0; i < lines.length; i++) lines[i] = new String[]{prefix + "_" + i, prefix + " name " + i, "", "", ""};
        var redisContainer = new org.testcontainers.containers.GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379);
        redisContainer.start();
        var redisFactory = new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
                redisContainer.getHost(), redisContainer.getMappedPort(6379));
        redisFactory.afterPropertiesSet();
        var realRedis = new com.aquasuite.ims.share.config.redis.RedisConfig().redisTemplate(redisFactory);
        IamApi iam = mock(IamApi.class);
        when(iam.getCurrentUserId()).thenReturn(UUID.randomUUID());
        long heapBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        var peakHeap = new java.util.concurrent.atomic.AtomicLong(heapBefore);
        var sampler = Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(() -> peakHeap.accumulateAndGet(
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(), Math::max), 0, 100, TimeUnit.MILLISECONDS);
        var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        long cpuStart = os instanceof com.sun.management.OperatingSystemMXBean sun ? sun.getProcessCpuTime() : -1;
        long start = System.nanoTime();
        try {
            CategoryImportJob job = new CategoryImportService(realRedis, new TempExcelFileManager(), jdbc,
                    transactions, iam, Runnable::run).submit(upload(lines));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            long heapAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long cpuEnd = os instanceof com.sun.management.OperatingSystemMXBean sun ? sun.getProcessCpuTime() : -1;
            System.out.println("CATEGORY_CAPACITY_ROWS=10000 ELAPSED_MS=" + elapsedMs + " CPU_NS=" + (cpuStart < 0 ? -1 : cpuEnd - cpuStart)
                    + " HEAP_BEFORE=" + heapBefore + " HEAP_AFTER=" + heapAfter + " HEAP_PEAK_SAMPLED=" + peakHeap.get());
            assertEquals("COMPLETED", job.status);
            assertEquals(10_000, job.createdRows);
        } finally {
            sampler.shutdownNow();
            jdbc.update("delete from public.categories where left(category_code, length(?)) = ?", prefix + "_", prefix + "_");
            redisFactory.destroy();
            redisContainer.stop();
        }
    }
}
