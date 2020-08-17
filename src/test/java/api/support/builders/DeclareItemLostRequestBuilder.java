package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.UUID;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class DeclareItemLostRequestBuilder extends JsonBuilder implements Builder {
  private final String loanId;
  private final DateTime dateTime;
  private final String comment;
  private final String servicePointId;

  public DeclareItemLostRequestBuilder() {
    this(null, DateTime.now(DateTimeZone.UTC), null, null);
  }

  public String getLoanId() {
    return loanId;
  }

  public DateTime getDateTime() {
    return dateTime;
  }

  public String getComment() {
    return comment;
  }

  public DeclareItemLostRequestBuilder(String loanId, DateTime dateTime,
    String comment, String servicePointId) {

    this.loanId = loanId;
    this.dateTime = dateTime;
    this.comment = comment;
    this.servicePointId = servicePointId;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    write(request, "declaredLostDateTime", this.dateTime);
    write(request, "comment", this.comment);
    write(request, "servicePointId", this.servicePointId);

    return request;
  }

   public DeclareItemLostRequestBuilder forLoanId(UUID id) {
    return new DeclareItemLostRequestBuilder(id.toString(), dateTime, comment, servicePointId);
  }

  public DeclareItemLostRequestBuilder on(DateTime dateTime) {
    return new DeclareItemLostRequestBuilder(loanId, dateTime, comment, servicePointId);
  }

  public DeclareItemLostRequestBuilder withComment(String comment) {
    return new DeclareItemLostRequestBuilder(loanId, dateTime, comment, servicePointId);
  }

  public DeclareItemLostRequestBuilder withNoComment() {
    return new DeclareItemLostRequestBuilder(loanId, dateTime, "", servicePointId);
  }

  public DeclareItemLostRequestBuilder withServicePointId(UUID servicePointId) {
    return new DeclareItemLostRequestBuilder(loanId, dateTime, comment, servicePointId.toString());
  }
}
