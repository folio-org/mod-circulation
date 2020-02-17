package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.List;
import java.util.UUID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class FeeFineOwnerBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String owner;
  private final String desc;
  private final List<JsonObject> servicePointOwner;
  private final String defaultChargeNoticeId;
  private final String defaultActionNoticeId;

  public FeeFineOwnerBuilder() {
    this.id = null;
    this.owner = null;
    this.desc = null;
    this.servicePointOwner = null;
    this.defaultChargeNoticeId = null;
    this.defaultActionNoticeId = null;
  }

  public FeeFineOwnerBuilder(UUID id, String owner, String desc, List<JsonObject> servicePointOwner,
    String defaultChargeNoticeId, String defaultActionNoticeId) {
    this.id = id;
    this.owner = owner;
    this.desc = desc;
    this.servicePointOwner = servicePointOwner;
    this.defaultChargeNoticeId = defaultChargeNoticeId;
    this.defaultActionNoticeId = defaultActionNoticeId;
  }

  @Override
  public JsonObject create() {
    JsonObject object = new JsonObject();

    write(object, "id", id);
    write(object, "owner", owner);
    write(object, "desc", desc);
    write(object, "servicePointOwner", servicePointOwner == null ? new JsonArray() :
      new JsonArray(servicePointOwner));
    write(object, "defaultChargeNoticeId", defaultChargeNoticeId);
    write(object, "defaultActionNoticeId", defaultActionNoticeId);

    return object;
  }

  public FeeFineOwnerBuilder withId(UUID id) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId);
  }
  public FeeFineOwnerBuilder withOwner(String owner) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId);
  }
  public FeeFineOwnerBuilder withDesc(String desc) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId);
  }
  public FeeFineOwnerBuilder withServicePointOwner(List<JsonObject> servicePointOwner) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId);
  }
  public FeeFineOwnerBuilder withDefaultChargeNoticeId(String defaultChargeNoticeId) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId);
  }
  public FeeFineOwnerBuilder withDefaultActionNoticeId(String defaultActionNoticeId) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId);
  }
}
