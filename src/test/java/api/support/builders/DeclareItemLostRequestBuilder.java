package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.UUID;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

public class DeclareItemLostRequestBuilder extends JsonBuilder implements Builder {
  private final UUID loanId;
  private final DateTime dateTime;
  private final String comment;

  public DeclareItemLostRequestBuilder() {
    this(null, DateTime.now(), null);
  }

  public UUID getLoanId() {
    return loanId;
  }

  public DateTime getDateTime() {
    return dateTime;
  }

  public String getComment() {
    return comment;
  }

  public DeclareItemLostRequestBuilder(UUID loanId, DateTime dateTime,
    String comment) {
    this.loanId = loanId;
    this.dateTime = dateTime;
    this.comment = comment;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    write(request, "declaredLostDateTime", this.dateTime);
    write(request, "comment", this.comment);

    return request;
  }

   public DeclareItemLostRequestBuilder forLoanId(UUID id) {
    return new DeclareItemLostRequestBuilder(id, dateTime, comment);
  }

  public DeclareItemLostRequestBuilder on(DateTime dateTime) {
    return new DeclareItemLostRequestBuilder(loanId, dateTime, comment);
  }

  public DeclareItemLostRequestBuilder withComment(String comment) {
    return new DeclareItemLostRequestBuilder(loanId, dateTime, comment);
  }

  public DeclareItemLostRequestBuilder withNoComment() {
    return new DeclareItemLostRequestBuilder(loanId, dateTime, "");
  }
}
