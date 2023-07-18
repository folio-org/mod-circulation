package api.support.builders;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.folio.circulation.domain.RequestType;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RequestPolicyBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String name;
  private final String description;
  private final List<RequestType> types;
  private Map<RequestType, Set<UUID>> allowedServicePoints;

  public RequestPolicyBuilder(List<RequestType> requestTypes) {

    this(UUID.randomUUID(),
      "Example Request Policy",
      "An example request policy",
      requestTypes
    );
  }

  public RequestPolicyBuilder(List<RequestType> requestTypes, UUID id) {

    this(id, "Example Request Policy" + id.toString(), "An example request policy", requestTypes);
  }

  public RequestPolicyBuilder(List<RequestType> requestTypes, String name, String description) {

    this(UUID.randomUUID(),
      name,
      description,
      requestTypes
    );
  }

  public RequestPolicyBuilder(UUID id, List<RequestType> requestTypes, String name,
    String description, Map<RequestType, Set<UUID>> allowedServicePoints) {

    this.id = id;
    this.name = name;
    this.description = description;
    this.types = requestTypes;
    this.allowedServicePoints = allowedServicePoints;
  }

  private RequestPolicyBuilder(
    UUID id,
    String name,
    String description,
    List<RequestType> types) {

    this.id = id;
    this.name = name;
    this.description = description;
    this.types = types;
  }

  @Override
  public JsonObject create() {
    JsonObject requestPolicy = new JsonObject();

    if (id != null) {
      put(requestPolicy, "id", id.toString());
    }

    put(requestPolicy, "name", this.name);
    put(requestPolicy, "description", this.description);

    JsonArray requestTypes = new JsonArray();

    for (RequestType t : types) {
      requestTypes.add(t.getValue());
    }

    put(requestPolicy, "requestTypes", requestTypes);

    JsonObject allowedServicePointsJson = new JsonObject();
    if (allowedServicePoints != null) {
      for (RequestType t : allowedServicePoints.keySet()) {
        allowedServicePointsJson.put(t.getValue(), allowedServicePoints.get(t));
      }
      requestPolicy.put("allowedServicePoints", allowedServicePointsJson);
    }

    return requestPolicy;
  }
}
