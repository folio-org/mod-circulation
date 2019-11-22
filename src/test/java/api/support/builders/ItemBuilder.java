package api.support.builders;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ItemBuilder extends JsonBuilder implements Builder {

  public static final String AVAILABLE = "Available";
  public static final String CHECKED_OUT = "Checked out";
  public static final String AWAITING_PICKUP = "Awaiting pickup";
  public static final String AWAITING_DELIVERY = "Awaiting delivery";
  public static final String IN_TRANSIT = "In transit";
  public static final String PAGED = "Paged";
  public static final String MISSING = "Missing";
  public static final String ON_ORDER = "On order";
  public static final String IN_PROCESS = "In process";

  private final UUID id;
  private final UUID holdingId;
  private final String barcode;
  private final String status;
  private final UUID materialTypeId;
  private final UUID permanentLocationId;
  private final UUID temporaryLocationId;
  private final UUID permanentLoanTypeId;
  private final UUID temporaryLoanTypeId;
  private final String itemLevelCallNumber;
  private final String itemLevelCallNumberPrefix;
  private final String itemLevelCallNumberSuffix;
  private String enumeration;
  private List<String> copyNumbers;
  private List<String> yearCaption;
  private String volume;

  public ItemBuilder() {
    this(UUID.randomUUID(), null, "565578437802", AVAILABLE,
      null, null, null, null, null, null, null, null, null, null, null, null);
  }

  private ItemBuilder(
    UUID id,
    UUID holdingId,
    String barcode,
    String status,
    UUID permanentLocationId,
    UUID temporaryLocationId,
    UUID materialTypeId,
    UUID permanentLoanTypeId,
    UUID temporaryLoanTypeId,
    String enumeration,
    List<String> copyNumbers,
    String itemLevelCallNumber,
    String itemLevelCallNumberPrefix,
    String itemLevelCallNumberSuffix,
    String volume,
    List<String> yearCaption) {

    this.id = id;
    this.holdingId = holdingId;
    this.barcode = barcode;
    this.status = status;
    this.temporaryLocationId = temporaryLocationId;
    this.materialTypeId = materialTypeId;
    this.permanentLocationId = permanentLocationId;
    this.temporaryLoanTypeId = temporaryLoanTypeId;
    this.permanentLoanTypeId = permanentLoanTypeId;
    this.enumeration = enumeration;
    this.copyNumbers = copyNumbers;
    this.itemLevelCallNumber = itemLevelCallNumber;
    this.itemLevelCallNumberPrefix = itemLevelCallNumberPrefix;
    this.itemLevelCallNumberSuffix = itemLevelCallNumberSuffix;
    this.volume = volume;
    this.yearCaption = yearCaption;
  }

  public JsonObject create() {
    JsonObject itemRequest = new JsonObject();

    put(itemRequest, "id", id);
    put(itemRequest, "barcode", barcode);
    put(itemRequest, "holdingsRecordId", holdingId);
    put(itemRequest, "materialTypeId", materialTypeId);
    put(itemRequest, "permanentLoanTypeId", permanentLoanTypeId);
    put(itemRequest, "temporaryLoanTypeId", temporaryLoanTypeId);
    put(itemRequest, "permanentLocationId", permanentLocationId);
    put(itemRequest, "temporaryLocationId", temporaryLocationId);
    put(itemRequest, "status", status, new JsonObject().put("name", status));
    put(itemRequest, "enumeration", enumeration);
    put(itemRequest, "copyNumbers", new JsonArray(copyNumbers));
    put(itemRequest, "itemLevelCallNumber", itemLevelCallNumber);
    put(itemRequest, "itemLevelCallNumberPrefix", itemLevelCallNumberPrefix);
    put(itemRequest, "itemLevelCallNumberSuffix", itemLevelCallNumberSuffix);
    put(itemRequest, "volume", volume);
    put(itemRequest, "yearCaption", new JsonArray(yearCaption));

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

  public ItemBuilder onOrder() {
    return withStatus(ON_ORDER);
  }

  public ItemBuilder inProcess() {
    return withStatus(IN_PROCESS);
  }

  private ItemBuilder withStatus(String status) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      status,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumbers,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption);
  }

  public ItemBuilder withBarcode(String barcode) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      barcode,
      this.status,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumbers,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption);
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
      locationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumbers,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption);
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
      this.permanentLocationId,
      locationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumbers,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption);
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
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumbers,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption);
  }

  public ItemBuilder withMaterialType(UUID materialTypeId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.permanentLocationId,
      this.temporaryLocationId,
      materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumbers,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption);
  }

  public ItemBuilder withPermanentLoanType(UUID loanTypeId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      loanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumbers,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption);
  }

  public ItemBuilder withTemporaryLoanType(UUID loanTypeId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      loanTypeId,
      this.enumeration,
      this.copyNumbers,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption);
  }

  public ItemBuilder withId(UUID id) {
    return new ItemBuilder(
      id,
      this.holdingId,
      this.barcode,
      this.status,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumbers,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption);
  }

  public ItemBuilder withEnumeration(String enumeration) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      enumeration,
      this.copyNumbers,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption);
  }

  public ItemBuilder withCopyNumbers(List<String> copyNumbers) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      copyNumbers,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption);
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
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumbers,
      itemLevelCallNumber,
      itemLevelCallNumberPrefix,
      itemLevelCallNumberSuffix,
      this.volume,
      this.yearCaption);
  }

  public ItemBuilder withVolume(String volume) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.materialTypeId,
      this.permanentLoanTypeId,
      this.temporaryLoanTypeId,
      this.enumeration,
      this.copyNumbers,
      this.itemLevelCallNumber,
      this.itemLevelCallNumberPrefix,
      this.itemLevelCallNumberSuffix,
      volume,
      this.yearCaption);
  }

    public ItemBuilder withYearCaption(List<String> yearCaption) {
      return new ItemBuilder(
        this.id,
        this.holdingId,
        this.barcode,
        this.status,
        this.permanentLocationId,
        this.temporaryLocationId,
        this.materialTypeId,
        this.permanentLoanTypeId,
        this.temporaryLoanTypeId,
        this.enumeration,
        this.copyNumbers,
        this.itemLevelCallNumber,
        this.itemLevelCallNumberPrefix,
        this.itemLevelCallNumberSuffix,
        this.volume,
        yearCaption);
    }
}
