package api.support.builders;

import io.vertx.core.json.JsonObject;
import api.APITestSuite;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class LoanBuilder extends JsonBuilder implements Builder {
  private final static String OPEN_LOAN_STATUS = "Open";
  private final static String CLOSED_LOAN_STATUS = "Closed";

  private final UUID id;
  private final UUID itemId;
  private final UUID userId;
  private final DateTime loanDate;
  private final String status;
  private DateTime returnDate;
  private final String action;
  private final DateTime dueDate;
  private final UUID proxyUserId;
  private final DateTime systemReturnDate;


  public LoanBuilder() {
    this(UUID.randomUUID(), UUID.randomUUID(), APITestSuite.userId(),
      new DateTime(2017, 03, 06, 16, 04, 43), null, "Open", null, null,
      "checkedout", null);
  }

  private LoanBuilder(
    UUID id,
    UUID itemId,
    UUID userId,
    DateTime loanDate,
    DateTime dueDate,
    String status,
    DateTime returnDate,
    DateTime systemReturnDate,
    String action,
    UUID proxyUserId) {

    this.id = id;
    this.itemId = itemId;
    this.userId = userId;
    this.proxyUserId = proxyUserId;
    this.loanDate = loanDate;
    this.status = status;
    this.returnDate = returnDate;
    this.systemReturnDate = systemReturnDate;
    this.action = action;
    this.dueDate = dueDate;
  }

  public JsonObject create() {

    JsonObject loanRequest = new JsonObject();

    if(id != null) {
      put(loanRequest, "id", id);
    }

    put(loanRequest, "userId", userId);
    put(loanRequest, "itemId", itemId);
    put(loanRequest, "loanDate", loanDate);

    put(loanRequest, "status", status, new JsonObject().put("name", status));

    put(loanRequest, "proxyUserId", proxyUserId);
    put(loanRequest, "action", action);
    put(loanRequest, "dueDate", dueDate);

    if(Objects.equals(status, CLOSED_LOAN_STATUS)) {
      put(loanRequest, "returnDate", returnDate);
      put(loanRequest, "systemReturnDate", systemReturnDate);
    }

    return loanRequest;
  }

  public LoanBuilder withRandomPastLoanDate() {
    Random random = new Random();

    return withLoanDate(DateTime.now().minusDays(random.nextInt(10)));
  }

  public LoanBuilder withLoanDate(DateTime loanDate) {
    return new LoanBuilder(this.id, this.itemId, this.userId,
      loanDate, this.dueDate, this.status, this.returnDate,
      this.systemReturnDate, this.action,this.proxyUserId);
  }

  public LoanBuilder withUserId(UUID userId) {
    return new LoanBuilder(this.id, this.itemId, userId,
      this.loanDate, this.dueDate, this.status, this.returnDate,
      this.systemReturnDate, this.action, this.proxyUserId);
  }

  public LoanBuilder withStatus(String status) {
    DateTime defaultedReturnDate = this.returnDate != null
      ? this.returnDate
      : this.loanDate.plusDays(1).plusHours(4);

    String action = null;

    switch(status) {
      case OPEN_LOAN_STATUS:
        action = "checkedout";
        break;
      case CLOSED_LOAN_STATUS:
        action = "checkedin";
        break;
    }

    return new LoanBuilder(this.id, this.itemId, this.userId,
      this.loanDate, this.dueDate, status, defaultedReturnDate,
      this.systemReturnDate, action, this.proxyUserId);
  }

  public LoanBuilder open() {
    return withStatus(OPEN_LOAN_STATUS);
  }

  public LoanBuilder closed() {
    return withStatus(CLOSED_LOAN_STATUS);
  }

  public LoanBuilder withNoStatus() {
    return new LoanBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.loanDate,
      this.dueDate,
      null,
      this.returnDate,
      this.systemReturnDate,
      null,
      this.proxyUserId);
  }

  public LoanBuilder withId(UUID id) {
    return new LoanBuilder(id, this.itemId, this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate,
      this.systemReturnDate, this.action, this.proxyUserId);
  }

  public LoanBuilder withItemId(UUID itemId) {
    return new LoanBuilder(this.id, itemId, this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate,
      this.systemReturnDate, this.action, this.proxyUserId);
  }

  public LoanBuilder withReturnDate(DateTime returnDate) {
    return new LoanBuilder(this.id, this.itemId, this.userId,
      this.loanDate, this.dueDate, this.status, returnDate,
      this.systemReturnDate, this.action, this.proxyUserId);
  }

  public LoanBuilder withSystemReturnDate(DateTime systemReturnDate) {
    return new LoanBuilder(this.id, this.itemId, this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate,
      systemReturnDate, this.action, this.proxyUserId);
  }

  public LoanBuilder withItem(IndividualResource item) {
    return new LoanBuilder(this.id, item.getId(), this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate,
      this.systemReturnDate, this.action, this.proxyUserId);
  }

  public LoanBuilder withDueDate(DateTime dueDate) {
    return new LoanBuilder(this.id, this.itemId, this.userId,
      this.loanDate, dueDate, this.status, this.returnDate,
      this.systemReturnDate, this.action, this.proxyUserId);
  }

  public LoanBuilder withProxyUserId(UUID proxyUserId) {
    return new LoanBuilder(this.id, this.itemId, this.userId,
      this.loanDate, dueDate, this.status, this.returnDate,
      this.systemReturnDate, this.action, proxyUserId);
  }

  public LoanBuilder dueIn(Period period) {
    if(this.loanDate == null) {
      throw new IllegalStateException(
        "Cannot use period to specify due when no loan date specified");
    }

    DateTime calculatedDueDate = this.loanDate.plus(period);

    return withDueDate(calculatedDueDate);
  }
}
