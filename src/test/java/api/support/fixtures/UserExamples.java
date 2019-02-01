package api.support.fixtures;

import api.support.builders.UserBuilder;

public class UserExamples {
  public static UserBuilder basedUponStevenJones() {
    return new UserBuilder()
      .withName("Jones", "Steven")
      .withBarcode("5694596854");
  }

  public static UserBuilder basedUponJessicaPontefract() {
    return new UserBuilder()
      .withName("Pontefract", "Jessica")
      .withBarcode("7697595697");
  }

  static UserBuilder basedUponRebeccaStuart() {
    return new UserBuilder()
      .withName("Stuart", "Rebecca")
      .withBarcode("6059539205");
  }

  static UserBuilder basedUponJamesRodwell() {
    return new UserBuilder()
      .withName("Rodwell", "James")
      .withBarcode("6430530304");

  }

  static UserBuilder basedUponCharlotteBroadwell() {
    return new UserBuilder()
      .withName("Broadwell", "Charlotte")
      .withBarcode("6430705932");
  }
}
