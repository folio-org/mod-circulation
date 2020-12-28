package org.folio.circulation.support.results;

import java.util.function.Function;
import java.util.function.Supplier;

public class MappingFunctions {
  private MappingFunctions() { }

  public static <T, R> Function<T, R> toFixedValue(Supplier<R> fixedValueSupplier) {
    return x -> fixedValueSupplier.get();
  }
}
