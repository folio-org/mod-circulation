package org.folio.circulation.support.results;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.circulation.support.results.AsynchronousResult.fromFutureResult;
import static org.folio.circulation.support.results.AsynchronousResultTestHelper.getValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

class AsynchronousResultCreationTests {
  @Test
  void shouldCreateNewResultFromFutureResult() {
    final var futureResult = Result.ofAsync(() -> 5);
    final var asynchronousResult = fromFutureResult(futureResult);

    assertThat(getValue(asynchronousResult, 1, SECONDS), is(5));
    assertThat(asynchronousResult.toCompletionStage(), not(sameInstance(futureResult)));
  }
}
