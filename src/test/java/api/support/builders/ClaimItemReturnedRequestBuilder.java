package api.support.builders;

import static org.folio.circulation.resources.ClaimItemReturnedResource.COMMENT;
import static org.folio.circulation.resources.ClaimItemReturnedResource.ITEM_CLAIMED_RETURNED_DATE;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class ClaimItemReturnedRequestBuilder implements Builder {
  private final DateTime itemClaimedReturnedDate;
  private final String comment;
  private final String loanId;

  public ClaimItemReturnedRequestBuilder() {
    this(null, DateTime.now(DateTimeZone.UTC), null);
  }

  private ClaimItemReturnedRequestBuilder(
    String loanId, DateTime itemClaimedReturnedDate, String comment) {

    this.itemClaimedReturnedDate = itemClaimedReturnedDate;
    this.comment = comment;
    this.loanId = loanId;
  }

  public ClaimItemReturnedRequestBuilder withItemClaimedReturnedDate(DateTime dateTime) {
    return new ClaimItemReturnedRequestBuilder(this.loanId, dateTime, this.comment);
  }

  public ClaimItemReturnedRequestBuilder withComment(String comment) {
    return new ClaimItemReturnedRequestBuilder(this.loanId, this.itemClaimedReturnedDate, comment);
  }

  public ClaimItemReturnedRequestBuilder forLoan(String loanId) {
    return new ClaimItemReturnedRequestBuilder(loanId, this.itemClaimedReturnedDate, this.comment);
  }

  public String getLoanId() {
    return loanId;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    if (itemClaimedReturnedDate != null) {
      request.put(ITEM_CLAIMED_RETURNED_DATE, itemClaimedReturnedDate.toString());
    }
    write(request, COMMENT, comment);

    return request;
  }
}
