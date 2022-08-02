package org.folio.circulation.support.results;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class MappingFunctions {
  private MappingFunctions() { }

  public static <T, R> Function<T, R> toFixedValue(Supplier<R> fixedValueSupplier) {
    return x -> fixedValueSupplier.get();
  }

  /**
   * Allows branching between two paths based upon the outcome of a condition
   *
   * Executes the whenTrue function when condition evaluates to true
   * Executes the whenFalse function when condition evaluates to false
   * Executes neither if the condition evaluation fails
   *
   * @param conditionFunction on which to branch upon
   * @param whenTrue executed when condition evaluates to true
   * @param whenFalse executed when condition evaluates to false
   * @return Result of whenTrue or whenFalse, unless previous result failed
   */
  public static <T, R> Function<T, CompletableFuture<Result<R>>> when(
    Function<T, CompletableFuture<Result<Boolean>>> conditionFunction,
    Function<T, CompletableFuture<Result<R>>> whenTrue,
    Function<T, CompletableFuture<Result<R>>> whenFalse) {

    return value ->
      conditionFunction.apply(value)
        .thenComposeAsync(r -> r.after(condition -> isTrue(condition)
          ? whenTrue.apply(value)
          : whenFalse.apply(value)));
  }
}
