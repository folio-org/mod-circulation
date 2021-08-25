package org.folio.circulation.domain;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.HoldingBuilder;
import api.support.builders.InstanceBuilder;
import api.support.builders.ItemBuilder;

class EffectiveLocationTests extends APITests {

  @Test
  void noLocations() {
    final Item item = Item.from(new ItemBuilder()
      .withNoPermanentLocation()
      .withNoTemporaryLocation()
      .create()
    ).withHoldingsRecord(
      new HoldingBuilder()
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .create()
    ).withInstance(instanceBuilder().create());

    assertThat(item.getLocationId(), nullValue());
  }

  @Test
  void noItem() {
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();

    final Item item = Item.from(null)
      .withHoldingsRecord(new HoldingBuilder()
        .withPermanentLocation(secondFloorEconomicsLocationId)
        .withNoTemporaryLocation()
        .create()
      ).withInstance(instanceBuilder().create());

    assertThat(item.getLocationId(), nullValue());
  }

  private InstanceBuilder instanceBuilder() {
    return new InstanceBuilder("", null);
  }
}
