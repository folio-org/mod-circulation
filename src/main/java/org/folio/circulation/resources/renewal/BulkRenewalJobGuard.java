package org.folio.circulation.resources.renewal;

import java.util.concurrent.atomic.AtomicBoolean;

public class BulkRenewalJobGuard {
  private final AtomicBoolean running = new AtomicBoolean(false);

  public boolean tryStart() {
    return running.compareAndSet(false, true);
  }

  public void finish() {
    running.set(false);
  }

  public boolean isRunning() {
    return running.get();
  }
}
