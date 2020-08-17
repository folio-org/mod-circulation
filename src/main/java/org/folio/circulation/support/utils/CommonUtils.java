package org.folio.circulation.support.utils;

import java.util.function.Supplier;

public final class CommonUtils {

  private CommonUtils() {}

  public static <T> T getOrDefault(Supplier<T> value, Supplier<T> def) {
    return value.get() != null ? value.get() : def.get();
  }
}
