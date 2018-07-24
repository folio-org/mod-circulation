package org.folio.circulation.support;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;

public interface HttpResult<T> {

  /**
   * Creates a successful result with the supplied value
   * unless an exception is thrown
   *
   * @param supplier of the result value
   * @return successful result or failed result with error
   */
  static <T> HttpResult<T> of(ThrowingSupplier<T, Exception> supplier) {
    try {
      return succeeded(supplier.get());
    } catch (Exception e) {
      return failed(new ServerErrorFailure(e));
    }
  }

  /**
   * Combines two results together, if both succeed.
   * Otherwise, returns either failure, first result takes precedence
   *
   * @param firstResult the  first result
   * @param secondResult the  second result
   * @param combiner function to combine the values together
   * @return either failure from the first result, failure from the second
   * or successful result with the values combined
   */
  static <T, U, V> HttpResult<V> combine(
    HttpResult<T> firstResult,
    HttpResult<U> secondResult,
    BiFunction<T, U, V> combiner) {

    if(firstResult.failed()) {
      return failed(firstResult.cause());
    }
    else if(secondResult.failed()) {
      return failed(secondResult.cause());
    }
    else {
      return succeeded(combiner.apply(firstResult.value(),
        secondResult.value()));
    }
  }

  /**
   * Combines a result together with the result of an action, if both succeed.
   * If the first result is a failure then it is returned, and the action is not invoked
   * otherwise if the result of the action is a failure it is returned
   *
   * @param nextAction the action to invoke if the current result succeeded
   * @param combiner function to combine the values together
   * @return either failure from the first result, failure from the action
   * or successful result with the values combined
   */
  default <U, V> CompletableFuture<HttpResult<V>> combineAfter(
    Function<T, CompletableFuture<HttpResult<U>>> nextAction, BiFunction<
    T, U, V> combiner) {

    return this.after(nextAction)
      .thenApply(actionResult -> actionResult.map(r ->
        combiner.apply(value(), r)));
  }

  /**
   * Allows branching between two paths based upon the outcome of a condition
   *
   * Executes the whenTrue function when condition evaluates to true
   * Executes the whenFalse function when condition evaluates to false
   * Executes neither if the condition evaluation fails
   * Forwards on failure if previous result failed
   *
   * @param condition on which to branch upon
   * @param whenTrue executed when condition evaluates to true
   * @param whenFalse executed when condition evaluates to false
   * @return Result of whenTrue or whenFalse, unless previous result failed
   */
  default CompletableFuture<HttpResult<T>> afterWhen(
    Function<T, CompletableFuture<HttpResult<Boolean>>> condition,
    Function<T, CompletableFuture<HttpResult<T>>> whenTrue,
    Function<T, CompletableFuture<HttpResult<T>>> whenFalse) {

    return after(value ->
      condition.apply(value)
        .thenCompose(r -> r.after(conditionResult -> {
          if (conditionResult) {
            return whenTrue.apply(value);
          } else {
            return whenFalse.apply(value);
          }
        })));
  }

  /**
   * Fail a result when a condition evaluates to true
   *
   * Responds with the result of the failure function when condition evaluates to true
   * Responds with success of the prior result when condition evaluates to false
   * Executes neither if the condition evaluation fails
   * Forwards on failure if previous result failed
   *
   * @param condition on which to decide upon
   * @param failure executed to create failure reason when condition evaluates to true
   * @return success when condition is false, failure otherwise
   */
  default CompletableFuture<HttpResult<T>> failAfter(
    Function<T, CompletableFuture<HttpResult<Boolean>>> condition,
    Function<T, HttpFailure> failure) {

    return afterWhen(condition,
      value -> completedFuture(failed(failure.apply(value))),
      value -> completedFuture(succeeded(value)));
  }

  /**
   * Allows branching between two paths based upon the outcome of a condition
   *
   * Executes the whenTrue function when condition evaluates to true
   * Executes the whenFalse function when condition evaluates to false
   * Executes neither if the condition evaluation fails
   * Forwards on failure if previous result failed
   *
   * @param condition on which to branch upon
   * @param whenTrue executed when condition evaluates to true
   * @param whenFalse executed when condition evaluates to false
   * @return Result of whenTrue or whenFalse, unless previous result failed
   */
  default HttpResult<T> nextWhen(
    Function<T, HttpResult<Boolean>> condition,
    Function<T, HttpResult<T>> whenTrue,
    Function<T, HttpResult<T>> whenFalse) {

    return next(value ->
      condition.apply(value).next(result -> result
          ? whenTrue.apply(value)
          : whenFalse.apply(value)));
  }

  /**
   * Fail a result when a condition evaluates to true
   *
   * Responds with the result of the failure function when condition evaluates to true
   * Responds with success of the prior result when condition evaluates to false
   * Executes neither if the condition evaluation fails
   * Forwards on failure if previous result failed
   *
   * @param condition on which to decide upon
   * @param failure executed to create failure reason when condition evaluates to true
   * @return success when condition is false, failure otherwise
   */
  default HttpResult<T> failWhen(
    Function<T, HttpResult<Boolean>> condition,
    Function<T, HttpFailure> failure) {

    return nextWhen(condition,
      value -> failed(failure.apply(value)),
      HttpResult::succeeded);
  }

  /**
   * Fail a result when a condition evaluates to true
   *
   * Responds with the result of the failure function when condition evaluates to true
   * Responds with success of the prior result when condition evaluates to false
   * Executes neither if the condition evaluation fails
   * Forwards on failure if previous result failed
   *
   * @param condition on which to decide upon
   * @param failure executed to create failure reason when condition evaluates to true
   * @return success when condition is false, failure otherwise
   */
  default HttpResult<T> failWhen(
    HttpResult<Boolean> condition,
    HttpFailure failure) {

    return nextWhen((v) -> condition,
      value -> failed(failure),
      HttpResult::succeeded);
  }

  boolean failed();

  T value();
  HttpFailure cause();

  default boolean succeeded() {
    return !failed();
  }

  static <T> HttpResult<T> succeeded(T value) {
    return new SuccessfulHttpResult<>(value);
  }

  static <T> WritableHttpResult<T> failed(HttpFailure cause) {
    return new FailedHttpResult<>(cause);
  }

  default <R> CompletableFuture<HttpResult<R>> after(
    Function<T, CompletableFuture<HttpResult<R>>> action) {

    if(failed()) {
      return completedFuture(failed(cause()));
    }

    return action.apply(value());
  }

  default <R> HttpResult<R> next(Function<T, HttpResult<R>> action) {
    if(failed()) {
      return failed(cause());
    }

    return action.apply(value());
  }

  default <U> HttpResult<U> map(Function<T, U> map) {
    if(failed()) {
      return failed(cause());
    }
    else {
      return HttpResult.succeeded(map.apply(value()));
    }
  }

  default T orElse(T other) {
    return succeeded()
      ? value()
      : other;
  }
}
