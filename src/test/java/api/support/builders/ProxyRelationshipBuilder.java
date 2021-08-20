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

  //Proxy relationship properties used to be kept within a informal "meta" object
  private boolean useMetaObject;

  public ProxyRelationshipBuilder() {
    this(UUID.randomUUID(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
      "Yes", "2018-02-28T12:30:23+00:00", "2999-02-28T12:30:23+00:00" , "Active" ,
      "Sponsor","Sponsor", false);
  }

  private ProxyRelationshipBuilder(
    UUID id,
    String userId,
    String proxyUserId,
    String requestForSponsor,
    String createdDate,
    String expirationDate,
    String status,
    String accrueTo,
    String notificationsTo,
    boolean useMetaObject) {

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
    this.useMetaObject = useMetaObject;
  }

  @Override
  public JsonObject create() {

    JsonObject request = new JsonObject();
    request.put("id", this.id.toString());
    request.put("userId", this.userId);
    request.put("proxyUserId", this.proxyUserId);

    //Can be used to put properties on the main object or the meta object
    //So can be used to verify fallback behaviour
    final JsonObject objectToPutValidationPropertiesOn;

    if(useMetaObject) {
      final JsonObject metaObject = new JsonObject();

      request.put("meta", metaObject);

      objectToPutValidationPropertiesOn = request.getJsonObject("meta");
    }
    else {
      objectToPutValidationPropertiesOn = request;
    }

    if(this.requestForSponsor != null) {
      objectToPutValidationPropertiesOn.put("requestForSponsor" , this.requestForSponsor);
    }

    if(this.expirationDate != null) {
      objectToPutValidationPropertiesOn.put("expirationDate" , this.expirationDate);
    }

    if(this.status != null) {
      objectToPutValidationPropertiesOn.put("status" , this.status);
    }

    if(this.accrueTo != null) {
      objectToPutValidationPropertiesOn.put("accrueTo" , this.accrueTo);
    }

    if(this.notificationsTo != null) {
      objectToPutValidationPropertiesOn.put("notificationsTo" , this.notificationsTo);
    }

    return request;
  }

  /**
   * Allows the validation properties to be created in either the top level object
   * or the informal "meta" object, in order to test both supported variants
   *
   * @param useMetaObject whether to use the "meta" object for validation properties
   *                      instead of main object in representation
   * @return A new instance of the builder with the changed property
   * @see https://issues.folio.org/browse/CIRC-107
   * @see https://issues.folio.org/browse/MODUSERS-68
   */
  public ProxyRelationshipBuilder useMetaObject(boolean useMetaObject) {
    return new ProxyRelationshipBuilder(
      this.id,
      this.userId,
      this.proxyUserId,
      this.requestForSponsor,
      this.createdDate,
      this.expirationDate,
      this.status,
      this.accrueTo,
      this.notificationsTo,
      useMetaObject
    );
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
      this.notificationsTo,
      false
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
      this.notificationsTo,
      false
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
      this.notificationsTo,
      false
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
      this.notificationsTo,
      false
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
      this.notificationsTo,
      false
    );
  }
}
