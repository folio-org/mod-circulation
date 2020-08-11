package org.folio.circulation.support.results;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.folio.circulation.support.Result;

public class AsynchronousResultBindings {
  private AsynchronousResultBindings() { }

  public static <T> CompletableFuture<Result<T>> safelyInitialise(
    Supplier<CompletableFuture<Result<T>>> supplier) {

    if (supplier == null) {
      return completedFuture(failedDueToServerError(new NullPointerException(
        "The asynchronous result supplier should not be null")));
    }

    try {
      return supplier.get();
    }
    catch(Exception e) {
      return completedFuture(failedDueToServerError(e));
    }
  }
}
