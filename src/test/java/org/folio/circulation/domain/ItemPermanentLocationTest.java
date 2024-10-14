package org.folio.circulation.domain;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import api.support.builders.ItemBuilder;
import lombok.val;

class ItemPermanentLocationTest {
  @Test
  void itemLocationTakesPriorityOverHoldings() {
    val itemLocation = UUID.randomUUID();
    val holdingsLocation = UUID.randomUUID().toString();

    val itemJson = new ItemBuilder().withPermanentLocation(itemLocation).create();

    val item = Item.from(itemJson)
      .withHoldings(new Holdings(null, null, null, holdingsLocation, null));

    assertThat(item.getPermanentLocationId(), is(itemLocation.toString()));
  }

  @Test
  void holdingsLocationIsReturnedByDefault() {
    val holdingsLocation = UUID.randomUUID().toString();

    val itemJson = new ItemBuilder().withPermanentLocation((UUID) null).create();

    val item = Item.from(itemJson)
      .withHoldings(new Holdings(null, null, null, holdingsLocation, null));

    assertThat(item.getPermanentLocationId(), is(holdingsLocation.toString()));
  }

  @Test
  void nullIsReturnedWhenNoHoldingsPresent() {
    val itemJson = new ItemBuilder().withPermanentLocation((UUID) null).create();

    val item = Item.from(itemJson).withHoldings(Holdings.unknown(null));

    assertThat(item.getPermanentLocationId(), nullValue());
  }
}
