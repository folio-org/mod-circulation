package api.support.builders;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

import java.util.UUID;

public class ProxyRelationshipBuilder implements Builder {

  private UUID id;
  private String userId;
  private String proxyUserId;

  private String requestForSponsor;
  private String createdDate;
  private String expirationDate;
  private String status;
  private String accrueTo;
  private String notificationsTo;


  public ProxyRelationshipBuilder() {
    this(UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
      "Yes", "2018-02-28T12:30:23+00:00", "2999-02-28T12:30:23+00:00" , "Active" ,
      "Sponsor","Sponsor");
  }

  public ProxyRelationshipBuilder(
    UUID id,
    String userId,
    String proxyUserId,
    String requestForSponsor,
    String createdDate,
    String expirationDate,
    String status,
    String accrueTo,
    String notificationsTo) {

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

  public ProxyRelationshipBuilder expires(DateTime expirationDate) {
    return new ProxyRelationshipBuilder(
      this.id,
      this.userId,
      this.proxyUserId,
      this.requestForSponsor,
      this.createdDate,
      expirationDate.toString(),
      this.status,
      this.accrueTo,
      this.notificationsTo
    );
  }

  public ProxyRelationshipBuilder doesNotExpire() {
    return new ProxyRelationshipBuilder(
      this.id,
      this.userId,
      this.proxyUserId,
      this.requestForSponsor,
      this.createdDate,
      null,
      this.status,
      this.accrueTo,
      this.notificationsTo
    );
  }

  public ProxyRelationshipBuilder active() {
    return withStatus("Active");
  }

  public ProxyRelationshipBuilder inactive() {
    return withStatus("Inactive");
  }

  private ProxyRelationshipBuilder withStatus(String status) {
    return new ProxyRelationshipBuilder(
      this.id,
      this.userId,
      this.proxyUserId,
      this.requestForSponsor,
      this.createdDate,
      this.expirationDate,
      status,
      this.accrueTo,
      this.notificationsTo
    );
  }

  public ProxyRelationshipBuilder sponsor(UUID sponsoringUserId) {
    return new ProxyRelationshipBuilder(
      this.id,
      sponsoringUserId.toString(),
      this.proxyUserId,
      this.requestForSponsor,
      this.createdDate,
      this.expirationDate,
      this.status,
      this.accrueTo,
      this.notificationsTo
    );
  }

  public ProxyRelationshipBuilder proxy(UUID proxyUserId) {
    return new ProxyRelationshipBuilder(
      this.id,
      this.userId,
      proxyUserId.toString(),
      this.requestForSponsor,
      this.createdDate,
      this.expirationDate,
      this.status,
      this.accrueTo,
      this.notificationsTo
    );
  }
}
