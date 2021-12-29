package org.folio.circulation.domain;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InstanceTests {
  @Test
  void cannotHaveANullCollectionOfIdentifiers() {
    assertThrows(NullPointerException.class, () -> new Instance("Title", null, emptyList(), emptyList(), emptyList()));
  }

  @Test
  void cannotHaveANullCollectionOfContributors() {
    assertThrows(NullPointerException.class, () -> new Instance("Title", emptyList(), null, emptyList(), emptyList()));
  }

  @Test
  void cannotHaveANullCollectionOfPublication() {
    assertThrows(NullPointerException.class, () -> new Instance("Title", emptyList(), emptyList(), null, emptyList()));
  }

  @Test
  void cannotHaveANullCollectionOfEditions() {
    assertThrows(NullPointerException.class, () -> new Instance("Title", emptyList(), emptyList(), emptyList(), null));
  }
}
