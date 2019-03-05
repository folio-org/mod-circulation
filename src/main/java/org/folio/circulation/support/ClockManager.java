package org.folio.circulation.support;

import java.time.Clock;

// This class allows for unit tests to replace the clock used by the module.
// Ideally, we'd use dependency injection for this.
public class ClockManager {
  private static final ClockManager INSTANCE = new ClockManager();

  private Clock clock = Clock.systemUTC();

  private ClockManager() {
    super();
  }

  public void setClock(Clock clock) {
    if (clock == null) {
      throw new IllegalArgumentException("clock cannot be null");
    }

    this.clock = clock;
  }

  public Clock getClock() {
    return clock;
  }

  public static ClockManager getClockManager() {
    return INSTANCE;
  }
}
