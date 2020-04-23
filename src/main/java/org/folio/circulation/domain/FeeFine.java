package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class FeeFine {
  public static final String OVERDUE_FINE_TYPE = "Overdue fine";
  public static final String LOST_ITEM_FEE_TYPE = "Lost item fee";
  public static final String LOST_ITEM_PROC_FEE_TYPE = "Lost item processing fee";
  public static final boolean AUTOMATIC_ON = true;
  public static final boolean AUTOMATIC_OFF = false;


  private final String id;
  private final String ownerId;
  private final String feeFineType;
  private final boolean automatic;


  public FeeFine(String id, String ownerId, String feeFineType) {
    this.id = id;
    this.ownerId = ownerId;
    this.feeFineType = feeFineType;
    this.automatic = AUTOMATIC_OFF;
  }
  
  public FeeFine(String id, String ownerId, String feeFineType, boolean automatic) {
    this.id = id;
    this.ownerId = ownerId;
    this.feeFineType = feeFineType;
    this.automatic = automatic;
  }



  public static FeeFine from(JsonObject jsonObject) {
    return new FeeFine(jsonObject.getString("id"), jsonObject.getString("ownerId"),
      jsonObject.getString("feeFineType"), jsonObject.getBoolean("automatic"));
  }

  public JsonObject toJson() {
    JsonObject jsonObject = new JsonObject();

    jsonObject.put("id", this.id);
    jsonObject.put("ownerId", this.ownerId);
    jsonObject.put("feeFineType", this.feeFineType);
    jsonObject.put("automatic", this.automatic);

    return jsonObject;
  }
  
  public boolean isAutomatic() {
    return automatic;
  }

  public String getId() {
    return id;
  }

  public String getFeeFineType() {
    return feeFineType;
  }
}
