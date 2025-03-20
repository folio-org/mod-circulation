package org.folio.circulation.support.utils;

import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CommonUtils {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private CommonUtils() {}

  public static <T> T getOrDefault(Supplier<T> value, Supplier<T> def) {
    return value.get() != null ? value.get() : def.get();
  }

  public static <T> void executeIfNotNull(T target, Consumer<T> action) {
    if (target != null) {
      action.accept(target);
    }
  }

  public static <L, R> Pair<L, R> pair(L l, R r) {
    return new ImmutablePair<>(l, r);
  }

  public static Integer tryParseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      log.warn("tryParseInt:: invalid string value '{}'", value, e);
      return null;
    }
  }
}
