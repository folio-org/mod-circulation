package api.support.fixtures;

import java.util.UUID;

import api.APITestSuite;
import api.support.builders.ItemBuilder;

public class ItemExamples {
  public static ItemBuilder basedUponSmallAngryPlanet(UUID bookMaterialTypeId) {
    return new ItemBuilder()
      .withPermanentLoanType(APITestSuite.canCirculateLoanTypeId())
      .withMaterialType(bookMaterialTypeId)
      .withBarcode("036000291452");
  }

  static ItemBuilder basedUponNod(UUID bookMaterialTypeId) {
    return new ItemBuilder()
      .withPermanentLoanType(APITestSuite.canCirculateLoanTypeId())
      .withMaterialType(bookMaterialTypeId)
      .withBarcode("565578437802");
  }

  static ItemBuilder basedUponUprooted(UUID bookMaterialTypeId) {
    return new ItemBuilder()
      .withPermanentLoanType(APITestSuite.canCirculateLoanTypeId())
      .withMaterialType(bookMaterialTypeId)
      .withBarcode("657670342075");
  }

  public static ItemBuilder basedUponTemeraire(UUID bookMaterialTypeId) {
    return new ItemBuilder()
      .withPermanentLoanType(APITestSuite.canCirculateLoanTypeId())
      .withMaterialType(bookMaterialTypeId)
      .withBarcode("232142443432");
  }

  static ItemBuilder basedUponInterestingTimes(UUID bookMaterialTypeId) {
    return new ItemBuilder()
      .withPermanentLoanType(APITestSuite.canCirculateLoanTypeId())
      .withMaterialType(bookMaterialTypeId)
      .withBarcode("56454543534");
  }

  static ItemBuilder basedUponDunkirk(UUID videoRecordingMaterialTypeId) {
    return new ItemBuilder()
      .withPermanentLoanType(APITestSuite.canCirculateLoanTypeId())
      .withMaterialType(videoRecordingMaterialTypeId)
      .withBarcode("70594943205");
  }
}
