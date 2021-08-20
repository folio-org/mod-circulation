package api.support.fixtures;

import java.util.UUID;

import api.support.builders.Address;

public class AddressExamples {
  public static final UUID WORK_ADDRESS_TYPE = UUID.randomUUID();
  public static final UUID HOME_ADDRESS_TYPE = UUID.randomUUID();
  //Would prefer realistic examples, however these at least make it obvious they are fake

  public static Address patriciansPalace() {
    return new Address(WORK_ADDRESS_TYPE, "Patrician's Palace",
      "Turnwise and Widdershins Broadway", "Ankh-Morpork", null, null, null);
  }

  public static Address RamkinResidence() {
    return new Address(HOME_ADDRESS_TYPE, "Ramkin Residence",
      "Scone Avenue", "Ankh-Morpork", null, null, null);
  }

  public static Address SiriusBlack() {
    return new Address(HOME_ADDRESS_TYPE, "12 Grimmauld Place",
      null, "London", "London region", "123456", "UK");
  }

  public static Address mainStreet() {
    return new Address(HOME_ADDRESS_TYPE, "16 Main St",
      "Apt 3a", "Northampton", "MA", "01060", "US");
  }
}
