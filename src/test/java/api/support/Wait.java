package api.support;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.awaitility.core.ConditionFactory;

import io.vertx.core.Future;
import lombok.SneakyThrows;

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

  public static <T> T waitForValue(Callable<T> valueSupplier, T expected) {
    return waitForValue(valueSupplier, (Predicate<T>) actual -> Objects.equals(actual, expected));
  }

  public static <T> T waitForValue(Callable<T> valueSupplier, Predicate<T> valuePredicate) {
    return waitAtMost(60, SECONDS)
      .until(valueSupplier, valuePredicate);
  }

  public static <T> T waitFor(Future<T> future) {
    return waitFor(future, 10);
  }

  @SneakyThrows
  public static <T> T waitFor(Future<T> future, int timeoutSeconds) {
    return future.toCompletionStage()
      .toCompletableFuture()
      .get(timeoutSeconds, SECONDS);
  }
}
