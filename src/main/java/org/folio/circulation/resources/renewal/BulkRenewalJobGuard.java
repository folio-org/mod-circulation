package org.folio.circulation.resources.renewal;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BulkRenewalJobGuard {
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final Runnable onFinish;

  public BulkRenewalJobGuard() {
    this(() -> {});
  }

  public BulkRenewalJobGuard(Runnable onFinish) {
    this.onFinish = onFinish;
  }

  public boolean tryStart() {
    return running.compareAndSet(false, true);
  }

  public void finish() {
    running.set(false);
    onFinish.run();
  }

  public boolean isRunning() {
    return running.get();
  }
}
