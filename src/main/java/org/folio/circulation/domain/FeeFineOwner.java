package org.folio.circulation.domain;

import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true)
public class FeeFineOwner {
  @ToString.Include
  private final String id;
  @ToString.Include
  private final String owner;
  private final List<String> servicePoints;

  public FeeFineOwner(String id, String owner, List<String> servicePoints) {
    this.id = id;
    this.owner = owner;
    this.servicePoints = servicePoints;
  }

  public static FeeFineOwner from(JsonObject jsonObject) {
    return new FeeFineOwner(jsonObject.getString("id"), jsonObject.getString("owner"),
      jsonObject.getJsonArray("servicePointOwner", new JsonArray()).stream()
        .map(e -> ((JsonObject) e).getString("value"))
        .collect(Collectors.toList()));
  }

  public String getId() {
    return id;
  }

  public String getOwner() {
    return owner;
  }

  public List<String> getServicePoints() {
    return servicePoints;
  }
}
