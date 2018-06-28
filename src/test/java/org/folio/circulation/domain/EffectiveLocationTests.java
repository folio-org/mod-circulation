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
      new ItemBuilder()
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .create(),
      new HoldingBuilder()
        .withPermanentLocation(popularReadingLocationId)
        .withNoTemporaryLocation()
        .create(),
      new InstanceBuilder("").create(),
      null, null);

    assertThat(item.getLocationId(), is(popularReadingLocationId));
  }

  @Test
  public void holdingTemporaryLocationTakesPrecedenceOverHoldingPermanentLocation() {
    final UUID popularReadingLocationId = UUID.randomUUID();
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .create(),
      new HoldingBuilder()
        .withPermanentLocation(popularReadingLocationId)
        .withTemporaryLocation(secondFloorEconomicsLocationId)
        .create(),
      new InstanceBuilder("").create(),
      null, null);

    assertThat(item.getLocationId(), is(secondFloorEconomicsLocationId));
  }

  @Test
  public void itemTemporaryLocationTakesPrecedenceOverItemPermanentLocation() {
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();
    final UUID thirdFloorDisplayCaseLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withPermanentLocation(secondFloorEconomicsLocationId)
        .withTemporaryLocation(thirdFloorDisplayCaseLocationId).create(),
      new HoldingBuilder()
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .create(),
      new InstanceBuilder("").create(),
      null, null);

    assertThat(item.getLocationId(), is(thirdFloorDisplayCaseLocationId));
  }

  @Test
  public void itemTemporaryLocationTakesPrecedenceOverAllOtherLocations() {
    final UUID popularReadingLocationId = UUID.randomUUID();
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();
    final UUID firstFloorComputerScienceLocationId = UUID.randomUUID();
    final UUID thirdFloorDisplayCaseLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withPermanentLocation(secondFloorEconomicsLocationId)
        .withTemporaryLocation(thirdFloorDisplayCaseLocationId).create(),
      new HoldingBuilder()
        .withPermanentLocation(firstFloorComputerScienceLocationId)
        .withTemporaryLocation(popularReadingLocationId)
        .create(),
      new InstanceBuilder("").create(),
      null, null);

    assertThat(item.getLocationId(), is(thirdFloorDisplayCaseLocationId));
  }

  @Test
  public void itemTemporaryLocationTakesPrecedenceOverHoldingTemporaryLocation() {
    final UUID popularReadingLocationId = UUID.randomUUID();
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();
    final UUID firstFloorComputerScienceLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withNoPermanentLocation()
        .withTemporaryLocation(secondFloorEconomicsLocationId).create(),
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
      new ItemBuilder()
        .withNoPermanentLocation()
        .withTemporaryLocation(popularReadingLocationId).create(),
      new HoldingBuilder()
        .withPermanentLocation(firstFloorComputerScienceLocationId)
        .withNoTemporaryLocation()
        .create(),
      new InstanceBuilder("").create(),
      null, null);

    assertThat(item.getLocationId(), is(popularReadingLocationId));
  }
}
