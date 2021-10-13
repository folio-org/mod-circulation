package api.support.builders;

import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTimeOptional;

import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Policies;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.utils.ClockUtil;

import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

public class LoanBuilder extends JsonBuilder implements Builder {
  private final static String OPEN_LOAN_STATUS = "Open";
  private final static String CLOSED_LOAN_STATUS = "Closed";

  private final UUID id;
  private final UUID itemId;
  private final UUID userId;
  private final ZonedDateTime loanDate;
  private final String status;
  private ZonedDateTime returnDate;
  private final String action;
  private final ZonedDateTime dueDate;
  private final UUID proxyUserId;
  private final ZonedDateTime systemReturnDate;
  private final UUID checkoutServicePointId;
  private final UUID checkinServicePointId;
  private final Boolean dueDateChangedByRecall;
  private final Policies policies;

  public LoanBuilder() {
    this(UUID.randomUUID(), UUID.randomUUID(), null, ZonedDateTime.of(2017, 3, 6, 16, 4, 43, 0, UTC), null, "Open",
        null, null, "checkedout", null, null, null, null, new Policies(null, null, null));
  }

  private LoanBuilder(UUID id, UUID itemId, UUID userId, ZonedDateTime loanDate,
    ZonedDateTime dueDate, String status, ZonedDateTime returnDate, ZonedDateTime systemReturnDate,
    String action, UUID proxyUserId, UUID checkoutServicePointId,
    UUID checkinServicePointId, Boolean dueDateChangedByRecall, Policies policies) {

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
    this.checkoutServicePointId = checkoutServicePointId;
    this.checkinServicePointId = checkinServicePointId;
    this.dueDateChangedByRecall = dueDateChangedByRecall;
    this.policies = policies;
  }

  public JsonObject create() {
    JsonObject loanRequest = new JsonObject();

    if (id != null) {
      put(loanRequest, "id", id);
    }

    put(loanRequest, "userId", userId);
    put(loanRequest, "itemId", itemId);
    put(loanRequest, "loanDate", loanDate);

    put(loanRequest, "status", status, new JsonObject().put("name", status));

    put(loanRequest, "proxyUserId", proxyUserId);
    put(loanRequest, "action", action);
    put(loanRequest, "dueDate", formatDateTimeOptional(dueDate));
    put(loanRequest, "checkoutServicePointId", checkoutServicePointId);
    put(loanRequest, "checkinServicePointId", checkinServicePointId);
    put(loanRequest, "dueDateChangedByRecall", dueDateChangedByRecall);

    if (Objects.equals(status, CLOSED_LOAN_STATUS)) {
      put(loanRequest, "returnDate", formatDateTimeOptional(returnDate));
      put(loanRequest, "systemReturnDate", formatDateTimeOptional(systemReturnDate));
    }

    return loanRequest;
  }

  public Loan asDomainObject() {
    return Loan.from(create());
  }

  public LoanBuilder withRandomPastLoanDate() {
    Random random = new Random();

    return withLoanDate(ClockUtil.getZonedDateTime().minusDays(random.nextInt(10)));
  }

  public LoanBuilder withLoanDate(ZonedDateTime loanDate) {
    return new LoanBuilder(this.id, this.itemId, this.userId, loanDate, this.dueDate, this.status, this.returnDate,
        this.systemReturnDate, this.action, this.proxyUserId, this.checkoutServicePointId, this.checkinServicePointId,
        this.dueDateChangedByRecall,  this.policies);
  }

  public LoanBuilder withUserId(UUID userId) {
    return new LoanBuilder(this.id, this.itemId, userId, this.loanDate, this.dueDate, this.status, this.returnDate,
        this.systemReturnDate, this.action, this.proxyUserId, this.checkoutServicePointId, this.checkinServicePointId,
        this.dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder withNoUserId() {
    return withUserId(null);
  }

  public LoanBuilder withStatus(String status) {
    ZonedDateTime defaultedReturnDate = this.returnDate != null ? this.returnDate : this.loanDate.plusDays(1).plusHours(4);

    String action = null;

    switch (status) {
    case OPEN_LOAN_STATUS:
      action = "checkedout";
      break;
    case CLOSED_LOAN_STATUS:
      action = "checkedin";
      break;
    }

    return new LoanBuilder(this.id, this.itemId, this.userId, this.loanDate, this.dueDate, status, defaultedReturnDate,
        this.systemReturnDate, action, this.proxyUserId, this.checkoutServicePointId, this.checkinServicePointId,
        this.dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder open() {
    return withStatus(OPEN_LOAN_STATUS);
  }

  public LoanBuilder closed() {
    return withStatus(CLOSED_LOAN_STATUS);
  }

  public LoanBuilder withNoStatus() {
    return new LoanBuilder(this.id, this.itemId, this.userId, this.loanDate, this.dueDate, null, this.returnDate,
        this.systemReturnDate, null, this.proxyUserId, this.checkoutServicePointId, this.checkinServicePointId,
        this.dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder withId(UUID id) {
    return new LoanBuilder(id, this.itemId, this.userId, this.loanDate, this.dueDate, this.status, this.returnDate,
        this.systemReturnDate, this.action, this.proxyUserId, this.checkoutServicePointId, this.checkinServicePointId,
        this.dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder withItemId(UUID itemId) {
    return new LoanBuilder(this.id, itemId, this.userId, this.loanDate, this.dueDate, this.status, this.returnDate,
        this.systemReturnDate, this.action, this.proxyUserId, this.checkoutServicePointId, this.checkinServicePointId,
        this.dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder withReturnDate(ZonedDateTime returnDate) {
    return new LoanBuilder(this.id, this.itemId, this.userId, this.loanDate, this.dueDate, this.status, returnDate,
        this.systemReturnDate, this.action, this.proxyUserId, this.checkoutServicePointId, this.checkinServicePointId,
        this.dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder withSystemReturnDate(ZonedDateTime systemReturnDate) {
    return new LoanBuilder(this.id, this.itemId, this.userId, this.loanDate, this.dueDate, this.status, this.returnDate,
        systemReturnDate, this.action, this.proxyUserId, this.checkoutServicePointId, this.checkinServicePointId,
        this.dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder withItem(IndividualResource item) {
    return new LoanBuilder(this.id, item.getId(), this.userId, this.loanDate, this.dueDate, this.status,
        this.returnDate, this.systemReturnDate, this.action, this.proxyUserId, this.checkoutServicePointId,
        this.checkinServicePointId, this.dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder withDueDate(ZonedDateTime dueDate) {
    return new LoanBuilder(this.id, this.itemId, this.userId, this.loanDate, dueDate, this.status, this.returnDate,
        this.systemReturnDate, this.action, this.proxyUserId, this.checkoutServicePointId, this.checkinServicePointId,
        this.dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder withCheckoutServicePointId(UUID checkoutServicePointID) {
    return new LoanBuilder(this.id, this.itemId, this.userId, this.loanDate, dueDate, this.status, this.returnDate,
        this.systemReturnDate, this.action, this.proxyUserId, checkoutServicePointID, this.checkinServicePointId,
        this.dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder withCheckinServicePointId(UUID checkinServicePointID) {
    return new LoanBuilder(this.id, this.itemId, this.userId, this.loanDate, dueDate, this.status, this.returnDate,
        this.systemReturnDate, this.action, this.proxyUserId, this.checkoutServicePointId, checkinServicePointID,
        this.dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder withProxyUserId(UUID proxyUserId) {
    return new LoanBuilder(this.id, this.itemId, this.userId, this.loanDate, dueDate, this.status, this.returnDate,
        this.systemReturnDate, this.action, proxyUserId, this.checkoutServicePointId, this.checkinServicePointId,
        this.dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder withDueDateChangedByRecall(Boolean dueDateChangedByRecall) {
    return new LoanBuilder(this.id, this.itemId, this.userId, this.loanDate, dueDate, this.status, this.returnDate,
      this.systemReturnDate, this.action, this.proxyUserId, this.checkoutServicePointId, this.checkinServicePointId,
      dueDateChangedByRecall, this.policies);
  }

  public LoanBuilder withLoanPolicy(LoanPolicy newLoanPolicy) {
    return new LoanBuilder(this.id, this.itemId, this.userId, this.loanDate, dueDate, this.status, this.returnDate,
      this.systemReturnDate, this.action, proxyUserId, this.checkoutServicePointId, this.checkinServicePointId,
      this.dueDateChangedByRecall, policies.withLoanPolicy(newLoanPolicy));
  }

  public LoanBuilder dueIn(Period period) {
    if (this.loanDate == null) {
      throw new IllegalStateException("Cannot use period to specify due when no loan date specified");
    }

    ZonedDateTime calculatedDueDate = this.loanDate.plus(period);

    return withDueDate(calculatedDueDate);
  }
}
