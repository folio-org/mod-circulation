package api.support.builders;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;

import io.vertx.core.json.JsonObject;

public class HoldingBuilder extends JsonBuilder implements Builder {
  private final UUID instanceId;
  private final UUID permanentLocationId;
  private final UUID temporaryLocationId;
  private final String callNumber;
  private final String callNumberPrefix;
  private final String callNumberSuffix;
  private final String copyNumber;

  public HoldingBuilder() {
    this(null, null, null, null, null, null, null);
  }

  private HoldingBuilder(
    UUID instanceId,
    UUID permanentLocationId,
    UUID temporaryLocationId,
    String callNumber,
    String callNumberPrefix,
    String callNumberSuffix,
    String copyNumber) {

    this.instanceId = instanceId;
    this.permanentLocationId = permanentLocationId;
    this.temporaryLocationId = temporaryLocationId;
    this.callNumber = callNumber;
    this.callNumberPrefix = callNumberPrefix;
    this.callNumberSuffix = callNumberSuffix;
    this.copyNumber = copyNumber;
  }

  @Override
  public JsonObject create() {
    final JsonObject holdings = new JsonObject();

    put(holdings, "instanceId", instanceId);
    put(holdings, "permanentLocationId", permanentLocationId);
    put(holdings, "temporaryLocationId", temporaryLocationId);
    put(holdings, "callNumber", callNumber);
    put(holdings, "callNumberPrefix", callNumberPrefix);
    put(holdings, "callNumberSuffix", callNumberSuffix);
    put(holdings, "copyNumber", copyNumber);

    return holdings;
  }

  public HoldingBuilder forInstance(UUID instanceId) {
    return new HoldingBuilder(
      instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.copyNumber
    );
  }

  public HoldingBuilder withPermanentLocation(IndividualResource location) {
    return withPermanentLocation(location.getId());
  }

  public HoldingBuilder withPermanentLocation(UUID locationId) {
    return new HoldingBuilder(
      this.instanceId,
      locationId,
      this.temporaryLocationId,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.copyNumber
    );
  }

  public HoldingBuilder withNoPermanentLocation() {
    return withPermanentLocation((UUID)null);
  }

  public HoldingBuilder withTemporaryLocation(IndividualResource location) {
    return withTemporaryLocation(location.getId());
  }

  public HoldingBuilder withTemporaryLocation(UUID locationId) {
    return new HoldingBuilder(
      this.instanceId,
      this.permanentLocationId,
      locationId,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.copyNumber
    );
  }

  public HoldingBuilder withNoTemporaryLocation() {
    return withTemporaryLocation((UUID)null);
  }

  public HoldingBuilder withCallNumber(String callNumber) {
    return new HoldingBuilder(
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      this.copyNumber
    );
  }

  public HoldingBuilder withCallNumberPrefix(String callNumberPrefix) {
    return new HoldingBuilder(
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.callNumber,
      callNumberPrefix,
      this.callNumberSuffix,
      this.copyNumber
    );
  }

  public HoldingBuilder withCallNumberSuffix(String callNumberSuffix) {
    return new HoldingBuilder(
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.callNumber,
      this.callNumberPrefix,
      callNumberSuffix,
      this.copyNumber
    );
  }

  public HoldingBuilder withCopyNumbers(List<String> copyNumbers) {
    return new HoldingBuilder(
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.callNumber,
      this.callNumberPrefix,
      this.callNumberSuffix,
      String.join("; ", copyNumbers)
    );
  }
}
