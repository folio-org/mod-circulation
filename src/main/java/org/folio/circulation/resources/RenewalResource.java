package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.CalendarRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRenewalService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils;
import org.folio.circulation.domain.representations.LoanResponse;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.PeriodUtil;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class RenewalResource extends Resource {
  private final String rootPath;

  RenewalResource(HttpClient client, String rootPath) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      rootPath, router);

    routeRegistration.create(this::renew);
  }

  private void renew(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true);
    final UserRepository userRepository = new UserRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final CalendarRepository calendarRepository = new CalendarRepository(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final LoanRenewalService loanRenewalService = LoanRenewalService.using(clients);

    //TODO: Validation check for same user should be in the domain service

    findLoan(routingContext.getBodyAsJson(), loanRepository, itemRepository, userRepository)
      .thenApply(r -> r.map(LoanAndRelatedRecords::new))
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenApply(r -> r.next(loanRenewalService::renew))
      .thenComposeAsync(r -> r.after(calendarRepository::lookupPeriod))
      .thenApply(r -> r.next(this::applyCLDDM))
      .thenComposeAsync(r -> r.after(calendarRepository::lookupPeriodForFixedDueDateSchedule))
      .thenApply(r -> r.next(this::applyFixedDueDateLimit))
      .thenComposeAsync(r -> r.after(loanRepository::updateLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(LoanResponse::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private HttpResult<LoanAndRelatedRecords> applyCLDDM(LoanAndRelatedRecords relatedRecords) {
    if (relatedRecords.getInitialDueDateDays() == null) {
      return HttpResult.succeeded(relatedRecords);
    }
    //TODO replace with configured timezone
    ClosedLibraryStrategy strategy =
      ClosedLibraryStrategyUtils.determineClosedLibraryStrategy(
        relatedRecords.getLoanPolicy(), DateTime.now(), DateTimeZone.UTC);

    DateTime dueDate = relatedRecords.getLoan().getDueDate();
    DateTime calculateDueDate =
      strategy.calculateDueDate(dueDate, relatedRecords.getInitialDueDateDays());

    relatedRecords.getLoan().changeDueDate(calculateDueDate);
    return HttpResult.succeeded(relatedRecords);
  }

  private HttpResult<LoanAndRelatedRecords> applyFixedDueDateLimit(LoanAndRelatedRecords relatedRecords) {
    if (relatedRecords.getFixedDueDateDays() == null) {
      return HttpResult.succeeded(relatedRecords);
    }
    final Loan loan = relatedRecords.getLoan();
    final LoanPolicy loanPolicy = relatedRecords.getLoanPolicy();
    final DateTime dueDate = relatedRecords.getLoan().getDueDate();

    Optional<DateTime> optionalDueDateLimit = loanPolicy.getFixedDueDateSchedules()
      .findDueDateFor(loan.getLoanDate());
    if (!optionalDueDateLimit.isPresent()) {
      return HttpResult.succeeded(relatedRecords);
    }
    DateTime dueDateLimit = optionalDueDateLimit.get();
    if (!PeriodUtil.isAfterDate(loan.getDueDate(), dueDateLimit)) {
      return HttpResult.succeeded(relatedRecords);
    }
    //TODO replace with configured timezone
    ClosedLibraryStrategy strategy =
      ClosedLibraryStrategyUtils.determineStrategyForMovingBackward(
        loanPolicy, DateTime.now(), DateTimeZone.UTC);
    DateTime calculatedDate =
      strategy.calculateDueDate(dueDate, relatedRecords.getFixedDueDateDays());
    relatedRecords.getLoan().changeDueDate(calculatedDate);
    return HttpResult.succeeded(relatedRecords);
  }

  protected abstract CompletableFuture<HttpResult<Loan>> findLoan(
    JsonObject request,
    LoanRepository loanRepository,
    ItemRepository itemRepository,
    UserRepository userRepository);
}
