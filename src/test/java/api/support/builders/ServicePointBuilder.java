package api.support.builders;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import api.support.http.IndividualResource;
import org.folio.circulation.domain.policy.ExpirationDateManagement;

import static org.folio.circulation.support.json.JsonPropertyFetcher.*;

public class ServicePointBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String name;
  private final String code;
  private final String discoveryDisplayName;
  private final String description;
  private final Integer shelvingLagTime;
  private final Boolean pickupLocation;
  private final JsonObject holdShelfExpiryPeriod;

  private final String holdShelfClosedLibraryDateManagement;
  private final Boolean ecsRequestRouting;

  public ServicePointBuilder(
      UUID id,
      String name,
      String code,
      String discoveryDisplayName,
      String description,
      Integer shelvingLagTime,
      Boolean pickupLocation,
      JsonObject holdShelfExpiryPeriod,
      String holdShelfClosedLibraryDateManagement,
      Boolean ecsRequestRouting) {
    this.id = id;
    this.name = name;
    this.code = code;
    this.discoveryDisplayName = discoveryDisplayName;
    this.description = description;
    this.shelvingLagTime = shelvingLagTime;
    this.pickupLocation = pickupLocation;
    this.holdShelfExpiryPeriod = holdShelfExpiryPeriod;
    this.holdShelfClosedLibraryDateManagement = holdShelfClosedLibraryDateManagement;
    this.ecsRequestRouting = ecsRequestRouting;
  }

  public ServicePointBuilder(String name, String code, String discoveryDisplayName) {
    this(
      UUID.randomUUID(),
        name,
        code,
        discoveryDisplayName,
        null,
        null,
        false,
        null,
      null,
      null);
  }

  public static ServicePointBuilder from(IndividualResource response) {
    JsonObject representation = response.getJson();
    return new ServicePointBuilder(
        UUID.fromString(representation.getString("id")),
        getProperty(representation, "name"),
        getProperty(representation, "code"),
        getProperty(representation, "discoveryDisplayName"),
        getProperty(representation, "description"),
        getIntegerProperty(representation, "shelvingLagTime", null),
        getBooleanProperty(representation, "pickupLocation"),
        getObjectProperty(representation, "holdShelfExpiryPeriod"),
        getProperty(representation, "holdShelfClosedLibraryDateManagement"),
        getBooleanProperty(representation, "ecsRequestRouting")
      );
  }

  @Override
  public JsonObject create() {
    JsonObject servicePoint = new JsonObject();
    put(servicePoint, "id", this.id);
    put(servicePoint, "name", this.name);
    put(servicePoint, "code", this.code);
    put(servicePoint, "discoveryDisplayName", this.discoveryDisplayName);
    put(servicePoint, "description", this.description);
    put(servicePoint, "shelvingLagTime", this.shelvingLagTime);
    put(servicePoint, "pickupLocation", this.pickupLocation);
    put(servicePoint, "holdShelfExpiryPeriod", this.holdShelfExpiryPeriod);
    put(servicePoint, "holdShelfClosedLibraryDateManagement", this.holdShelfClosedLibraryDateManagement);
    put(servicePoint, "ecsRequestRouting", this.ecsRequestRouting);

    return servicePoint;
  }

  public ServicePointBuilder withId(UUID newId) {
    return new ServicePointBuilder(
      newId,
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.description,
      this.shelvingLagTime,
      this.pickupLocation,
      this.holdShelfExpiryPeriod,
      this.holdShelfClosedLibraryDateManagement,
      this.ecsRequestRouting);
  }

  public ServicePointBuilder withName(String newName) {
    return new ServicePointBuilder(
      this.id,
      newName,
      this.code,
      this.discoveryDisplayName,
      this.description,
      this.shelvingLagTime,
      this.pickupLocation,
      this.holdShelfExpiryPeriod,
      this.holdShelfClosedLibraryDateManagement,
      this.ecsRequestRouting);
  }

  public ServicePointBuilder withCode(String newCode) {
    return new ServicePointBuilder(
      this.id,
      this.name,
      newCode,
      this.discoveryDisplayName,
      this.description,
      this.shelvingLagTime,
      this.pickupLocation,
      this.holdShelfExpiryPeriod,
      this.holdShelfClosedLibraryDateManagement,
      this.ecsRequestRouting);
  }

  public ServicePointBuilder withDiscoveryDisplayName(String newDiscoveryDisplayName) {
    return new ServicePointBuilder(
      this.id,
      this.name,
      this.code,
      newDiscoveryDisplayName,
      this.description,
      this.shelvingLagTime,
      this.pickupLocation,
      this.holdShelfExpiryPeriod,
      this.holdShelfClosedLibraryDateManagement,
      this.ecsRequestRouting);
  }

  public ServicePointBuilder withDescription(String newDescription) {
    return new ServicePointBuilder(
      this.id,
      this.name,
      this.code,
      this.discoveryDisplayName,
      newDescription,
      this.shelvingLagTime,
      this.pickupLocation,
      this.holdShelfExpiryPeriod,
      this.holdShelfClosedLibraryDateManagement,
      this.ecsRequestRouting);
  }

  public ServicePointBuilder withShelvingLagTime(Integer newShelvingLagTime) {
    return new ServicePointBuilder(
      this.id,
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.description,
      newShelvingLagTime,
      this.pickupLocation,
      this.holdShelfExpiryPeriod,
      this.holdShelfClosedLibraryDateManagement,
      this.ecsRequestRouting);
  }

  public ServicePointBuilder withPickupLocation(Boolean newPickupLocation) {
    return new ServicePointBuilder(
      this.id,
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.description,
      this.shelvingLagTime,
      newPickupLocation,
      this.holdShelfExpiryPeriod,
      this.holdShelfClosedLibraryDateManagement,
      this.ecsRequestRouting);
  }

  public ServicePointBuilder withHoldShelfExpriyPeriod(int duration, String intervalId) {
    final JsonObject holdShelfExpiryPeriod = new JsonObject();

    put(holdShelfExpiryPeriod, "duration", duration);
    put(holdShelfExpiryPeriod, "intervalId", intervalId);

    return new ServicePointBuilder(
      this.id,
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.description,
      this.shelvingLagTime,
      this.pickupLocation,
      holdShelfExpiryPeriod,
      this.holdShelfClosedLibraryDateManagement,
      this.ecsRequestRouting);
  }

  public ServicePointBuilder withholdShelfClosedLibraryDateManagement(String expirationDateManagement) {
    return new ServicePointBuilder(
      this.id,
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.description,
      this.shelvingLagTime,
      this.pickupLocation,
      this.holdShelfExpiryPeriod,
      expirationDateManagement,
      this.ecsRequestRouting);
  }

  public ServicePointBuilder withEcsRequestRouting(Boolean ecsRequestRouting) {
    return new ServicePointBuilder(
      this.id,
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.description,
      this.shelvingLagTime,
      this.pickupLocation,
      this.holdShelfExpiryPeriod,
      this.holdShelfClosedLibraryDateManagement,
      ecsRequestRouting);
  }
}
