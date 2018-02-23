package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.UserBuilder;

public class UserExamples {
  public static UserBuilder basedUponStevenJones() {
    return new UserBuilder()
      .withName("Jones", "Steven")
      .withBarcode("5694596854");
  }

  public static UserBuilder basedUponJessicaPontefract() {
    return new UserBuilder()
      .withName("Jessica", "Pontefract")
      .withBarcode("7697595697");
  }

  public static UserBuilder basedUponRebeccaStuart() {
    return new UserBuilder()
      .withName("Stuart", "Rebecca")
      .withBarcode("6059539205");
  }
}
