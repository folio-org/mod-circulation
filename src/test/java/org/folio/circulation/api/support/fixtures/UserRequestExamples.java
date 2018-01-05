package org.folio.circulation.api.support.fixtures;

import org.folio.circulation.api.support.builders.UserRequestBuilder;

public class UserRequestExamples {
  public static UserRequestBuilder basedUponStevenJones() {
    return new UserRequestBuilder()
      .withName("Jones", "Steven")
      .withBarcode("5694596854");
  }

  public static UserRequestBuilder basedUponJessicaPontefract() {
    return new UserRequestBuilder()
      .withName("Jessica", "Pontefract")
      .withBarcode("7697595697");
  }
}
