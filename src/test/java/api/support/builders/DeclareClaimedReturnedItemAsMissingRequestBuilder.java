package api.support.builders;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;

public class DeclareClaimedReturnedItemAsMissingRequestBuilder implements Builder {
  private final String comment;
  private final String loanId;

  public DeclareClaimedReturnedItemAsMissingRequestBuilder() {
    this(null, null);
  }

  private DeclareClaimedReturnedItemAsMissingRequestBuilder(
    String loanId,  String comment) {

    this.comment = comment;
    this.loanId = loanId;
  }

  public DeclareClaimedReturnedItemAsMissingRequestBuilder withComment(String comment) {
    return new DeclareClaimedReturnedItemAsMissingRequestBuilder(this.loanId, comment);
  }

  public DeclareClaimedReturnedItemAsMissingRequestBuilder forLoan(String loanId) {
    return new DeclareClaimedReturnedItemAsMissingRequestBuilder(loanId, this.comment);
  }

  public String getLoanId() {
    return loanId;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    write(request, "comment", comment);

    return request;
  }
}
