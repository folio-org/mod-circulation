package api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;

public class RenewByIdRequestBuilder extends JsonBuilder implements Builder {
  private final String itemId;
  private final String userId;

  public RenewByIdRequestBuilder() {
    this(null, null);
  }

  private RenewByIdRequestBuilder(
    String itemId,
    String userId) {

    this.itemId = itemId;
    this.userId = userId;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemId", this.itemId);
    put(request, "userId", this.userId);

    return request;
  }

  public RenewByIdRequestBuilder forItem(IndividualResource item) {
    return new RenewByIdRequestBuilder(
      getId(item),
      this.userId);
  }

  public RenewByIdRequestBuilder forUser(IndividualResource loanee) {
    return new RenewByIdRequestBuilder(
      this.itemId,
      getId(loanee));
  }

  private String getId(IndividualResource record) {
    return record.getJson().getString("id");
  }
}
