package api.support.builders;

import org.folio.circulation.support.JsonPropertyWriter;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;

public class BatchChangeDueDateRequestBuilder extends JsonBuilder implements
  Builder {

  private final List<String> loanIds;
  private final DateTime dueDate;
  private boolean emptyBody;

  public BatchChangeDueDateRequestBuilder() {
    this(new ArrayList<>(), null);
    emptyBody = false;
  }

  public BatchChangeDueDateRequestBuilder(List<String> loanIds,
    DateTime dueDate) {
    this.loanIds = loanIds;
    this.dueDate = dueDate;
  }

  @Override
  public JsonObject create() {
    final JsonObject payload = new JsonObject();

    if (!emptyBody) {
      JsonPropertyWriter.write(payload, "loanIds", new JsonArray(loanIds));
      JsonPropertyWriter.write(payload, "dueDate", dueDate);
    }

    return payload;
  }

  public BatchChangeDueDateRequestBuilder forLoanId(String loanId) {
    return new BatchChangeDueDateRequestBuilder(
      appendToLoanIds(loanId),
      dueDate);
  }

  private List<String> appendToLoanIds(String loanId) {
    loanIds.add(loanId);
    return loanIds;
  }

  public BatchChangeDueDateRequestBuilder withEmptyBody() {
    emptyBody = true;
    return this;
  }

  public BatchChangeDueDateRequestBuilder on(DateTime dueDate) {

    return new BatchChangeDueDateRequestBuilder(loanIds, dueDate);
  }
}
