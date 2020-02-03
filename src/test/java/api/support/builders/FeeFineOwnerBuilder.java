package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.List;
import java.util.UUID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class FeeFineOwnerBuilder extends JsonBuilder implements Builder {
  private UUID id;
  private String owner;
  private String desc;
  private List<JsonObject> servicePointOwner;
  private String defaultChargeNoticeId;
  private String defaultActionNoticeId;
  private JsonObject metadata;

  public FeeFineOwnerBuilder() {
  }

  public FeeFineOwnerBuilder(UUID id, String owner, String desc,
                             List<JsonObject> servicePointOwner,
                             String defaultChargeNoticeId, String defaultActionNoticeId,
                             JsonObject metadata) {
    this.id = id;
    this.owner = owner;
    this.desc = desc;
    this.servicePointOwner = servicePointOwner;
    this.defaultChargeNoticeId = defaultChargeNoticeId;
    this.defaultActionNoticeId = defaultActionNoticeId;
    this.metadata = metadata;
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
    write(object, "metadata", metadata);

    return object;
  }

  public FeeFineOwnerBuilder withId(UUID id) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId, metadata);
  }
  public FeeFineOwnerBuilder withOwner(String owner) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId, metadata);
  }
  public FeeFineOwnerBuilder withDesc(String desc) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId, metadata);
  }
  public FeeFineOwnerBuilder withServicePointOwner(List<JsonObject> servicePointOwner) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId, metadata);
  }
  public FeeFineOwnerBuilder withDefaultChargeNoticeId(String defaultChargeNoticeId) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId, metadata);
  }
  public FeeFineOwnerBuilder withDefaultActionNoticeId(String defaultActionNoticeId) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId, metadata);
  }
  public FeeFineOwnerBuilder withMetadata(JsonObject metadata) {
    return new FeeFineOwnerBuilder(id, owner, desc, servicePointOwner, defaultChargeNoticeId,
      defaultActionNoticeId, metadata);
  }
}
