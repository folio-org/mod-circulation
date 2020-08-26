package org.folio.circulation.support.utils;

import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public final class CommonUtils {

  private CommonUtils() {}

  public static <T> T getOrDefault(Supplier<T> value, Supplier<T> def) {
    return value.get() != null ? value.get() : def.get();
  }

  public static <L, R> Pair<L, R> pair(L l, R r) {
    return new ImmutablePair<>(l, r);
  }
}
