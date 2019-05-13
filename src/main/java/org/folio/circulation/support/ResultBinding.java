package org.folio.circulation.support;

import java.util.function.Function;

public class ResultBinding {
  private ResultBinding() { }

  public static <T, R> Function<Result<T>, Result<R>> mapResult(Function<T, R> mapper) {
    return result -> result.map(mapper);
  }
}
