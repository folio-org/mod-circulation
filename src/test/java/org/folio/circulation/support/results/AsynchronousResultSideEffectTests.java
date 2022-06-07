package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isFailureContaining;
import static org.folio.circulation.support.results.AsynchronousResult.successful;
import static org.folio.circulation.support.results.AsynchronousResultExamples.alreadyFailed;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.folio.circulation.support.failures.HttpFailure;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AsynchronousResultSideEffectTests {
  private final Invocable<Integer> onSuccess = new Invocable<>();
  private final Invocable<HttpFailure> onFailure = new Invocable<>();

  @Nested
  class whenSuccessful {
    private final AsynchronousResult<Integer> result = successful(10);

    @Test
    void onSuccessConsumerShouldBeExecuted() {
      result.onSuccess(onSuccess.consumer());

      assertThat(onSuccess.hasBeenInvoked(), is(true));
      assertThat(onSuccess.invokedValue(), is(10));
    }

    @Test
    void onFailureConsumerShouldNotBeExecuted() {
      result.onFailure(onFailure.consumer());

      assertThat(onSuccess.hasBeenInvoked(), is(false));
    }

    @Test
    void onCompletionOnlySuccessConsumerShouldBeExecuted() {
      result.onComplete(onSuccess.consumer(), onFailure.consumer());

      assertThat(onSuccess.hasBeenInvoked(), is(true));
      assertThat(onSuccess.invokedValue(), is(10));

      assertThat(onFailure.hasBeenInvoked(), is(false));
    }
  }

  @Nested
  class whenFailed {
    private final AsynchronousResult<Integer> result = alreadyFailed();

    @Test
    void onSuccessConsumerShouldNotBeExecuted() {
      result.onSuccess(onSuccess.consumer());

      assertThat(onSuccess.hasBeenInvoked(), is(false));
    }

    @Test
    void onFailureConsumerShouldBeExecuted() {
      result.onFailure(onFailure.consumer());

      assertThat(onFailure.hasBeenInvoked(), is(true));
      assertThat(onFailure.invokedValue(), isFailureContaining("Already failed"));
    }

    @Test
    void onCompletionOnlySuccessConsumerShouldBeExecuted() {
      result.onComplete(onSuccess.consumer(), onFailure.consumer());

      assertThat(onSuccess.hasBeenInvoked(), is(false));

      assertThat(onFailure.hasBeenInvoked(), is(true));
      assertThat(onFailure.invokedValue(), isFailureContaining("Already failed"));
    }
  }

  private static class Invocable<T> {
    private final AtomicBoolean invoked = new AtomicBoolean();
    private final AtomicReference<T> invokedWithValue = new AtomicReference<>();

    public Consumer<T> consumer() {
      return (T x) -> {
        invoked.set(true);
        invokedWithValue.set(x);
      };
    }

    public boolean hasBeenInvoked() {
      return invoked.get();
    }

    public T invokedValue() {
      return invokedWithValue.get();
    }
  }
}
