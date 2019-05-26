package api.support.builders;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.Item;
import org.folio.circulation.support.http.client.IndividualResource;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class HoldingBuilder extends JsonBuilder implements Builder {
  private final UUID instanceId;
  private final UUID permanentLocationId;
  private final UUID temporaryLocationId;
  private final String callNumber;
  private final List<Item> holdingsItems;

  public HoldingBuilder() {
    this(null, null, null, null, null);
  }

  private HoldingBuilder(
    UUID instanceId,
    UUID permanentLocationId,
    UUID temporaryLocationId,
    String callNumber,
    List<Item> holdingsItems) {

    this.instanceId = instanceId;
    this.permanentLocationId = permanentLocationId;
    this.temporaryLocationId = temporaryLocationId;
    this.callNumber = callNumber;
    this.holdingsItems = holdingsItems;
  }

  @Override
  public JsonObject create() {
    final JsonObject holdings = new JsonObject();

    put(holdings, "instanceId", instanceId);
    put(holdings, "permanentLocationId", permanentLocationId);
    put(holdings, "temporaryLocationId", temporaryLocationId);
    put(holdings, "callNumber", callNumber);
    put(holdings, "holdingsItems", toJsonHoldingItems(holdingsItems));

    return holdings;
  }

  public HoldingBuilder forInstance(UUID instanceId) {
    return new HoldingBuilder(
      instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.callNumber,
      this.holdingsItems);
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
      this.holdingsItems);
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
      this.holdingsItems);
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
      this.holdingsItems
    );
  }

  public HoldingBuilder withHoldingsItems(List<Item> holdingsItems) {
    return new HoldingBuilder(
      this.instanceId,
      this.permanentLocationId,
      this.temporaryLocationId,
      this.callNumber,
      holdingsItems
    );
  }

  private JsonArray toJsonHoldingItems(List<Item> holdingsItems) {
    JsonArray holdingsItemJsonArr = new JsonArray();
    for( Item item : holdingsItems) {
      holdingsItemJsonArr.add(item.getItem());
    }
    return holdingsItemJsonArr;
  }
}
