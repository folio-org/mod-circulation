package api.support.builders;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.circulation.support.http.client.IndividualResource;

import static org.folio.circulation.support.JsonPropertyFetcher.*;

public class ServicePointBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String name;
  private final String code;
  private final String discoveryDisplayName;
  private final String description;
  private final Integer shelvingLagTime;
  private final Boolean pickupLocation;
    
  public ServicePointBuilder(
      UUID id, 
      String name,
      String code,
      String discoveryDisplayName,
      String description,
      Integer shelvingLagTime,
      Boolean pickupLocation) {
    this.id = id;
    this.name = name;
    this.code = code;
    this.discoveryDisplayName = discoveryDisplayName;
    this.description = description;
    this.shelvingLagTime = shelvingLagTime;
    this.pickupLocation = pickupLocation;
  }
  
  public ServicePointBuilder(String name, String code, String discoveryDisplayName) {
    this(
      UUID.randomUUID(),
        name,
        code,
        discoveryDisplayName,
        null,
        null,
        false);
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
        getBooleanProperty(representation, "pickupLocation")
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
      this.pickupLocation);
  }
  
  public ServicePointBuilder withName(String newName) {
    return new ServicePointBuilder(
      this.id,
      newName,
      this.code,
      this.discoveryDisplayName,
      this.description,
      this.shelvingLagTime,
      this.pickupLocation);
  }

  public ServicePointBuilder withCode(String newCode) {
    return new ServicePointBuilder(
      this.id,
      this.name,
      newCode,
      this.discoveryDisplayName,
      this.description,
      this.shelvingLagTime,
      this.pickupLocation);
  }  
  
  public ServicePointBuilder withDiscoveryDisplayName(String newDiscoveryDisplayName) {
    return new ServicePointBuilder(
      this.id,
      this.name,
      this.code,
      newDiscoveryDisplayName,
      this.description,
      this.shelvingLagTime,
      this.pickupLocation);
  }  
  
  public ServicePointBuilder withDescription(String newDescription) {
    return new ServicePointBuilder(
      this.id,
      this.name,
      this.code,
      this.discoveryDisplayName,
      newDescription,
      this.shelvingLagTime,
      this.pickupLocation);
  }  
  
  public ServicePointBuilder withShelvingLagTime(Integer newShelvingLagTime) {
    return new ServicePointBuilder(
      this.id,
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.description,
      newShelvingLagTime,
      this.pickupLocation);
  }  
  
  public ServicePointBuilder withPickupLocation(Boolean newPickupLocation) {
    return new ServicePointBuilder(
      this.id,
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.description,
      this.shelvingLagTime,
      newPickupLocation);
  }
}
