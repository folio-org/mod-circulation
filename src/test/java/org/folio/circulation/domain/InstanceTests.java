package org.folio.circulation.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InstanceTests {
  @Test
  void cannotHaveANullCollectionOfIdentifiers() {
    assertThrows(NullPointerException.class, () -> new Instance("Title", null));
  }
}
