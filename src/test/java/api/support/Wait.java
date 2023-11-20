package api.support;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.awaitility.core.ConditionFactory;

public class Wait {
  private Wait() { }

  public static ConditionFactory waitAtLeast(int delay, TimeUnit timeUnit) {
    return await()
      .pollDelay(delay, timeUnit)
      .atLeast(delay, timeUnit);
  }

  public static <T> Collection<T> waitForSize(Callable<Collection<T>> supplier, int expectedSize) {
    return waitForValue(supplier, (Predicate<Collection<T>>) c -> c.size() == expectedSize);
  }

  public static <T> T waitForValue(Callable<T> valueSupplier, T expectedValue) {
    return waitForValue(valueSupplier, (Predicate<T>) actualValue -> actualValue == expectedValue);
  }

  public static <T> T waitForValue(Callable<T> valueSupplier, Predicate<T> valuePredicate) {
    return waitAtMost(30, SECONDS)
      .until(valueSupplier, valuePredicate);
  }
}
