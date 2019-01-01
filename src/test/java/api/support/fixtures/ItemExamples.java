package api.support.fixtures;

import api.APITestSuite;
import api.support.builders.ItemBuilder;

public class ItemExamples {
  public static ItemBuilder basedUponSmallAngryPlanet() {
    return new ItemBuilder()
      .withPermanentLoanType(APITestSuite.canCirculateLoanTypeId())
      .withMaterialType(MaterialTypesFixture.bookMaterialTypeId())
      .withBarcode("036000291452");
  }

  static ItemBuilder basedUponNod() {
    return new ItemBuilder()
      .withPermanentLoanType(APITestSuite.canCirculateLoanTypeId())
      .withMaterialType(MaterialTypesFixture.bookMaterialTypeId())
      .withBarcode("565578437802");
  }

  static ItemBuilder basedUponUprooted() {
    return new ItemBuilder()
      .withPermanentLoanType(APITestSuite.canCirculateLoanTypeId())
      .withMaterialType(MaterialTypesFixture.bookMaterialTypeId())
      .withBarcode("657670342075");
  }

  public static ItemBuilder basedUponTemeraire() {
    return new ItemBuilder()
      .withPermanentLoanType(APITestSuite.canCirculateLoanTypeId())
      .withMaterialType(MaterialTypesFixture.bookMaterialTypeId())
      .withBarcode("232142443432");
  }

  static ItemBuilder basedUponInterestingTimes() {
    return new ItemBuilder()
      .withPermanentLoanType(APITestSuite.canCirculateLoanTypeId())
      .withMaterialType(MaterialTypesFixture.bookMaterialTypeId())
      .withBarcode("56454543534");
  }

  static ItemBuilder basedUponDunkirk() {
    return new ItemBuilder()
      .withPermanentLoanType(APITestSuite.canCirculateLoanTypeId())
      .withMaterialType(MaterialTypesFixture.videoRecordingMaterialTypeId())
      .withBarcode("70594943205");
  }
}
