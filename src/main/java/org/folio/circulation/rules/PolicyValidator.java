package org.folio.circulation.rules;

@FunctionalInterface
public interface PolicyValidator<K, V, S> {
  void validatePolicy(K var1, V var2, S var3);
}
