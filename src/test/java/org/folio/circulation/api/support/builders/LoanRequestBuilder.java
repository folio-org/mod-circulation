package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class LoanRequestBuilder extends JsonRequestBuilder implements Builder {
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
  private final DateTime returnProcessedDate;

  
  public LoanRequestBuilder() {
    this(UUID.randomUUID(), UUID.randomUUID(), APITestSuite.userId(),
      new DateTime(2017, 03, 06, 16, 04, 43), null, "Open", null, null,
      "checkedout", null);
  }

  private LoanRequestBuilder(
    UUID id,
    UUID itemId,
    UUID userId,
    DateTime loanDate,
    DateTime dueDate,
    String status,
    DateTime returnDate,
    DateTime returnProcessedDate,
    String action,
    UUID proxyUserId) {

    this.id = id;
    this.itemId = itemId;
    this.userId = userId;
    this.proxyUserId = proxyUserId;
    this.loanDate = loanDate;
    this.status = status;
    this.returnDate = returnDate;
    this.returnProcessedDate = returnProcessedDate;
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
      put(loanRequest, "returnProcessedDate", returnProcessedDate);
    }

    return loanRequest;
  }

  public LoanRequestBuilder withRandomPastLoanDate() {
    Random random = new Random();

    return withLoanDate(DateTime.now().minusDays(random.nextInt(10)));
  }

  public LoanRequestBuilder withLoanDate(DateTime loanDate) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      loanDate, this.dueDate, this.status, this.returnDate,
      this.returnProcessedDate, this.action,this.proxyUserId);
  }

  public LoanRequestBuilder withUserId(UUID userId) {
    return new LoanRequestBuilder(this.id, this.itemId, userId,
      this.loanDate, this.dueDate, this.status, this.returnDate,
      this.returnProcessedDate, this.action, this.proxyUserId);
  }

  public LoanRequestBuilder withStatus(String status) {
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

    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, this.dueDate, status, defaultedReturnDate,
      this.returnProcessedDate, action, this.proxyUserId);
  }

  public LoanRequestBuilder open() {
    return withStatus(OPEN_LOAN_STATUS);
  }

  public LoanRequestBuilder closed() {
    return withStatus(CLOSED_LOAN_STATUS);
  }

  public LoanRequestBuilder withNoStatus() {
    return new LoanRequestBuilder(
      this.id,
      this.itemId,
      this.userId,
      this.loanDate,
      this.dueDate,
      null,
      this.returnDate,
      this.returnProcessedDate,
      null,
      this.proxyUserId);
  }

  public LoanRequestBuilder withId(UUID id) {
    return new LoanRequestBuilder(id, this.itemId, this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate,
      this.returnProcessedDate, this.action, this.proxyUserId);
  }

  public LoanRequestBuilder withItemId(UUID itemId) {
    return new LoanRequestBuilder(this.id, itemId, this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate,
      this.returnProcessedDate, this.action, this.proxyUserId);
  }

  public LoanRequestBuilder withReturnDate(DateTime returnDate) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, this.dueDate, this.status, returnDate,
      this.returnProcessedDate, this.action, this.proxyUserId);
  }
  
  public LoanRequestBuilder withReturnProcessedDate(DateTime returnProcessedDate) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate,
      returnProcessedDate, this.action, this.proxyUserId);
  }

  public LoanRequestBuilder withItem(IndividualResource item) {
    return new LoanRequestBuilder(this.id, item.getId(), this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate,
      this.returnProcessedDate, this.action, this.proxyUserId);
  }

  public LoanRequestBuilder withDueDate(DateTime dueDate) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, dueDate, this.status, this.returnDate, 
      this.returnProcessedDate, this.action, this.proxyUserId);
  }

  public LoanRequestBuilder withProxyUserId(UUID proxyUserId) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, dueDate, this.status, this.returnDate, 
      this.returnProcessedDate, this.action, proxyUserId);
  }

  public LoanRequestBuilder dueIn(Period period) {
    if(this.loanDate == null) {
      throw new IllegalStateException(
        "Cannot use period to specify due when no loan date specified");
    }

    DateTime calculatedDueDate = this.loanDate.plus(period);

    return withDueDate(calculatedDueDate);
  }
}
