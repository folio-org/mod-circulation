package org.folio.circulation.resources.renewal;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.policy.Period.days;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.RENEWAL_ITEM_IS_NOT_LOANABLE;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.CirculationErrorType;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.logging.RenewalPerformanceLogger;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class RegularRenewalTest {

  private static final String ITEMS_CANNOT_BE_RENEWED_WHEN_THERE_IS_AN_ACTIVE_RECALL_REQUEST =
    "items cannot be renewed when there is an active recall request";
  private static final String ITEM_IS_NOT_LOANABLE = "item is not loanable";
  private static final String ITEM_IS_AGED_TO_LOST = "item is Aged to lost";
  private static final String LOAN_IS_NOT_RENEWABLE = "loan is not renewable";
  private static final String ITEMS_CANNOT_BE_RENEWED_ACTIVE_PENDING_HOLD_REQUEST =
    "Items with this loan policy cannot be renewed when there is an active, pending hold request";
  private static final String ALTERNATIVE_RENEWAL_PERIOD_FOR_HOLDS_IS_SPECIFIED =
    "Item's loan policy has fixed profile but alternative renewal period for holds is specified";
  private static final String POLICY_HAS_FIXED_PROFILE_BUT_RENEWAL_PERIOD_IS_SPECIFIED =
    "Item's loan policy has fixed profile but renewal period is specified";
  private static final String LOAN_AT_MAXIMUM_RENEWAL_NUMBER = "loan at maximum renewal number";
  private static final String CANNOT_DETERMINE_WHEN_TO_RENEW_FROM =
    "cannot determine when to renew from";
  private static final String RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE =
    "renewal would not change the due date";

  private static final UUID ITEM_ID = UUID.randomUUID();

  private RenewalResourceTestAppender appender;
  private org.apache.logging.log4j.core.Logger renewalResourceLogger;

  @BeforeEach
  void setUp() {
    appender = new RenewalResourceTestAppender();
    appender.start();

    renewalResourceLogger = (org.apache.logging.log4j.core.Logger) LogManager
      .getLogger(RenewalResource.class);
    renewalResourceLogger.addAppender(appender);
    renewalResourceLogger.setLevel(Level.INFO);
  }

  @AfterEach
  void tearDown() {
    if (renewalResourceLogger != null && appender != null) {
      renewalResourceLogger.removeAppender(appender);
    }

    if (appender != null) {
      appender.stop();
    }
  }

  @Test
  void canRenewLoan() {
    final var rollingPeriod = days(10);
    final var currentDueDate = getZonedDateTime();
    final var expectedDueDate = rollingPeriod.plusDate(currentDueDate);

    final var loanPolicy = new LoanPolicyBuilder().rolling(rollingPeriod)
      .renewFromCurrentDueDate().asDomainObject();
    final var loan = new LoanBuilder()
      .withCheckoutServicePointId(UUID.randomUUID())
      .asDomainObject().changeDueDate(currentDueDate)
      .withLoanPolicy(loanPolicy);

    final var resultCompletableFuture = renew(loan, new OverridingErrorHandler(null));

    assertThat(resultCompletableFuture.succeeded(), is(true));
    assertThat(resultCompletableFuture.value().getDueDate(), is(expectedDueDate));
  }

  @Test
  void cannotRenewWhenRecallRequestedAndPolicyNorLoanableAndItemLost() {
    final var recallRequest = new RequestBuilder().recall().withItemId(ITEM_ID).asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder()
      .withLoanable(false).withRenewable(false).asDomainObject();
    final var loan = new LoanBuilder().withItemId(ITEM_ID).asDomainObject()
      .changeItemStatusForItemAndLoan(AGED_TO_LOST)
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, recallRequest, errorHandler);

    assertEquals(3, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler,
      ITEMS_CANNOT_BE_RENEWED_WHEN_THERE_IS_AN_ACTIVE_RECALL_REQUEST));
    assertTrue(matchErrorReason(errorHandler, ITEM_IS_NOT_LOANABLE));
    assertTrue(matchErrorReason(errorHandler, ITEM_IS_AGED_TO_LOST));
  }

  @Test
  void cannotRenewWhenRecallRequested() {
    final var recallRequest = new RequestBuilder().recall().withItemId(ITEM_ID).asDomainObject();
    final var loan = new LoanBuilder().withItemId(ITEM_ID).asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, recallRequest, errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      ITEMS_CANNOT_BE_RENEWED_WHEN_THERE_IS_AN_ACTIVE_RECALL_REQUEST));
  }

  @Test
  void cannotRenewWhenItemIsNotLoanable() {
    final var loanPolicy = new LoanPolicyBuilder().withLoanable(false).asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertEquals(1, errorHandler.getErrors().size());
    assertTrue(matchErrorType(errorHandler, RENEWAL_ITEM_IS_NOT_LOANABLE));
    assertTrue(matchErrorReason(errorHandler, ITEM_IS_NOT_LOANABLE));
  }

  @Test
  void cannotRenewWhenLoanIsNotRenewable() {
    final var loanPolicy = new LoanPolicyBuilder().withRenewable(false).asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertTrue(matchErrorReason(errorHandler, LOAN_IS_NOT_RENEWABLE));
  }

  @Test
  void cannotRenewWhenHoldRequestIsNotRenewable() {
    final var request = new RequestBuilder().hold().withItemId(ITEM_ID).asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder()
      .withHolds(null, false, null)
      .asDomainObject();

    final var loan = new LoanBuilder()
      .withItemId(ITEM_ID)
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, request, errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      ITEMS_CANNOT_BE_RENEWED_ACTIVE_PENDING_HOLD_REQUEST));
  }

  @Test
  void cannotRenewWhenHoldRequestedAndFixedPolicyHasAlternativeRenewPeriod() {
    final var request = new RequestBuilder().hold().withItemId(ITEM_ID).asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withHolds(null, true, days(1))
      .asDomainObject();

    final var loan = new LoanBuilder()
      .withItemId(ITEM_ID)
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, request, errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      ALTERNATIVE_RENEWAL_PERIOD_FOR_HOLDS_IS_SPECIFIED));
  }

  @Test
  void cannotRenewWhenHoldRequestedAndFixedPolicyHasRenewPeriod() {
    final var request = new RequestBuilder().hold().withItemId(ITEM_ID).asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .renewWith(days(10))
      .withHolds(null, true, null)
      .asDomainObject();

    final var loan = new LoanBuilder()
      .withItemId(ITEM_ID)
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, request, errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      POLICY_HAS_FIXED_PROFILE_BUT_RENEWAL_PERIOD_IS_SPECIFIED));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "Declared lost",
    "Aged to lost",
    "Claimed returned",
  })
  void cannotRenewItemsWithDisallowedStatuses(String itemStatus) {
    final var loanPolicy = new LoanPolicyBuilder().asDomainObject();
    final var loan = new LoanBuilder().asDomainObject()
      .withLoanPolicy(loanPolicy)
      .changeItemStatusForItemAndLoan(ItemStatus.from(itemStatus));

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, errorHandler);

    assertTrue(matchErrorReason(errorHandler, "item is " + itemStatus));
  }

  @Test
  void cannotRenewLoanThatReachedRenewalLimit() {
    final var renewalLimit = 2;
    final var loanPolicy = new LoanPolicyBuilder()
      .withRenewable(true).limitedRenewals(renewalLimit).asDomainObject();
    final var loan = Loan.from(new JsonObject().put("renewalCount", renewalLimit + 1))
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, errorHandler);

    assertTrue(matchErrorReason(errorHandler, LOAN_AT_MAXIMUM_RENEWAL_NUMBER));
  }

  @Test
  void cannotRenewWhenDueDateCannotBeCalculated() {
    final var loanPolicy = new LoanPolicyBuilder().rolling(days(10))
      .withRenewFrom("INVALID_RENEW_FROM").asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertTrue(matchErrorReason(errorHandler, CANNOT_DETERMINE_WHEN_TO_RENEW_FROM));
  }

  @Test
  void cannotRenewWhenPolicyDueDateIsEarlierThanCurrentDueDate() {
    final var rollingPeriod = days(11);
    final var loanPolicy = new LoanPolicyBuilder()
      .rolling(rollingPeriod)
      .renewFromSystemDate()
      .asDomainObject();

    final var loan = new LoanBuilder().asDomainObject()
      .changeDueDate(getZonedDateTime().plusMinutes(rollingPeriod.toMinutes() * 2))
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, errorHandler);

    assertTrue(matchErrorReason(errorHandler, RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
  }

  @Test
  void shouldNotAttemptToCalculateDueDateWhenPolicyIsNotLoanable() {
    final var loanPolicy = spy(new LoanPolicyBuilder()
      .withLoanable(false).asDomainObject());

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertEquals(1, errorHandler.getErrors().size());
    assertTrue(matchErrorType(errorHandler, RENEWAL_ITEM_IS_NOT_LOANABLE));
    assertTrue(matchErrorReason(errorHandler, ITEM_IS_NOT_LOANABLE));
  }

  @Test
  void shouldNotAttemptToCalculateDueDateWhenPolicyIsNotRenewable() {
    final var loanPolicy = spy(new LoanPolicyBuilder()
      .rolling(days(1)).notRenewable().asDomainObject());

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertTrue(matchErrorReason(errorHandler, LOAN_IS_NOT_RENEWABLE));
  }

  @Test
  void renewByBarcodeInvalidRequestThreadsPerfStateIntoRenewalContext() {
    final var performanceAnalysisId = UUID.randomUUID().toString();
    final var lastPerformanceTimestampMillis = new AtomicLong(123L);
    final var resource = new TrackingRenewByBarcodeResource();
    final var invalidRequest = new JsonObject();

    final var result = resource.findLoan(invalidRequest, null, null, null, null,
      performanceAnalysisId, lastPerformanceTimestampMillis);
    final var latestTimestamp = lastPerformanceTimestampMillis.get();
    final var loan = new LoanBuilder().asDomainObject();
    final var contextResult = resource.toRenewalContext(Result.succeeded(loan), invalidRequest,
      "test-user", performanceAnalysisId, lastPerformanceTimestampMillis);

    assertFalse(result.join().succeeded());
    assertEquals(performanceAnalysisId, resource.loggedPerformanceAnalysisId);
    assertSame(lastPerformanceTimestampMillis, resource.loggedTimestampReference);
    assertTrue(latestTimestamp > 123L);
    assertThat(contextResult.succeeded(), is(true));
    assertEquals(performanceAnalysisId, contextResult.value().getPerformanceAnalysisId());
    assertEquals(latestTimestamp, contextResult.value().getLastPerformanceTimestampMillis());
  }

  @Test
  void shouldLogAndAdvanceTopLevelPerformanceStateUsingRenewalContext() {
    final var resource = new TrackingRenewByBarcodeResource();
    final var loan = new LoanBuilder().asDomainObject();
    final var context = RenewalContext.create(loan, new JsonObject(), "test-user",
      "analysis-id", 100L);

    final var updatedContext = resource.logTopLevelPerformanceStep(context,
      "renewal-context-created", "renew-by-barcode");

    assertEquals("analysis-id", updatedContext.getPerformanceAnalysisId());
    assertTrue(updatedContext.getLastPerformanceTimestampMillis() > 100L);
    assertEquals(updatedContext.getLastPerformanceTimestampMillis(),
      resource.latestLoggedContextTimestamp);
  }

  @Test
  void shouldLogFailedResultsUsingTopLevelRenewalResourceHelpers() {
    final var resource = new TrackingRenewByBarcodeResource();
    final var failedContextResult = Result.<RenewalContext>failed(
      new ValidationErrorFailure(emptyList()));
    final var failedResponseResult = Result.<HttpResponse>failed(
      new ValidationErrorFailure(emptyList()));

    final var loggedContextResult = resource.logFailedTopLevelRenewalContextStep(
      failedContextResult,
      "renewal-context-created", "renew-by-barcode");
    final var loggedResponseBuiltResult = resource.logFailedTopLevelResponseBuiltStep(
      failedResponseResult,
      "response-built", "renew-by-barcode");
    final var loggedRequestFinishedResult = resource.logFailedTopLevelRequestFinishedStep(
      failedResponseResult,
      "request-finished", "renew-by-barcode");

    assertSame(failedContextResult, loggedContextResult);
    assertSame(failedResponseResult, loggedResponseBuiltResult);
    assertSame(failedResponseResult, loggedRequestFinishedResult);
    assertEquals(singletonList("renewal-context-created"), resource.loggedFailedContextSteps);
    assertEquals(singletonList("response-built"), resource.loggedFailedResponseBuiltSteps);
    assertEquals(singletonList("request-finished"), resource.loggedFailedRequestFinishedSteps);
  }

  @Test
  void shouldIncludeLoanIdWhenLoggingSucceededLoanResultUsingSharedHelper() {
    final var resource = new TrackingRenewByBarcodeResource();
    final var loan = new LoanBuilder().asDomainObject();

    resource.logSucceededLoanLookup(Result.succeeded(loan), "loan-lookup-complete",
      "renew-by-barcode");

    assertThat(appender.getSinglePerformanceEvent().getMessage().getFormattedMessage(),
      containsString("loanId=" + loan.getId()));
  }

  @Test
  void shouldIncludeLoanIdWhenLoggingSucceededRenewalContextResultUsingSharedHelper() {
    final var resource = new TrackingRenewByBarcodeResource();
    final var loan = new LoanBuilder().asDomainObject();
    final var context = RenewalContext.create(loan, new JsonObject(), "test-user",
      "analysis-id", 100L);

    resource.logSucceededRenewalContext(Result.succeeded(context), "user-validation-complete",
      "renew-by-barcode");

    assertThat(appender.getSinglePerformanceEvent().getMessage().getFormattedMessage(),
      containsString("loanId=" + loan.getId()));
  }

  @Test
  void shouldIncludeLoanIdWhenLoggingSucceededTailResponseStep() {
    final var resource = new TrackingRenewByBarcodeResource();
    final var loan = new LoanBuilder().asDomainObject();
    final var loanIdReference = new AtomicReference<>(loan.getId());

    resource.logTailResponseStep(Result.succeeded(JsonHttpResponse.ok(new JsonObject())),
      loanIdReference, "response-built", "renew-by-barcode");

    assertThat(appender.getSinglePerformanceEvent().getMessage().getFormattedMessage(),
      containsString("loanId=" + loan.getId()));
  }

  @Test
  void shouldIncludeLoanIdWhenLoggingFailedTailResponseStep() {
    final var resource = new TrackingRenewByBarcodeResource();
    final var loan = new LoanBuilder().asDomainObject();
    final var loanIdReference = new AtomicReference<>(loan.getId());

    resource.logTailResponseStep(Result.failed(new ValidationErrorFailure(emptyList())),
      loanIdReference, "request-finished", "renew-by-barcode");

    assertThat(appender.getSinglePerformanceEvent().getMessage().getFormattedMessage(),
      containsString("loanId=" + loan.getId()));
  }

  @Test
  void shouldSyncTopLevelTimestampReferenceFromRenewalContextBeforeTailLogging() {
    final var resource = new TrackingRenewByBarcodeResource();
    final var context = RenewalContext.create(new LoanBuilder().asDomainObject(),
      new JsonObject(), "test-user", "analysis-id", 100L)
      .withLastPerformanceTimestampMillis(250L);
    final var timestampReference = new AtomicLong(100L);

    resource.syncTopLevelTimestampReference(Result.succeeded(context), timestampReference);

    assertEquals(250L, timestampReference.get());
  }

  @Test
  void shouldAdvanceTopLevelTimestampReferenceForFailedRenewalContextResult() {
    final var resource = new TrackingRenewByBarcodeResource();
    final var timestampReference = new AtomicLong(100L);

    resource.syncTopLevelTimestampReference(Result.failed(
      new ValidationErrorFailure(emptyList())), timestampReference);

    assertTrue(timestampReference.get() > 100L);
  }

  private Result<Loan> renew(Loan loan, Request topRequest,
    CirculationErrorHandler errorHandler) {

    RenewalContext renewalContext = RenewalContext.create(loan, new JsonObject(), "no-user")
      .withRequestQueue(new RequestQueue(singletonList(topRequest)));

    return new RenewByBarcodeResource(null)
      .regularRenew(renewalContext, errorHandler, getZonedDateTime())
      .map(RenewalContext::getLoan);
  }

  private Result<Loan> renew(Loan loan, CirculationErrorHandler errorHandler) {
    RenewalContext renewalContext = RenewalContext.create(loan, new JsonObject(), "no-user")
      .withRequestQueue(new RequestQueue(emptyList()));

    return new RenewByBarcodeResource(null)
      .regularRenew(renewalContext, errorHandler, getZonedDateTime())
      .map(RenewalContext::getLoan);
  }

  private Result<Loan> renew(LoanPolicy loanPolicy, Request topRequest,
    CirculationErrorHandler errorHandler) {

    final var loan = new LoanBuilder().asDomainObject().withLoanPolicy(loanPolicy);

    return renew(loan, topRequest, errorHandler);
  }

  private Result<Loan> renew(LoanPolicy loanPolicy,
    CirculationErrorHandler errorHandler) {

    final var loan = new LoanBuilder().asDomainObject().withLoanPolicy(loanPolicy);
    return renew(loan, errorHandler);
  }

  private boolean matchErrorReason(CirculationErrorHandler errorHandler, String expectedReason) {
    return errorHandler.getErrors().keySet().stream()
      .map(ValidationErrorFailure.class::cast)
      .anyMatch(httpFailure -> httpFailure.hasErrorWithReason(expectedReason));
  }

  private boolean matchErrorType(CirculationErrorHandler errorHandler,
    CirculationErrorType errorType) {

    return errorHandler.getErrors().containsValue(errorType);
  }

  private static class TrackingRenewByBarcodeResource extends RenewByBarcodeResource {
    private String loggedPerformanceAnalysisId;
    private AtomicLong loggedTimestampReference;
    private Long latestLoggedContextTimestamp;
    private final java.util.List<String> loggedFailedContextSteps = new java.util.ArrayList<>();
    private final java.util.List<String> loggedFailedResponseBuiltSteps = new java.util.ArrayList<>();
    private final java.util.List<String> loggedFailedRequestFinishedSteps = new java.util.ArrayList<>();

    private TrackingRenewByBarcodeResource() {
      super(null);
    }

    @Override
    protected <T> Result<T> logPerformanceStep(Logger logger, Result<T> result,
      String performanceAnalysisId, AtomicLong lastPerformanceTimestampMillis,
      String stepName, String endpoint) {

      loggedPerformanceAnalysisId = performanceAnalysisId;
      loggedTimestampReference = lastPerformanceTimestampMillis;

      return super.logPerformanceStep(logger, result, performanceAnalysisId,
        lastPerformanceTimestampMillis, stepName, endpoint);
    }

    private RenewalContext logTopLevelPerformanceStep(RenewalContext context,
      String stepName, String endpoint) {

      final RenewalContext updatedContext = super.logPerformanceStep(context, stepName, endpoint);

      latestLoggedContextTimestamp = updatedContext.getLastPerformanceTimestampMillis();

      return updatedContext;
    }

    private Result<RenewalContext> logFailedTopLevelRenewalContextStep(
      Result<RenewalContext> result,
      String stepName, String endpoint) {

      return logAndAdvancePerformanceStep(result, "analysis-id",
        new AtomicLong(100L), stepName, endpoint);
    }

    private Result<HttpResponse> logFailedTopLevelResponseBuiltStep(Result<HttpResponse> result,
      String stepName, String endpoint) {

      return logPerformanceStep(result, "analysis-id", new AtomicLong(100L), stepName,
        endpoint);
    }

    private Result<HttpResponse> logFailedTopLevelRequestFinishedStep(Result<HttpResponse> result,
      String stepName, String endpoint) {

      return logPerformanceStep(result, "analysis-id", new AtomicLong(100L), stepName,
        endpoint);
    }

    private Result<HttpResponse> logTailResponseStep(Result<HttpResponse> result,
      AtomicReference<String> loanIdReference, String stepName, String endpoint) {

      return super.logPerformanceStep(result, "analysis-id", new AtomicLong(100L),
        loanIdReference, stepName, endpoint);
    }

    private Result<Loan> logSucceededLoanLookup(Result<Loan> result, String stepName,
      String endpoint) {

      return logPerformanceStep(result, "analysis-id", new AtomicLong(100L), stepName,
        endpoint);
    }

    private Result<RenewalContext> logSucceededRenewalContext(Result<RenewalContext> result,
      String stepName, String endpoint) {

      return logPerformanceStep(result, "analysis-id", new AtomicLong(100L), stepName,
        endpoint);
    }

    private Result<RenewalContext> syncTopLevelTimestampReference(Result<RenewalContext> result,
      AtomicLong lastPerformanceTimestampMillis) {

      return synchronizeTimestampReference(result, lastPerformanceTimestampMillis);
    }

    @Override
    protected Result<RenewalContext> logAndAdvancePerformanceStep(Result<RenewalContext> result,
      String performanceAnalysisId, AtomicLong lastPerformanceTimestampMillis,
      String stepName, String endpoint) {

      if (result.failed()) {
        loggedFailedContextSteps.add(stepName);
      }

      return super.logAndAdvancePerformanceStep(result, performanceAnalysisId,
        lastPerformanceTimestampMillis, stepName, endpoint);
    }

    @Override
    protected <T> Result<T> logPerformanceStep(Result<T> result,
      String performanceAnalysisId, AtomicLong lastPerformanceTimestampMillis,
      String stepName, String endpoint) {

      if (result.failed() && "response-built".equals(stepName)) {
        loggedFailedResponseBuiltSteps.add(stepName);
      }

      if (result.failed() && "request-finished".equals(stepName)) {
        loggedFailedRequestFinishedSteps.add(stepName);
      }

      return super.logPerformanceStep(result, performanceAnalysisId,
        lastPerformanceTimestampMillis, stepName, endpoint);
    }
  }

  private static final class RenewalResourceTestAppender extends AbstractAppender {
    private final List<LogEvent> events = new ArrayList<>();

    private RenewalResourceTestAppender() {
      super("RegularRenewalTestAppender", null, PatternLayout.createDefaultLayout(),
        false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      events.add(event.toImmutable());
    }

    private LogEvent getSinglePerformanceEvent() {
      assertEquals(1, events.size());
      return events.stream()
        .filter(event -> RenewalPerformanceLogger.RENEWAL_PERF_ANALYSIS.equals(event.getMarker()))
        .findFirst()
        .orElseThrow();
    }
  }
}
