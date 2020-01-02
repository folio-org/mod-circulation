package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;


public class LostItemPolicy {
  private final JsonObject representation;

  public LostItemPolicy(JsonObject representation) {
    this.representation = representation;
  }

  private UUID id;
  private String name;

  public String getId() {
    return getProperty(representation, "id");
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return getProperty(representation, "name");
  }

  public void setName(String name) {
    this.name = name;
  }

  public static LostItemPolicy from(JsonObject representation) {
    return new LostItemPolicy(representation);
  }

  public static LostItemPolicy unknown(String id) {
    return new UnknownLoanPolicy(id);
  }

  //TODO: Improve this to be a proper null object
  // requires significant rework of the loan policy interface
  private static class UnknownLoanPolicy extends LostItemPolicy {
    UnknownLoanPolicy(String id) {
      super(new JsonObject().put("id", id));
    }
  }
}
