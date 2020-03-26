package api.support.builders;

import static org.folio.circulation.resources.MarkItemMissingResource.COMMENT;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;

public class MarkItemMissingRequestBuilder implements Builder {
  private final String comment;
  private final String loanId;

  public MarkItemMissingRequestBuilder() {
    this(null, null);
  }

  private MarkItemMissingRequestBuilder(
    String loanId,  String comment) {

    this.comment = comment;
    this.loanId = loanId;
  }

  public MarkItemMissingRequestBuilder withComment(String comment) {
    return new MarkItemMissingRequestBuilder(this.loanId, comment);
  }

  public MarkItemMissingRequestBuilder forLoan(String loanId) {
    return new MarkItemMissingRequestBuilder(loanId, this.comment);
  }

  public String getLoanId() {
    return loanId;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    write(request, COMMENT, comment);

    return request;
  }
}
