package api.support.builders;

import static org.folio.circulation.resources.ResolveClaimAsMissingResource.COMMENT;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;

public class ResolveClaimAsMissingRequestBuilder implements Builder {
  private final String comment;
  private final String loanId;

  public ResolveClaimAsMissingRequestBuilder() {
    this(null, null);
  }

  private ResolveClaimAsMissingRequestBuilder(
    String loanId,  String comment) {

    this.comment = comment;
    this.loanId = loanId;
  }

  public ResolveClaimAsMissingRequestBuilder withComment(String comment) {
    return new ResolveClaimAsMissingRequestBuilder(this.loanId, comment);
  }

  public ResolveClaimAsMissingRequestBuilder forLoan(String loanId) {
    return new ResolveClaimAsMissingRequestBuilder(loanId, this.comment);
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
