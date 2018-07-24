package org.folio.circulation.support;

@FunctionalInterface
public interface ThrowingSupplier<T, E extends Exception> {
  T get() throws E;
}
