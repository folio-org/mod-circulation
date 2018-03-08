package org.folio.circulation.api.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class UserProxyRequestBuilder implements Builder {

  private UUID id;
  private String userId;
  private String proxyUserId;

  private String requestForSponsor;
  private String createdDate;
  private String expirationDate;
  private String status;
  private String accrueTo;
  private String notificationsTo;


  public UserProxyRequestBuilder() {
    this(UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
      "Yes", "2018-02-28T12:30:23+00:00", "2999-02-28T12:30:23+00:00" , "Active" ,
      "Sponsor","Sponsor");
  }

  public UserProxyRequestBuilder(UUID id, String userId, String proxyUserId,
      String requestForSponsor, String createdDate, String expirationDate, String status,
      String accrueTo, String notificationsTo) {
    super();
    this.id = id;
    this.userId = userId;
    this.proxyUserId = proxyUserId;
    this.requestForSponsor = requestForSponsor;
    this.createdDate = createdDate;
    this.expirationDate = expirationDate;
    this.status = status;
    this.accrueTo = accrueTo;
    this.notificationsTo = notificationsTo;
  }

  @Override
  public JsonObject create() {

    JsonObject request = new JsonObject();
    request.put("id", this.id.toString());
    request.put("userId", this.userId);
    request.put("proxyUserId", this.proxyUserId);
    request.put("meta", new JsonObject());

    if(this.requestForSponsor != null) {
      request.getJsonObject("meta").put("requestForSponsor" , this.requestForSponsor);
    }
    if(this.createdDate != null) {
      request.getJsonObject("meta").put("createdDate" , this.createdDate);
    }
    if(this.expirationDate != null) {
      request.getJsonObject("meta").put("expirationDate" , this.expirationDate);
    }
    if(this.status != null) {
      request.getJsonObject("meta").put("status" , this.status);
    }
    if(this.accrueTo != null) {
      request.getJsonObject("meta").put("accrueTo" , this.accrueTo);
    }
    if(this.notificationsTo != null) {
      request.getJsonObject("meta").put("notificationsTo" , this.notificationsTo);
    }
    return request;
  }

  public UserProxyRequestBuilder withValidationFields(String expDate, String status, String userId, String proxyId) {
    this.expirationDate = expDate;
    this.status = status;
    this.userId = userId;
    this.proxyUserId = proxyId;
    return this;
  }

}
