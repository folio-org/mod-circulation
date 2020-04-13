package api.support.builders;

import static org.folio.circulation.resources.DeclareItemClaimedReturnedAsMissingResource.COMMENT;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;

public class DeclareItemClaimedReturnedAsMissingRequestBuilder implements Builder {
  private final String comment;
  private final String loanId;

  public DeclareItemClaimedReturnedAsMissingRequestBuilder() {
    this(null, null);
  }

  private DeclareItemClaimedReturnedAsMissingRequestBuilder(
    String loanId,  String comment) {

    this.comment = comment;
    this.loanId = loanId;
  }

  public DeclareItemClaimedReturnedAsMissingRequestBuilder withComment(String comment) {
    return new DeclareItemClaimedReturnedAsMissingRequestBuilder(this.loanId, comment);
  }

  public DeclareItemClaimedReturnedAsMissingRequestBuilder forLoan(String loanId) {
    return new DeclareItemClaimedReturnedAsMissingRequestBuilder(loanId, this.comment);
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
