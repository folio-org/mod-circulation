package org.folio.circulation.resources.renewal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

class BulkRenewalJobGuardTest {
  @Test
  void shouldNotBeRunningByDefault() {
    BulkRenewalJobGuard guard = new BulkRenewalJobGuard();

    assertFalse(guard.isRunning());
  }

  @Test
  void shouldAllowFirstJobStart() {
    BulkRenewalJobGuard guard = new BulkRenewalJobGuard();

    assertTrue(guard.tryStart());
    assertTrue(guard.isRunning());
  }

  @Test
  void shouldRejectSecondJobWhileRunning() {
    BulkRenewalJobGuard guard = new BulkRenewalJobGuard();

    assertTrue(guard.tryStart());
    assertFalse(guard.tryStart());
  }

  @Test
  void shouldAllowAnotherJobAfterFinish() {
    BulkRenewalJobGuard guard = new BulkRenewalJobGuard();

    assertTrue(guard.tryStart());
    guard.finish();

    assertFalse(guard.isRunning());
    assertTrue(guard.tryStart());
  }

  @Test
  void shouldAllowOnlyOneConcurrentStartAttempt() throws InterruptedException {
    BulkRenewalJobGuard guard = new BulkRenewalJobGuard();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(2);
    List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

    Runnable tryStart = () -> {
      ready.countDown();

      try {
        start.await();
        results.add(guard.tryStart());
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
      finally {
        done.countDown();
      }
    };

    Thread first = new Thread(tryStart);
    Thread second = new Thread(tryStart);

    first.start();
    second.start();
    ready.await();
    start.countDown();
    done.await();

    assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
    assertEquals(1, results.stream().filter(started -> !started).count());
    assertTrue(guard.isRunning());
  }
}
