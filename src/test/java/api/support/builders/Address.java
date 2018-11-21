package api.support.builders;

import java.util.UUID;

public class Address {
  private UUID type;
  private String addressLineOne;
  private String addressLineTwo;
  private String city;
  private String region;
  private String postalCode;
  private String countryId;

  public Address(
    UUID type,
    String addressLineOne,
    String addressLineTwo,
    String city,
    String region,
    String postalCode,
    String countryId) {

    this.type = type;
    this.addressLineOne = addressLineOne;
    this.addressLineTwo = addressLineTwo;
    this.city = city;
    this.region = region;
    this.postalCode = postalCode;
    this.countryId = countryId;
  }

  UUID getType() {
    return type;
  }

  String getAddressLineOne() {
    return addressLineOne;
  }

  String getAddressLineTwo() {
    return addressLineTwo;
  }

  String getCity() {
    return city;
  }

  String getRegion() {
    return region;
  }

  String getPostalCode() {
    return postalCode;
  }

  String getCountryId() {
    return countryId;
  }
}
