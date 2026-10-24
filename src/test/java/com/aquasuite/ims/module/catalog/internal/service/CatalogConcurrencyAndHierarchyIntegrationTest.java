package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.error.CatalogErrorCode;
import com.aquasuite.ims.module.catalog.internal.payload.req.CreateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.CreateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryResponse;
import com.aquasuite.ims.module.catalog.internal.repository.CategoryRepository;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CatalogConcurrencyAndHierarchyIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void cleanData() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            try { stmt.executeUpdate("DELETE FROM public.bundle_items"); } catch (Exception ignored) {}
            try { stmt.executeUpdate("DELETE FROM public.bundle_configurations"); } catch (Exception ignored) {}
            try { stmt.executeUpdate("DELETE FROM public.product_images"); } catch (Exception ignored) {}
            try { stmt.executeUpdate("DELETE FROM public.product_variants"); } catch (Exception ignored) {}
            try { stmt.executeUpdate("DELETE FROM public.products"); } catch (Exception ignored) {}
            stmt.executeUpdate("DELETE FROM public.categories");
            stmt.executeUpdate("DELETE FROM public.brands");
        }
    }

    @Test
    @DisplayName("Hierarchy: Missing parent throws CATEGORY_PARENT_NOT_FOUND (CAT_009)")
    void testMissingParentThrows() {
        UUID nonExistentParentId = UUID.randomUUID();
        CreateCategoryRequest req = CreateCategoryRequest.builder()
                .code("CHILD_CAT")
                .name("Child Category")
                .parentId(nonExistentParentId)
                .build();

        assertThatThrownBy(() -> categoryService.createCategory(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_PARENT_NOT_FOUND));
    }

    @Test
    @DisplayName("Hierarchy: Self-parent on update throws CATEGORY_SELF_PARENT (CAT_010)")
    void testSelfParentThrows() {
        CategoryResponse root = categoryService.createCategory(CreateCategoryRequest.builder()
                .code("ROOT_CAT")
                .name("Root Category")
                .build());

        UpdateCategoryRequest updateReq = UpdateCategoryRequest.builder()
                .name("Root Category Updated")
                .status("ACTIVE")
                .parentId(root.getId())
                .build();

        assertThatThrownBy(() -> categoryService.updateCategory(root.getId(), updateReq))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_SELF_PARENT));
    }

    @Test
    @DisplayName("Hierarchy: Multi-level cycle detection throws CATEGORY_CYCLE (CAT_011)")
    void testMultiLevelCycleThrows() {
        // A -> B -> C
        CategoryResponse catA = categoryService.createCategory(CreateCategoryRequest.builder()
                .code("CAT_A")
                .name("Category A")
                .build());

        CategoryResponse catB = categoryService.createCategory(CreateCategoryRequest.builder()
                .code("CAT_B")
                .name("Category B")
                .parentId(catA.getId())
                .build());

        CategoryResponse catC = categoryService.createCategory(CreateCategoryRequest.builder()
                .code("CAT_C")
                .name("Category C")
                .parentId(catB.getId())
                .build());

        // Attempt to reparent A to C (A -> B -> C -> A cycle)
        UpdateCategoryRequest reparentA = UpdateCategoryRequest.builder()
                .name("Category A")
                .status("ACTIVE")
                .parentId(catC.getId())
                .build();

        assertThatThrownBy(() -> categoryService.updateCategory(catA.getId(), reparentA))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_CYCLE));

        // Attempt to reparent B to C (B -> C -> B cycle)
        UpdateCategoryRequest reparentB = UpdateCategoryRequest.builder()
                .name("Category B")
                .status("ACTIVE")
                .parentId(catC.getId())
                .build();

        assertThatThrownBy(() -> categoryService.updateCategory(catB.getId(), reparentB))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_CYCLE));
    }

    @Test
    @DisplayName("Hierarchy: Corrupt cycle in existing data throws CATEGORY_HIERARCHY_CONFLICT (CAT_012)")
    void testCorruptCycleInExistingDataThrows() throws Exception {
        UUID xId = UUID.randomUUID();
        UUID yId = UUID.randomUUID();

        // Bypass triggers/FK temporarily to simulate corrupted cycle X -> Y -> X in DB
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE public.categories DISABLE TRIGGER ALL");
            stmt.execute("ALTER TABLE public.categories DROP CONSTRAINT fk_categories_parent");
            stmt.execute("INSERT INTO public.categories (category_id, category_code, category_name, parent_id) VALUES ('" +
                    xId + "', 'CAT_X', 'Category X', '" + yId + "')");
            stmt.execute("INSERT INTO public.categories (category_id, category_code, category_name, parent_id) VALUES ('" +
                    yId + "', 'CAT_Y', 'Category Y', '" + xId + "')");
            stmt.execute("ALTER TABLE public.categories ADD CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES public.categories(category_id) ON DELETE RESTRICT");
            stmt.execute("ALTER TABLE public.categories ENABLE TRIGGER ALL");
        }

        // Now creating a child under X should detect the existing corrupted cycle
        CreateCategoryRequest req = CreateCategoryRequest.builder()
                .code("CAT_Z")
                .name("Category Z")
                .parentId(xId)
                .build();

        assertThatThrownBy(() -> categoryService.createCategory(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_HIERARCHY_CONFLICT));
    }

    @Test
    @DisplayName("Hierarchy: Delete parent with children throws CATEGORY_HAS_CHILDREN (CAT_013)")
    void testDeleteParentWithChildrenThrows() {
        CategoryResponse parent = categoryService.createCategory(CreateCategoryRequest.builder()
                .code("PARENT_CAT")
                .name("Parent Category")
                .build());

        categoryService.createCategory(CreateCategoryRequest.builder()
                .code("CHILD_CAT")
                .name("Child Category")
                .parentId(parent.getId())
                .build());

        assertThatThrownBy(() -> categoryService.deleteCategory(parent.getId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_HAS_CHILDREN));
    }

    @Test
    @DisplayName("Advisory Lock: Category mutation takes advisory lock (1128354892, 1) and releases it on transaction end")
    void testAdvisoryLockAcquiredAndReleased() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch finishTxLatch = new CountDownLatch(1);
        AtomicBoolean secondThreadTryLockResult = new AtomicBoolean(true);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: starts transaction, creates category (takes advisory lock), waits
        Future<?> f1 = executor.submit(() -> {
            tx.execute(status -> {
                categoryService.createCategory(CreateCategoryRequest.builder()
                        .code("LOCK_TEST_CAT")
                        .name("Lock Test Category")
                        .build());
                lockAcquiredLatch.countDown();

                try {
                    finishTxLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        lockAcquiredLatch.await(5, TimeUnit.SECONDS);

        // Thread 2: in another connection, test if pg_try_advisory_xact_lock(1128354892, 1) is false
        Future<?> f2 = executor.submit(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT pg_try_advisory_xact_lock(1128354892, 1)")) {
                if (rs.next()) {
                    secondThreadTryLockResult.set(rs.getBoolean(1));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        f2.get(5, TimeUnit.SECONDS);
        // The second thread must NOT be able to acquire the advisory lock while Thread 1's transaction is active!
        assertThat(secondThreadTryLockResult.get()).isFalse();

        // Release Thread 1's transaction
        finishTxLatch.countDown();
        f1.get(5, TimeUnit.SECONDS);

        // Now Thread 3: after Thread 1 completes, lock is released
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT pg_try_advisory_xact_lock(1128354892, 1)")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isTrue();
        }

        executor.shutdown();
    }

    @Test
    @DisplayName("Advisory Lock: Brand operations and Category reads are NOT blocked by Category advisory lock")
    void testBrandAndCategoryReadNotBlockedByLock() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch finishTxLatch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: holds advisory lock
        Future<?> f1 = executor.submit(() -> {
            tx.execute(status -> {
                categoryService.createCategory(CreateCategoryRequest.builder()
                        .code("HOLD_LOCK_CAT")
                        .name("Hold Lock Category")
                        .build());
                lockAcquiredLatch.countDown();

                try {
                    finishTxLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        lockAcquiredLatch.await(5, TimeUnit.SECONDS);

        // Thread 2: Brand creation can proceed concurrently without getting blocked!
        Future<?> f2 = executor.submit(() -> {
            brandService.createBrand(CreateBrandRequest.builder()
                    .code("BRAND_CONCURRENT")
                    .name("Concurrent Brand")
                    .build());
        });

        f2.get(3, TimeUnit.SECONDS); // must complete without deadlock or blocking

        // Thread 3: Category read can proceed concurrently without getting blocked!
        Future<?> f3 = executor.submit(() -> {
            categoryService.listCategories(0, 10, null, null, "code", "asc");
        });

        f3.get(3, TimeUnit.SECONDS); // must complete without blocking

        finishTxLatch.countDown();
        f1.get(5, TimeUnit.SECONDS);

        executor.shutdown();
    }

    @Test
    @DisplayName("Concurrency: Concurrent reciprocal reparent (A->B vs B->A) with ready barrier is serialized by advisory lock, rejects cycle, and leaves valid hierarchy")
    void testConcurrentReciprocalReparent() throws Exception {
        // Setup two independent root categories A and B
        CategoryResponse catA = categoryService.createCategory(CreateCategoryRequest.builder()
                .code("CAT_A")
                .name("Category A")
                .build());
        CategoryResponse catB = categoryService.createCategory(CreateCategoryRequest.builder()
                .code("CAT_B")
                .name("Category B")
                .build());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Worker 1: in independent transaction, reparent A to B (A's parent becomes B)
            TransactionTemplate tx1 = new TransactionTemplate(transactionManager);
            tx1.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Future<CategoryResponse> future1 = executor.submit(() -> tx1.execute(status -> {
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Worker 1 timed out waiting for start barrier");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return categoryService.updateCategory(catA.getId(), UpdateCategoryRequest.builder()
                        .name("Category A")
                        .status("ACTIVE")
                        .parentId(catB.getId())
                        .build());
            }));

            // Worker 2: in independent transaction, reparent B to A (B's parent becomes A)
            TransactionTemplate tx2 = new TransactionTemplate(transactionManager);
            tx2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Future<CategoryResponse> future2 = executor.submit(() -> tx2.execute(status -> {
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Worker 2 timed out waiting for start barrier");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return categoryService.updateCategory(catB.getId(), UpdateCategoryRequest.builder()
                        .name("Category B")
                        .status("ACTIVE")
                        .parentId(catA.getId())
                        .build());
            }));

            // Ensure both worker transactions are open and waiting at start gate
            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .withFailMessage("Both workers must have active transactions and be ready before start")
                    .isTrue();

            // Release start gate to commence race
            start.countDown();

            boolean task1Success = false;
            boolean task2Success = false;
            Throwable task1Error = null;
            Throwable task2Error = null;

            try {
                future1.get(10, TimeUnit.SECONDS);
                task1Success = true;
            } catch (java.util.concurrent.ExecutionException e) {
                task1Error = e.getCause();
            }

            try {
                future2.get(10, TimeUnit.SECONDS);
                task2Success = true;
            } catch (java.util.concurrent.ExecutionException e) {
                task2Error = e.getCause();
            }

            // Exactly one must succeed, and the other must fail with exact CAT_011 (CATEGORY_CYCLE)
            assertThat(task1Success ^ task2Success)
                    .withFailMessage("Exactly one transaction must succeed and one must fail. T1: %s, T2: %s", task1Success, task2Success)
                    .isTrue();

            Throwable failedError = task1Success ? task2Error : task1Error;
            assertThat(failedError).isInstanceOf(AppException.class);
            assertThat(((AppException) failedError).getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_CYCLE);

            // Direct SQL query verification: no reciprocal cycle exists in database!
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                UUID aParentId = null;
                try (ResultSet rs = stmt.executeQuery("SELECT parent_id FROM public.categories WHERE category_id = '" + catA.getId() + "'")) {
                    assertThat(rs.next()).isTrue();
                    aParentId = rs.getObject("parent_id", UUID.class);
                }
                UUID bParentId = null;
                try (ResultSet rs = stmt.executeQuery("SELECT parent_id FROM public.categories WHERE category_id = '" + catB.getId() + "'")) {
                    assertThat(rs.next()).isTrue();
                    bParentId = rs.getObject("parent_id", UUID.class);
                }

                if (task1Success) {
                    assertThat(aParentId).isEqualTo(catB.getId());
                    assertThat(bParentId).isNull();
                } else {
                    assertThat(bParentId).isEqualTo(catA.getId());
                    assertThat(aParentId).isNull();
                }

                // Verify no cycle
                assertThat(aParentId != null && aParentId.equals(catB.getId()) && bParentId != null && bParentId.equals(catA.getId()))
                        .isFalse();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Concurrency: Race between child creation under parent and parent deletion is serialized without orphan rows")
    void testChildCreationVersusParentDeletionRace() throws Exception {
        // Setup Parent category P
        CategoryResponse parent = categoryService.createCategory(CreateCategoryRequest.builder()
                .code("PARENT_P")
                .name("Parent Category")
                .build());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Worker 1: in independent transaction, create child under parent P
            TransactionTemplate tx1 = new TransactionTemplate(transactionManager);
            tx1.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Future<CategoryResponse> futureChild = executor.submit(() -> tx1.execute(status -> {
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Worker 1 timed out waiting for start barrier");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return categoryService.createCategory(CreateCategoryRequest.builder()
                        .code("CHILD_C")
                        .name("Child Category")
                        .parentId(parent.getId())
                        .build());
            }));

            // Worker 2: in independent transaction, delete parent P
            TransactionTemplate tx2 = new TransactionTemplate(transactionManager);
            tx2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Future<Void> futureDelete = executor.submit(() -> tx2.execute(status -> {
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Worker 2 timed out waiting for start barrier");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                categoryService.deleteCategory(parent.getId());
                return null;
            }));

            // Ensure both worker transactions are open and waiting at start gate
            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .withFailMessage("Both workers must have active transactions and be ready before start")
                    .isTrue();

            // Release start gate to commence race
            start.countDown();

            boolean childSuccess = false;
            boolean deleteSuccess = false;
            Throwable childError = null;
            Throwable deleteError = null;

            try {
                futureChild.get(10, TimeUnit.SECONDS);
                childSuccess = true;
            } catch (java.util.concurrent.ExecutionException e) {
                childError = e.getCause();
            }

            try {
                futureDelete.get(10, TimeUnit.SECONDS);
                deleteSuccess = true;
            } catch (java.util.concurrent.ExecutionException e) {
                deleteError = e.getCause();
            }

            // Exactly one operation must succeed
            assertThat(childSuccess ^ deleteSuccess)
                    .withFailMessage("Exactly one operation must succeed. ChildSuccess: %s, DeleteSuccess: %s", childSuccess, deleteSuccess)
                    .isTrue();

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                if (childSuccess) {
                    // Child won -> Delete parent failed with CATEGORY_HAS_CHILDREN (CAT_013)
                    assertThat(deleteError).isInstanceOf(AppException.class);
                    assertThat(((AppException) deleteError).getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_HAS_CHILDREN);

                    // Direct database verification for child-wins branch:
                    // 1. Parent P exists
                    try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM public.categories WHERE category_id = '" + parent.getId() + "'")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                    }
                    // 2. Child C exists
                    try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM public.categories WHERE category_code = 'CHILD_C'")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                    }
                    // 3. Child C has parent_id = parent.id (no orphan, valid link)
                    try (ResultSet rs = stmt.executeQuery("SELECT parent_id FROM public.categories WHERE category_code = 'CHILD_C'")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getObject("parent_id", UUID.class)).isEqualTo(parent.getId());
                    }
                } else {
                    // Delete won -> Child creation failed with CATEGORY_PARENT_NOT_FOUND (CAT_009)
                    assertThat(childError).isInstanceOf(AppException.class);
                    assertThat(((AppException) childError).getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_PARENT_NOT_FOUND);

                    // Direct database verification for delete-wins branch:
                    // 1. Parent P does NOT exist
                    try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM public.categories WHERE category_id = '" + parent.getId() + "'")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(0);
                    }
                    // 2. Child C does NOT exist (no orphan row, no partial insert)
                    try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM public.categories WHERE category_code = 'CHILD_C'")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(0);
                    }
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
