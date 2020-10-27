package api.support.fixtures;

import java.util.UUID;

import api.support.builders.ItemBuilder;

public class ItemExamples {
  public static ItemBuilder basedUponSmallAngryPlanet(
    UUID bookMaterialTypeId,
    UUID loanTypeId) {

    return new ItemBuilder()
      .withPermanentLoanType(loanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .withBarcode("036000291452");
  }

  public static ItemBuilder basedUponSmallAngryPlanet(
    UUID bookMaterialTypeId,
    UUID loanTypeId,
    String callNumber,
    String callNumberPrefix,
    String callNumberSuffix,
    String copyNumber) {

    return new ItemBuilder()
      .withPermanentLoanType(loanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .withBarcode("036000291452")
      .withCallNumber(callNumber, callNumberPrefix, callNumberSuffix)
      .withCopyNumber(copyNumber);
  }

  public static ItemBuilder basedUponNod(UUID bookMaterialTypeId,
    UUID loanTypeId) {

    return new ItemBuilder()
      .withPermanentLoanType(loanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .withBarcode("565578437802");
  }

  static ItemBuilder basedUponUprooted(
    UUID bookMaterialTypeId, UUID loanTypeId) {

    return new ItemBuilder()
      .withPermanentLoanType(loanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .withBarcode("657670342075");
  }

  public static ItemBuilder basedUponTemeraire(
    UUID bookMaterialTypeId,
    UUID loanTypeId) {

    return new ItemBuilder()
      .withPermanentLoanType(loanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .withBarcode("232142443432");
  }

  public static ItemBuilder basedUponInterestingTimes(
    UUID bookMaterialTypeId,
    UUID loanTypeId) {

    return new ItemBuilder()
      .withPermanentLoanType(loanTypeId)
      .withMaterialType(bookMaterialTypeId)
      .withBarcode("56454543534");
  }

  public static ItemBuilder basedUponDunkirk(
    UUID videoRecordingMaterialTypeId,
    UUID loanTypeId) {

    return new ItemBuilder()
      .withPermanentLoanType(loanTypeId)
      .withMaterialType(videoRecordingMaterialTypeId)
      .withBarcode("70594943205");
  }
}
