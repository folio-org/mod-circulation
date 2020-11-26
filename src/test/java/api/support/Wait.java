package api.support;

import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;

import org.awaitility.core.ConditionFactory;

public class Wait {
  private Wait() { }

  public static ConditionFactory atLeast(int delay, TimeUnit timeUnit) {
    return await()
      .pollDelay(delay, timeUnit)
      .atLeast(delay, timeUnit);
  }
}
