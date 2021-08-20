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

  public UUID getType() {
    return type;
  }

  public String getAddressLineOne() {
    return addressLineOne;
  }

  public String getAddressLineTwo() {
    return addressLineTwo;
  }

  public String getCity() {
    return city;
  }

  public String getRegion() {
    return region;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public String getCountryId() {
    return countryId;
  }
}
