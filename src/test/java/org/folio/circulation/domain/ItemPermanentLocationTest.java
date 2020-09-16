package org.folio.circulation.domain;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.junit.Test;

import api.support.builders.HoldingBuilder;
import api.support.builders.ItemBuilder;
import lombok.val;

public class ItemPermanentLocationTest {

  @Test
  public void itemLocationTakesPriorityOverHoldings() {
    val itemLocation = UUID.randomUUID();
    val holdingsLocation = UUID.randomUUID();

    val itemJson = new ItemBuilder().withPermanentLocation(itemLocation).create();
    val holdingsJson = new HoldingBuilder().withPermanentLocation(holdingsLocation).create();

    val item = Item.from(itemJson).withHoldingsRecord(holdingsJson);

    assertThat(item.getPermanentLocationId(), is(itemLocation.toString()));
  }

  @Test
  public void holdingsLocationIsReturnedByDefault() {
    val holdingsLocation = UUID.randomUUID();

    val itemJson = new ItemBuilder().withPermanentLocation((UUID) null).create();
    val holdingsJson = new HoldingBuilder().withPermanentLocation(holdingsLocation).create();

    val item = Item.from(itemJson).withHoldingsRecord(holdingsJson);

    assertThat(item.getPermanentLocationId(), is(holdingsLocation.toString()));
  }

  @Test
  public void nullIsReturnedWhenNoHoldingsJsonPresent() {
    val itemJson = new ItemBuilder().withPermanentLocation((UUID) null).create();

    val item = Item.from(itemJson).withHoldingsRecord(null);

    assertThat(item.getPermanentLocationId(), nullValue());
  }
}
