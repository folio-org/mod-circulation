package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;

import java.time.ZonedDateTime;
import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class ClaimItemReturnedRequestBuilder implements Builder {
  private final ZonedDateTime itemClaimedReturnedDate;
  private final String comment;
  private final String loanId;

  public ClaimItemReturnedRequestBuilder() {
    this(null, getZonedDateTime(), null);
  }

  private ClaimItemReturnedRequestBuilder(
    String loanId, ZonedDateTime itemClaimedReturnedDate, String comment) {

    this.itemClaimedReturnedDate = itemClaimedReturnedDate;
    this.comment = comment;
    this.loanId = loanId;
  }

  public ClaimItemReturnedRequestBuilder withItemClaimedReturnedDate(ZonedDateTime dateTime) {
    return new ClaimItemReturnedRequestBuilder(this.loanId, dateTime, this.comment);
  }

  public ClaimItemReturnedRequestBuilder withComment(String comment) {
    return new ClaimItemReturnedRequestBuilder(this.loanId, this.itemClaimedReturnedDate, comment);
  }

  public ClaimItemReturnedRequestBuilder forLoan(String loanId) {
    return new ClaimItemReturnedRequestBuilder(loanId, this.itemClaimedReturnedDate, this.comment);
  }

  public ClaimItemReturnedRequestBuilder forLoan(UUID loanId) {
    return new ClaimItemReturnedRequestBuilder(loanId.toString(),
      this.itemClaimedReturnedDate, this.comment);
  }

  public String getLoanId() {
    return loanId;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    if (itemClaimedReturnedDate != null) {
      request.put("itemClaimedReturnedDateTime", formatDateTime(itemClaimedReturnedDate));
    }
    write(request, "comment", comment);

    return request;
  }
}
