package org.folio.circulation.domain;

import static java.util.Collections.unmodifiableSet;
import static org.apache.commons.collections4.SetUtils.hashSet;

import java.util.Set;

import io.vertx.core.json.JsonObject;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true)
public class FeeFine {
  public static final String OVERDUE_FINE_TYPE = "Overdue fine";
  public static final String LOST_ITEM_FEE_TYPE = "Lost item fee";
  public static final String LOST_ITEM_ACTUAL_COST_FEE_TYPE = "Lost item fee (actual cost)";
  public static final String LOST_ITEM_PROCESSING_FEE_TYPE = "Lost item processing fee";

  private static final Set<String> LOST_ITEM_FEE_TYPES =
    unmodifiableSet(hashSet(LOST_ITEM_FEE_TYPE, LOST_ITEM_ACTUAL_COST_FEE_TYPE,
      LOST_ITEM_PROCESSING_FEE_TYPE));

  @ToString.Include
  private final String id;
  @ToString.Include
  private final String ownerId;
  @ToString.Include
  private final String feeFineType;

  public FeeFine(String id, String ownerId, String feeFineType) {
    this.id = id;
    this.ownerId = ownerId;
    this.feeFineType = feeFineType;
  }

  public static Set<String> lostItemFeeTypes() {
    return LOST_ITEM_FEE_TYPES;
  }

  public static FeeFine from(JsonObject jsonObject) {
    return new FeeFine(jsonObject.getString("id"), jsonObject.getString("ownerId"),
      jsonObject.getString("feeFineType"));
  }

  public JsonObject toJson() {
    JsonObject jsonObject = new JsonObject();

    jsonObject.put("id", this.id);
    jsonObject.put("ownerId", this.ownerId);
    jsonObject.put("feeFineType", this.feeFineType);

    return jsonObject;
  }

  public String getId() {
    return id;
  }

  public String getFeeFineType() {
    return feeFineType;
  }
}