package org.folio.circulation.support.results;

import java.util.function.Function;

public class ResultBinding {
  private ResultBinding() { }

  public static <T, R> Function<Result<T>, Result<R>> mapResult(
    Function<T, R> mapper) {

    return result -> result.map(mapper);
  }

  public static <T, R> Function<Result<T>, Result<R>> flatMapResult(
    Function<T, Result<R>> mapper) {

    return result -> result.next(mapper);
  }
}
