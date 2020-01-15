package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class LostItemPolicy extends CirculationPolicy {

  private LostItemPolicy(String id){
    this(id, null);
  }

  private LostItemPolicy(String id, String name) {
    super(id, name);
  }

  public static LostItemPolicy from(JsonObject lostItemPolicy) {
    return new LostItemPolicy(
      getProperty(lostItemPolicy, "id"),
      getProperty(lostItemPolicy, "name")
    );
  }

  public static LostItemPolicy unknown(String id) {
    return new UnknownLostItemPolicy(id);
  }

  private static class UnknownLostItemPolicy extends LostItemPolicy {
    UnknownLostItemPolicy(String id) {
      super(id);
    }
  }
}
