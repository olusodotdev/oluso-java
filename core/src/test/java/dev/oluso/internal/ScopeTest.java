package dev.oluso.internal;

import dev.oluso.Breadcrumb;
import dev.oluso.UserContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScopeTest {

    @Test
    void isolatesBreadcrumbsBetweenConcurrentScopesOnDifferentThreads() throws Exception {
        Scope scope = new Scope(30);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch aStarted = new CountDownLatch(1);

        try {
            Future<List<String>> taskA = executor.submit(() -> scope.run(() -> {
                scope.addBreadcrumb(Breadcrumb.builder("a1").build());
                aStarted.countDown();
                Thread.sleep(50);
                scope.addBreadcrumb(Breadcrumb.builder("a2").build());
                return scope.getBreadcrumbs().stream().map(Breadcrumb::getMessage).collect(Collectors.toList());
            }));

            aStarted.await();
            Future<List<String>> taskB = executor.submit(() -> scope.run(() -> {
                scope.addBreadcrumb(Breadcrumb.builder("b1").build());
                return scope.getBreadcrumbs().stream().map(Breadcrumb::getMessage).collect(Collectors.toList());
            }));

            assertEquals(List.of("a1", "a2"), taskA.get());
            assertEquals(List.of("b1"), taskB.get());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void fallsBackToASharedStoreOutsideRun() {
        Scope scope = new Scope(30);
        scope.addBreadcrumb(Breadcrumb.builder("no scope").build());
        List<Breadcrumb> breadcrumbs = scope.getBreadcrumbs();
        assertEquals(1, breadcrumbs.size());
        assertEquals("no scope", breadcrumbs.get(0).getMessage());
    }

    @Test
    void capsBreadcrumbsAtMaxBreadcrumbs() throws Exception {
        Scope scope = new Scope(2);
        scope.run(() -> {
            scope.addBreadcrumb(Breadcrumb.builder("1").build());
            scope.addBreadcrumb(Breadcrumb.builder("2").build());
            scope.addBreadcrumb(Breadcrumb.builder("3").build());
            List<String> messages =
                    scope.getBreadcrumbs().stream().map(Breadcrumb::getMessage).collect(Collectors.toList());
            assertEquals(List.of("2", "3"), messages);
            return null;
        });
    }

    @Test
    void tracksUserAndCustomContextPerRun() throws Exception {
        Scope scope = new Scope(30);
        scope.run(() -> {
            scope.setUserContext(UserContext.of("user_1"));
            scope.setCustomContext("cartId", "cart_1");
            assertEquals("user_1", scope.getUserContext().getId());
            assertEquals("cart_1", scope.getCustomContext().get("cartId"));
            return null;
        });
    }

    @Test
    void restoresThePreviousScopeAfterNestedRunReturns() throws Exception {
        Scope scope = new Scope(30);
        scope.run(() -> {
            scope.addBreadcrumb(Breadcrumb.builder("outer").build());
            scope.run(() -> {
                scope.addBreadcrumb(Breadcrumb.builder("inner").build());
                return null;
            });
            List<String> messages =
                    scope.getBreadcrumbs().stream().map(Breadcrumb::getMessage).collect(Collectors.toList());
            assertEquals(List.of("outer"), messages);
            return null;
        });
    }
}
