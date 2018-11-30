package org.folio.circulation.domain;

import static api.support.matchers.UUIDMatcher.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.UUID;

import org.junit.Test;

import api.support.builders.HoldingBuilder;
import api.support.builders.InstanceBuilder;
import api.support.builders.ItemBuilder;

//Scenario comments based upon numbering from https://wiki.folio.org/display/RA/Effective+Location+Logic
public class EffectiveLocationTests {
  //Scenario 1
  @Test
  public void onlyPermanentHoldingLocation() {
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
      null, null, null);

    assertThat(item.getLocationId(), is(popularReadingLocationId));
  }

  //Scenario 2
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
      null, null, null);

    assertThat(item.getLocationId(), is(secondFloorEconomicsLocationId));
  }

  //Scenario 3
  @Test
  public void itemPermanentLocationTakesPrecedenceOverBothHoldingLocations() {
    final UUID popularReadingLocationId = UUID.randomUUID();
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();
    final UUID firstFloorComputerScienceLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withPermanentLocation(secondFloorEconomicsLocationId)
        .withNoTemporaryLocation()
        .create(),
      new HoldingBuilder()
        .withPermanentLocation(firstFloorComputerScienceLocationId)
        .withTemporaryLocation(popularReadingLocationId)
        .create(),
      new InstanceBuilder("").create(),
      null, null, null);

    assertThat(item.getLocationId(), is(secondFloorEconomicsLocationId));
  }

  //Scenario 4
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
      null, null, null);

    assertThat(item.getLocationId(), is(thirdFloorDisplayCaseLocationId));
  }

  //Scenario 5
  @Test
  public void itemPermanentLocationTakesPrecedenceOverHoldingPermanent() {
    final UUID popularReadingLocationId = UUID.randomUUID();
    final UUID firstFloorComputerScienceLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withPermanentLocation(firstFloorComputerScienceLocationId)
        .withNoTemporaryLocation()
        .create(),
      new HoldingBuilder()
        .withPermanentLocation(popularReadingLocationId)
        .withNoTemporaryLocation()
        .create(),
      new InstanceBuilder("").create(),
      null, null, null);

    assertThat(item.getLocationId(), is(firstFloorComputerScienceLocationId));
  }

  //Scenario 6
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
      null, null, null);

    assertThat(item.getLocationId(), is(popularReadingLocationId));
  }

  //Scenario 7
  @Test
  public void itemTemporaryLocationTakesPrecedenceOverBothHoldingLocations() {
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
      null, null, null);

    assertThat(item.getLocationId(), is(secondFloorEconomicsLocationId));
  }

  //Scenario 8
  @Test
  public void itemTemporaryLocationTakesPrecedenceOverBothPermanentLocations() {
    final UUID popularReadingLocationId = UUID.randomUUID();
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();
    final UUID firstFloorComputerScienceLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withPermanentLocation(secondFloorEconomicsLocationId)
        .withTemporaryLocation(popularReadingLocationId)
        .create(),
      new HoldingBuilder()
        .withPermanentLocation(firstFloorComputerScienceLocationId)
        .withNoTemporaryLocation()
        .create(),
      new InstanceBuilder("").create(),
      null, null, null);

    assertThat(item.getLocationId(), is(popularReadingLocationId));
  }

  @Test
  public void itemTemporaryLocationTakesPrecedenceOverHoldingTemporaryLocation() {
    final UUID popularReadingLocationId = UUID.randomUUID();
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withNoPermanentLocation()
        .withTemporaryLocation(secondFloorEconomicsLocationId).create(),
      new HoldingBuilder()
        .withNoPermanentLocation()
        .withTemporaryLocation(popularReadingLocationId)
        .create(),
      new InstanceBuilder("").create(),
      null, null, null);

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
      null, null, null);

    assertThat(item.getLocationId(), is(thirdFloorDisplayCaseLocationId));
  }

  @Test
  public void onlyPermanentItemLocation() {
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withPermanentLocation(secondFloorEconomicsLocationId)
        .withNoTemporaryLocation()
        .create(),
      new HoldingBuilder()
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .create(),
      new InstanceBuilder("").create(),
      null, null, null);

    assertThat(item.getLocationId(), is(secondFloorEconomicsLocationId));
  }

  @Test
  public void onlyTemporaryItemLocation() {
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withNoPermanentLocation()
        .withTemporaryLocation(secondFloorEconomicsLocationId)
        .create(),
      new HoldingBuilder()
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .create(),
      new InstanceBuilder("").create(),
      null, null, null);

    assertThat(item.getLocationId(), is(secondFloorEconomicsLocationId));
  }

  @Test
  public void onlyTemporaryHoldingLocation() {
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .create(),
      new HoldingBuilder()
        .withNoPermanentLocation()
        .withTemporaryLocation(secondFloorEconomicsLocationId)
        .create(),
      new InstanceBuilder("").create(),
      null, null, null);

    assertThat(item.getLocationId(), is(secondFloorEconomicsLocationId));
  }

  @Test
  public void itemPermanentLocationTakesPrecedenceOverTemporaryHoldingLocation() {
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();
    final UUID thirdFloorDisplayCaseLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withPermanentLocation(secondFloorEconomicsLocationId)
        .withNoTemporaryLocation()
        .create(),
      new HoldingBuilder()
        .withNoPermanentLocation()
        .withTemporaryLocation(thirdFloorDisplayCaseLocationId)
        .create(),
      new InstanceBuilder("").create(),
      null, null, null);

    assertThat(item.getLocationId(), is(secondFloorEconomicsLocationId));
  }

  @Test
  public void itemTemporaryLocationTakesPrecedenceOverTemporaryHoldingLocation() {
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();
    final UUID thirdFloorDisplayCaseLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withNoPermanentLocation()
        .withTemporaryLocation(thirdFloorDisplayCaseLocationId)
        .create(),
      new HoldingBuilder()
        .withNoPermanentLocation()
        .withTemporaryLocation(secondFloorEconomicsLocationId)
        .create(),
      new InstanceBuilder("").create(),
      null, null, null);

    assertThat(item.getLocationId(), is(thirdFloorDisplayCaseLocationId));
  }

  @Test
  public void noLocations() {
    final Item item = new Item(
      new ItemBuilder()
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .create(),
      new HoldingBuilder()
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .create(),
      new InstanceBuilder("").create(),
      null, null, null);

    assertThat(item.getLocationId(), nullValue());
  }

  @Test
  public void noItem() {
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();

    final Item item = new Item(
      null,
      new HoldingBuilder()
        .withPermanentLocation(secondFloorEconomicsLocationId)
        .withNoTemporaryLocation()
        .create(),
      new InstanceBuilder("").create(),
      null, null, null);

    assertThat(item.getLocationId(), is(secondFloorEconomicsLocationId));
  }

  @Test
  public void noHolding() {
    final UUID secondFloorEconomicsLocationId = UUID.randomUUID();

    final Item item = new Item(
      new ItemBuilder()
        .withPermanentLocation(secondFloorEconomicsLocationId)
        .withNoTemporaryLocation()
        .create(),
      null,
      new InstanceBuilder("").create(),
      null, null, null);

    assertThat(item.getLocationId(), is(secondFloorEconomicsLocationId));
  }

  @Test
  public void noItemOrHolding() {
    final Item item = new Item(
      null,
      null,
      new InstanceBuilder("").create(),
      null, null, null);

    assertThat(item.getLocationId(), nullValue());
  }
}
