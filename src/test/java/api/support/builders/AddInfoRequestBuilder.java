package api.support.builders;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

public class AddInfoRequestBuilder implements Builder {
  private final String action;
  private final String actionComment;
  private final String loanId;

  public AddInfoRequestBuilder(
    String loanId, String addInfoAction, String actionComment ) {
    this.action = addInfoAction;
    this.actionComment = actionComment;
    this.loanId = loanId;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();
    write(request, "action", action);
    write(request, "actionComment", actionComment);
    return request;
  }

  public String getLoanId() {
    return loanId;
  }

}
