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
  public static final String IN_TRANSIT = "In transit";
  public static final String PAGED = "Paged";
  public static final String MISSING = "Missing";

  private final UUID id;
  private final UUID holdingId;
  private final String barcode;
  private final String status;
  private final UUID materialTypeId;
  private final UUID permanentLocationId;
  private final UUID temporaryLocationId;
  private final UUID permanentLoanTypeId;
  private final UUID temporaryLoanTypeId;
  private String enumeration;
  private List<String> copyNumbers;


  public ItemBuilder() {
    this(UUID.randomUUID(), null, "565578437802", AVAILABLE,
      null, null, null, null, null, null, null);
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
    List<String> copyNumbers) {

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
      this.copyNumbers);
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
      this.copyNumbers);
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
      this.copyNumbers);
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
      this.copyNumbers);
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
      this.copyNumbers);
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
      this.copyNumbers);
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
      this.copyNumbers);
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
      this.copyNumbers);
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
      this.copyNumbers);
  }

  public ItemBuilder withEnumeration(String enumeration) {
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
      enumeration,
      this.copyNumbers);
  }

  public ItemBuilder withCopyNumbers(List<String> copyNumbers) {
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
      copyNumbers);
  }
}
