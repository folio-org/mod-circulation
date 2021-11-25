package org.folio.circulation.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import api.support.builders.ItemBuilder;
import lombok.val;

class ItemTests {
  @Test
  void cannotHaveANullHoldings() {
    val item = Item.from(new ItemBuilder().create());

    assertThrows(NullPointerException.class, () -> item.withHoldings(null));
  }

  @Test
  void cannotHaveANullInstance() {
    val item = Item.from(new ItemBuilder().create());

    assertThrows(NullPointerException.class, () -> item.withInstance(null));
  }

  @Test
  void cannotHaveANullMaterialType() {
    val item = Item.from(new ItemBuilder().create());

    assertThrows(NullPointerException.class, () -> item.withMaterialType(null));
  }

  @Test
  void cannotHaveANullLoanType() {
    val item = Item.from(new ItemBuilder().create());

    assertThrows(NullPointerException.class, () -> item.withLoanType(null));
  }
}
