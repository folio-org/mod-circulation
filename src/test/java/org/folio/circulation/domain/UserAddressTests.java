package org.folio.circulation.domain;

import static api.support.fixtures.AddressExamples.HOME_ADDRESS_TYPE;
import static api.support.fixtures.AddressExamples.RamkinResidence;
import static api.support.fixtures.AddressExamples.SiriusBlack;
import static api.support.fixtures.AddressExamples.WORK_ADDRESS_TYPE;
import static api.support.fixtures.AddressExamples.patriciansPalace;
import static api.support.matchers.UUIDMatcher.is;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import api.support.builders.Address;
import api.support.builders.UserBuilder;
import io.vertx.core.json.JsonObject;

public class UserAddressTests {
  @Test
  public void noAddressFoundWhenNoAddresses() {
    final User user = new User(new UserBuilder()
      .withNoAddresses()
      .create());

    JsonObject foundAddress = user.getAddressByType(WORK_ADDRESS_TYPE.toString());

    assertThat("Should not find an address", foundAddress, nullValue());
  }

  @Test
  public void addressFoundWhenOnlyAddressHasSameType() {
    final User user = new User(new UserBuilder()
      .withAddress(RamkinResidence())
      .create());

    JsonObject foundAddress = user.getAddressByType(HOME_ADDRESS_TYPE.toString());

    assertThat("Should find address", foundAddress, notNullValue());
    assertThat(foundAddress.getString("addressTypeId"), is(HOME_ADDRESS_TYPE));
    assertThat(foundAddress.getString("addressLine1"), is("Ramkin Residence"));
  }

  @Test
  public void addressFoundWhenOtherAddressesAreDifferentTypes() {
    final User user = new User(new UserBuilder()
      .withAddress(SiriusBlack())
      .withAddress(patriciansPalace())
      .withAddress(RamkinResidence())
      .create());

    JsonObject foundAddress = user.getAddressByType(WORK_ADDRESS_TYPE.toString());

    assertThat("Should find address", foundAddress, notNullValue());
    assertThat(foundAddress.getString("addressTypeId"), is(WORK_ADDRESS_TYPE));
    assertThat(foundAddress.getString("addressLine1"), is("Patrician's Palace"));
  }

  @Test
  public void noAddressFoundWhenOnlyOtherTypeAddresses() {
    final User user = new User(new UserBuilder()
      .withAddress(RamkinResidence())
      .create());

    JsonObject foundAddress = user.getAddressByType(WORK_ADDRESS_TYPE.toString());

    assertThat("Should not find an address", foundAddress, nullValue());
  }

  @Test
  public void noAddressFoundWhenOnlyAddressHasNoType() {
    //TODO: Replace with address builder
    final User user = new User(new UserBuilder()
      .withAddress(new Address(null, "Fake address", null, null, null, null, null))
      .create());

    JsonObject foundAddress = user.getAddressByType(WORK_ADDRESS_TYPE.toString());

    assertThat("Should not find an address", foundAddress, nullValue());
  }
}
