package api.support;

import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;

import org.awaitility.core.ConditionFactory;

class Wait {
  private Wait() { }

  public static ConditionFactory waitAtLeast(int delay, TimeUnit timeUnit) {
    return await()
      .pollDelay(delay, timeUnit)
      .atLeast(delay, timeUnit);
  }
}
