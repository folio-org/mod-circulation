package api.support.builders;

import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ID;

import io.vertx.core.json.JsonObject;

public class EndSessionBuilder extends JsonBuilder implements Builder {

  private final String patronId;
  private final String actionType;

  public EndSessionBuilder() {
    this(null, null);
  }

  EndSessionBuilder(String patronId, String actionType) {
    this.patronId = patronId;
    this.actionType = actionType;
  }

  @Override
  public JsonObject create() {
    JsonObject jsonObject = new JsonObject();

    if (this.patronId == null && this.actionType == null) {
      return jsonObject;
    }

    return jsonObject
      .put(PATRON_ID, this.patronId)
      .put(ACTION_TYPE, this.actionType);
  }

  public EndSessionBuilder withPatronId(String patronId) {
    return new EndSessionBuilder(
      patronId,
      this.actionType);
  }

  public EndSessionBuilder withActionType(String actionType) {
    return new EndSessionBuilder(
      this.patronId,
      actionType);
  }
}
