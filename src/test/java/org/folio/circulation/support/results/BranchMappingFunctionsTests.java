package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isFailureContaining;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.folio.circulation.support.results.MappingFunctions.when;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.folio.circulation.support.failures.HttpFailure;
import org.junit.jupiter.api.Test;

import lombok.SneakyThrows;

class BranchMappingFunctionsTests {
  private final Function<Integer, CompletableFuture<Result<Integer>>> addTenWhenEven = when(
    (Integer x) -> ofAsync(() -> isEven(x)), x -> ofAsync(() -> x + 10), x -> ofAsync(() -> x));

  @Test
  void shouldApplyTrueBranchWhenConditionTrue() {
    assertThat(getValue(addTenWhenEven.apply(10)), is(20));
  }

  @Test
  void shouldApplyFalseBranchWhenConditionFalse() {
    assertThat(getValue(addTenWhenEven.apply(15)), is(15));
  }

  @Test
  void shouldFailWhenConditionEvaluationFails() {
    final var conditionFails = when(
      x -> supplyAsync(ResultExamples::conditionFailed), x -> ofAsync(() -> x), x -> ofAsync(() -> x));

    assertThat(getCause(conditionFails.apply(15)), isFailureContaining("Condition failed"));
  }

  @SneakyThrows
  private <T> T getValue(CompletableFuture<Result<T>> futureResult) {
    return futureResult.get(5, TimeUnit.SECONDS).value();
  }

  @SneakyThrows
  private <T> HttpFailure getCause(CompletableFuture<Result<T>> futureResult) {
    return futureResult.get(5, TimeUnit.SECONDS).cause();
  }

  private boolean isEven(Integer x) {
    return x % 2 == 0;
  }
}
