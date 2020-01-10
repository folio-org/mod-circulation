package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isFailureContaining;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.folio.circulation.support.HttpFailure;
import org.junit.Test;

public class ResultApplySideEffectTests {
  @Test
  public void shouldApplySuccessConsumerWhenSuccessful() {
    final AtomicInteger appliedSuccess = new AtomicInteger();
    final AtomicBoolean appliedFailure = new AtomicBoolean();

    succeeded(10)
      .applySideEffect(appliedSuccess::set,
        cause -> appliedFailure.set(true));

    Awaitility.await()
      .atMost(1, SECONDS)
      .until(appliedSuccess::get, is(10));

    assertThat("Should not have applied failure consumer",
      appliedFailure.get(), is(false));
  }

  @Test
  public void shouldApplyFailureConsumerWhenFailed() {
    final AtomicBoolean appliedSuccess = new AtomicBoolean();
    final AtomicReference<HttpFailure> appliedFailure = new AtomicReference<>();

    alreadyFailed()
      .applySideEffect(value -> appliedSuccess.set(true),
        appliedFailure::set);

    Awaitility.await()
      .atMost(1, SECONDS)
      .until(appliedFailure::get, isFailureContaining("Already failed"));

    assertThat("Should not have applied success consumer",
      appliedSuccess.get(), is(false));
  }
}
