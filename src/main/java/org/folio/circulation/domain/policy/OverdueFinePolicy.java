package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class OverdueFinePolicy {
  private final JsonObject representation;

  public OverdueFinePolicy(JsonObject representation) {
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

  public static OverdueFinePolicy from(JsonObject representation) {
    return new OverdueFinePolicy(representation);
  }

  public static OverdueFinePolicy unknown(String id) {
    return new UnknownLoanPolicy(id);
  }

  //TODO: Improve this to be a proper null object
  // requires significant rework of the loan policy interface
  private static class UnknownLoanPolicy extends OverdueFinePolicy {
    UnknownLoanPolicy(String id) {
      super(new JsonObject().put("id", id));
    }
  }
}
