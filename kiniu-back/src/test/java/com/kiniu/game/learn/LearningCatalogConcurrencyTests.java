package com.kiniu.game.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the hot-reload contract under concurrency: readers must never observe a half-written
 * catalog, the revision must be monotonic, and no committed reload may be lost.
 */
class LearningCatalogConcurrencyTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void shouldServeOnlyCompleteCatalogsWhileTheFileIsRewrittenConcurrently() throws Exception {
        Path path = tempDir.resolve("concurrent-catalog.json");
        writeCatalog(path, "v0");
        LearningCatalogService service = new LearningCatalogService(
                objectMapper, path.toString(), TaskCheckRegistryTestFactory.standard(objectMapper));

        int versions = 40;
        Set<String> validTitles = ConcurrentHashMap.newKeySet();
        validTitles.add("v0");
        for (int index = 1; index <= versions; index++) {
            validTitles.add("v" + index);
        }

        AtomicReference<Throwable> readerFailure = new AtomicReference<>();
        AtomicBoolean stop = new AtomicBoolean(false);
        int readerCount = 4;
        ExecutorService readers = Executors.newFixedThreadPool(readerCount);
        CountDownLatch started = new CountDownLatch(readerCount);
        for (int reader = 0; reader < readerCount; reader++) {
            readers.submit(() -> {
                started.countDown();
                try {
                    while (!stop.get()) {
                        String title = service.getCatalog().modules().get(0).tasks().get(0).title();
                        if (!validTitles.contains(title)) {
                            readerFailure.compareAndSet(null, new AssertionError("Served a torn catalog: " + title));
                            return;
                        }
                    }
                } catch (Throwable failure) {
                    readerFailure.compareAndSet(null, failure);
                }
            });
        }
        assertTrue(started.await(5, TimeUnit.SECONDS));

        for (int index = 1; index <= versions; index++) {
            writeCatalog(path, "v" + index);
            Files.setLastModifiedTime(path, FileTime.fromMillis(
                    Files.getLastModifiedTime(path).toMillis() + 1_000));
            Thread.sleep(1);
        }
        stop.set(true);
        readers.shutdown();
        assertTrue(readers.awaitTermination(10, TimeUnit.SECONDS));
        if (readerFailure.get() != null) {
            throw new AssertionError(readerFailure.get());
        }

        // After the writer stops, the service must converge on the final on-disk version.
        writeCatalog(path, "final");
        Files.setLastModifiedTime(path, FileTime.fromMillis(
                Files.getLastModifiedTime(path).toMillis() + 5_000));
        assertEquals("final", service.getCatalog().modules().get(0).tasks().get(0).title());
    }

    @Test
    void shouldKeepRevisionMonotonicAndCountEveryReload() throws Exception {
        Path path = tempDir.resolve("revision-catalog.json");
        writeCatalog(path, "v0");
        LearningCatalogService service = new LearningCatalogService(
                objectMapper, path.toString(), TaskCheckRegistryTestFactory.standard(objectMapper));

        int threads = 8;
        int reloadsPerThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int thread = 0; thread < threads; thread++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int reload = 0; reload < reloadsPerThread; reload++) {
                    service.reload();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // The constructor starts at revision 1; every committed reload must bump it exactly once.
        assertEquals(1L + (long) threads * reloadsPerThread, service.revision());
    }

    @Test
    void shouldKeepServingTheLastValidCatalogWhenTheFileDisappearsAndReturnWhenItComesBack() throws Exception {
        Path path = tempDir.resolve("vanish-catalog.json");
        writeCatalog(path, "before");
        LearningCatalogService service = new LearningCatalogService(
                objectMapper, path.toString(), TaskCheckRegistryTestFactory.standard(objectMapper));
        assertEquals("before", service.getCatalog().modules().get(0).tasks().get(0).title());

        Files.delete(path);
        assertEquals("before", service.getCatalog().modules().get(0).tasks().get(0).title());

        writeCatalog(path, "after");
        Files.setLastModifiedTime(path, FileTime.fromMillis(
                Files.getLastModifiedTime(path).toMillis() + 5_000));
        assertEquals("after", service.getCatalog().modules().get(0).tasks().get(0).title());
    }

    private void writeCatalog(Path path, String title) throws Exception {
        LearningTaskDefinition task = new LearningTaskDefinition(
                "first",
                title,
                "Summary",
                "beginner",
                "requirements",
                10,
                List.of("requirements"),
                "Objective",
                "Scenario",
                "project-agent",
                List.of(new LearningFileView("artifact.md", "starter text")),
                List.of(new TaskCheckDefinition("content", "min-length", "artifact.md", "3", true, 100, "Content")));
        LearningCatalogDefinition catalog = new LearningCatalogDefinition(
                1,
                List.of(new LearningModuleDefinition("module", "Module", "Summary", "beginner", List.of(task))));
        objectMapper.writeValue(path.toFile(), catalog);
    }
}