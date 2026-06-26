package api.support.builders;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

public class ItemBuilder extends JsonBuilder implements Builder {
  public static final String AVAILABLE = "Available";
  public static final String CHECKED_OUT = "Checked out";
  public static final String AWAITING_PICKUP = "Awaiting pickup";
  public static final String AWAITING_DELIVERY = "Awaiting delivery";
  public static final String IN_TRANSIT = "In transit";
  public static final String PAGED = "Paged";
  public static final String MISSING = "Missing";
  public static final String DECLARED_LOST = "Declared lost";
  public static final String ON_ORDER = "On order";
  public static final String IN_PROCESS = "In process";
  public static final String CLAIMED_RETURNED = "Claimed returned";
  public static final String INTELLECTUAL_ITEM = "Intellectual item";
  public static final String LOST_AND_PAID = "Lost and paid";

  private final UUID id;
  private final UUID holdingId;
  private final String barcode;
  private final String status;
  private final UUID materialTypeId;
  private final UUID effectiveLocationId;
  private final UUID permanentLocationId;
  private final UUID temporaryLocationId;
  private final UUID permanentLoanTypeId;
  private final UUID temporaryLoanTypeId;
  private final String itemLevelCallNumber;
  private final String itemLevelCallNumberPrefix;
  private final String itemLevelCallNumberSuffix;
  private String enumeration;
  private String copyNumber;
  private List<String> yearCaption;
  private String volume;
  private final String chronology;
  private String displaySummary;
  private String numberOfPieces;
  private String descriptionOfPieces;

  public ItemBuilder() {
    this(UUID.randomUUID(), null, "565578437802", AVAILABLE,
      null, null, null, null, null, null, null, null, null, null, null, null,
      Collections.emptyList(),
      null, null, null, null);
  }

  private ItemBuilder(
    UUID id,
    UUID holdingId,
    String barcode,
    String status,
    UUID effectiveLocationId,
    UUID permanentLocationId,
    UUID temporaryLocationId,
    UUID materialTypeId,
    UUID permanentLoanTypeId,
    UUID temporaryLoanTypeId,
    String enumeration,
    String copyNumber,
    String itemLevelCallNumber,
    String itemLevelCallNumberPrefix,
    String itemLevelCallNumberSuffix,
    String volume,
    List<String> yearCaption,
    String chronology,
    String displaySummary,
    String numberOfPieces,
    String descriptionOfPieces) {

    this.id = id;
    this.holdingId = holdingId;
    this.barcode = barcode;
    this.status = status;
    this.effectiveLocationId = effectiveLocationId;
    this.temporaryLocationId = temporaryLocationId;
    this.materialTypeId = materialTypeId;
    this.permanentLocationId = permanentLocationId;
    this.temporaryLoanTypeId = temporaryLoanTypeId;
    this.permanentLoanTypeId = permanentLoanTypeId;
    this.enumeration = enumeration;
    this.copyNumber = copyNumber;
    this.itemLevelCallNumber = itemLevelCallNumber;
    this.itemLevelCallNumberPrefix = itemLevelCallNumberPrefix;
    this.itemLevelCallNumberSuffix = itemLevelCallNumberSuffix;
    this.volume = volume;
    this.yearCaption = yearCaption;
    this.chronology = chronology;
    this.displaySummary = displaySummary;
    this.numberOfPieces = numberOfPieces;
    this.descriptionOfPieces = descriptionOfPieces;
  }

  public JsonObject create() {
    JsonObject itemRequest = new JsonObject();

    put(itemRequest, "id", id);
    put(itemRequest, "_version", 4);
    put(itemRequest, "barcode", barcode);
    put(itemRequest, "holdingsRecordId", holdingId);
    put(itemRequest, "materialTypeId", materialTypeId);
    put(itemRequest, "effectiveLocationId", effectiveLocationId);
    put(itemRequest, "permanentLoanTypeId", permanentLoanTypeId);
    put(itemRequest, "temporaryLoanTypeId", temporaryLoanTypeId);
    put(itemRequest, "permanentLocationId", permanentLocationId);
    put(itemRequest, "temporaryLocationId", temporaryLocationId);
    put(itemRequest, "status", status, new JsonObject().put("name", status));
    put(itemRequest, "enumeration", enumeration);
    put(itemRequest, "copyNumber", copyNumber);
    put(itemRequest, "itemLevelCallNumber", itemLevelCallNumber);
    put(itemRequest, "itemLevelCallNumberPrefix", itemLevelCallNumberPrefix);
    put(itemRequest, "itemLevelCallNumberSuffix", itemLevelCallNumberSuffix);
    put(itemRequest, "volume", volume);
    put(itemRequest, "yearCaption", yearCaption);
    put(itemRequest, "chronology", chronology);
    put(itemRequest, "displaySummary", displaySummary);
    put(itemRequest, "numberOfPieces", numberOfPieces);
    put(itemRequest, "descriptionOfPieces", descriptionOfPieces);

    return itemRequest;
  }

  public ItemBuilder checkOut() {
    return withStatus(CHECKED_OUT);
  }

  public ItemBuilder available() {
    return withStatus(AVAILABLE);
  }

  public ItemBuilder missing() {
    return withStatus(MISSING);
  }

  public ItemBuilder declaredLost() {
    return withStatus(DECLARED_LOST);
  }

  public ItemBuilder agedToLost() {
    return withStatus("Aged to lost");
  }

  public ItemBuilder lostAndPaid() {
    return withStatus("Lost and paid");
  }

  public ItemBuilder onOrder() {
    return withStatus(ON_ORDER);
  }

  public ItemBuilder inProcess() {
    return withStatus(IN_PROCESS);
  }

  public ItemBuilder claimedReturned() {
    return withStatus(CLAIMED_RETURNED);
  }

  public ItemBuilder withdrawn() {
    return withStatus("Withdrawn");
  }

  public ItemBuilder intellectualItem() {
    return withStatus(INTELLECTUAL_ITEM);
  }

  public ItemBuilder withStatus(String status) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withBarcode(String barcode) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withNoBarcode() {
    return withBarcode(null);
  }

  public ItemBuilder withPermanentLocation(IndividualResource location) {
    return withPermanentLocation(location.getId());
  }

  public ItemBuilder withPermanentLocation(UUID locationId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      locationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withNoPermanentLocation() {
    return withPermanentLocation((UUID) null);
  }

  public ItemBuilder withTemporaryLocation(IndividualResource location) {
    return withTemporaryLocation(location.getId());
  }

  public ItemBuilder withTemporaryLocation(UUID locationId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      locationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withNoTemporaryLocation() {
    return withTemporaryLocation((UUID) null);
  }

  public ItemBuilder forHolding(UUID holdingId) {
    return new ItemBuilder(
      this.id,
      holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withMaterialType(UUID materialTypeId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withPermanentLoanType(UUID loanTypeId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      loanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withTemporaryLoanType(UUID loanTypeId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      loanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withId(UUID id) {
    return new ItemBuilder(
      id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withEnumeration(String enumeration) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withCopyNumber(String copyNumber) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withCallNumber(
    String itemLevelCallNumber,
    String itemLevelCallNumberPrefix,
    String itemLevelCallNumberSuffix) {

    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      itemLevelCallNumber,
      itemLevelCallNumberPrefix,
      itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withVolume(String volume) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

    public ItemBuilder withYearCaption(List<String> yearCaption) {
      return new ItemBuilder(
        this.id,
        this.holdingId,
        this.barcode,
        this.status,
        this.effectiveLocationId,
        this.permanentLocationId,
        this.temporaryLocationId,
        this.materialTypeId,
        this.permanentLoanTypeId,
        this.temporaryLoanTypeId,
        this.enumeration,
        this.copyNumber,
        this.itemLevelCallNumber,
        this.itemLevelCallNumberPrefix,
        this.itemLevelCallNumberSuffix,
        this.volume,
        yearCaption,
        this.chronology,
        this.displaySummary,
        this.numberOfPieces,
        this.descriptionOfPieces);
    }

  public ItemBuilder withChronology(String chronology) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withDisplaySummary(String displaySummary) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withNumberOfPieces(String numberOfPieces) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      numberOfPieces,
      this.descriptionOfPieces);
  }

  public ItemBuilder withDescriptionOfPieces(String descriptionOfPieces) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      descriptionOfPieces);
  }

  public ItemBuilder withRandomBarcode() {
    return withBarcode(generateRandomBarcode());
  }

  public String generateRandomBarcode() {
    return String.valueOf(new Random().nextLong());
  }
  public ItemBuilder withEffectiveLocation(UUID effectiveLocationId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      effectiveLocationId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumber,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption,
      this.chronology,
      this.displaySummary,
      this.numberOfPieces,
      this.descriptionOfPieces);
  }
}
