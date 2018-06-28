package org.folio.circulation.domain;

import api.support.builders.HoldingBuilder;
import api.support.builders.InstanceBuilder;
import api.support.builders.ItemBuilder;
import org.junit.Test;

import java.util.UUID;

import static api.support.matchers.UUIDMatcher.is;
import static org.junit.Assert.assertThat;

public class EffectiveLocationTests {
  @Test
  public void holdingPermanentLocationIsDefaultWhenNoOtherLocations() {
    final UUID popularReadingLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder().create(),
      new HoldingBuilder().withPermanentLocation(popularReadingLocationId).create(),
      new InstanceBuilder("").create(),
      null, null);

    assertThat(item.getLocationId(), is(popularReadingLocationId));
  }

  @Test
  public void holdingTemporaryLocationTakesPrecedenceOverHoldingPermanentLocation() {
    final UUID popularReadingLocationId = UUID.randomUUID();
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder().create(),
      new HoldingBuilder()
        .withPermanentLocation(popularReadingLocationId)
        .withTemporaryLocation(secondFloorEconomicsLocationId)
        .create(),
      new InstanceBuilder("").create(),
      null, null);

    assertThat(item.getLocationId(), is(secondFloorEconomicsLocationId));
  }

  @Test
  public void itemTemporaryLocationTakesPrecedenceOverHoldingTemporaryLocation() {
    final UUID popularReadingLocationId = UUID.randomUUID();
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();
    final UUID firstFloorComputerScienceLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder().withTemporaryLocation(secondFloorEconomicsLocationId).create(),
      new HoldingBuilder()
        .withPermanentLocation(firstFloorComputerScienceLocationId)
        .withTemporaryLocation(popularReadingLocationId)
        .create(),
      new InstanceBuilder("").create(),
      null, null);

    assertThat(item.getLocationId(), is(secondFloorEconomicsLocationId));
  }
  
  @Test
  public void itemTemporaryLocationTakesPrecedenceOverHoldingPermanent() {
    final UUID popularReadingLocationId = UUID.randomUUID();
    final UUID firstFloorComputerScienceLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder().withTemporaryLocation(popularReadingLocationId).create(),
      new HoldingBuilder()
        .withPermanentLocation(firstFloorComputerScienceLocationId)
        .create(),
      new InstanceBuilder("").create(),
      null, null);

    assertThat(item.getLocationId(), is(popularReadingLocationId));
  }
}
