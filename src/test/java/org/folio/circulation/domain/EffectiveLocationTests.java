package org.folio.circulation.domain;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.UUID;

import org.junit.Test;

import api.support.APITests;
import api.support.builders.HoldingBuilder;
import api.support.builders.InstanceBuilder;
import api.support.builders.ItemBuilder;

public class EffectiveLocationTests extends APITests {

  @Test
  public void noLocations() {
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
  public void noItem() {
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
