package api.support.fixtures;

import api.support.builders.UserBuilder;

public class UserExamples {
  public static UserBuilder basedUponStevenJones() {
    return new UserBuilder()
      .withName("Jones","Steven","Jacob")
      .withBarcode("5694596854")
      .withActive(true);
  }

  public static UserBuilder basedUponJessicaPontefract() {
    return new UserBuilder()
      .withPreferredFirstName("Pontefract", "Jessica","Jess")
      .withBarcode("7697595697")
      .withActive(true);
  }

  static UserBuilder basedUponRebeccaStuart() {
    return new UserBuilder()
      .withName("Stuart", "Rebecca")
      .withBarcode("6059539205")
      .withActive(true);
  }

  static UserBuilder basedUponJamesRodwell() {
    return new UserBuilder()
      .withName("Rodwell", "James")
      .withBarcode("6430530304")
      .withActive(true);

  }

  static UserBuilder basedUponGroot() {
    return new UserBuilder()
      .withName("DcbSystem", "dcb")
      .withBarcode("6430530304")
      .withActive(true);
  }

  static UserBuilder basedUponJames() {
    return new UserBuilder()
      .withBarcode("6430530304")
      .withPreferredFirstName("kim", "james", "kimJ")
      .withActive(true);
  }

  static UserBuilder basedUponCharlotteBroadwell() {
    return new UserBuilder()
      .withName("Broadwell", "Charlotte")
      .withBarcode("6430705932")
      .withActive(true);
  }

  static UserBuilder basedUponBobbyBibbin() {
    return new UserBuilder()
      .withName("Bibbin", "Bobby")
      .withBarcode("6630705935")
      .withActive(true);
  }

  static UserBuilder basedUponHenryHanks() {
    return new UserBuilder()
      .withName("Hanks", "Henry")
      .withBarcode("6430777932")
      .withActive(true);
  }

  static UserBuilder basedUponDcbUser() {
    return new UserBuilder()
      .withName("DcbSystem", null)
      .withBarcode("dcb_user")
      .withType("dcb")
      .withActive(true);
  }
}
