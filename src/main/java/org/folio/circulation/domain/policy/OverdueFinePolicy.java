package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class OverdueFinePolicy {
  private final String id;
  private final String name;

  private OverdueFinePolicy(String id) {
    this(id, null);
  }

  private OverdueFinePolicy(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public static OverdueFinePolicy from(JsonObject lostItemPolicy) {
    return new OverdueFinePolicy(
      getProperty(lostItemPolicy, "id"),
      getProperty(lostItemPolicy, "name")
    );
  }

  public static OverdueFinePolicy unknown(String id) {
    return new OverdueFinePolicy.UnknownOverdueFinePolicy(id);
  }

  private static class UnknownOverdueFinePolicy extends OverdueFinePolicy {
    UnknownOverdueFinePolicy(String id) {
      super(id);
    }
  }
}
