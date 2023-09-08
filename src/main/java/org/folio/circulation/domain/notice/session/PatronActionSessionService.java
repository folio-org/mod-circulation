package org.folio.circulation.domain.notice.session;

import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.groupingBy;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContextWithoutUser;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.results.AsynchronousResultBindings.safelyInitialise;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.ImmediatePatronNoticeService;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.combiner.LoanNoticeContextCombiner;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.sessions.PatronActionSessionRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class PatronActionSessionService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final PageLimit DEFAULT_SESSION_SIZE_PAGE_LIMIT = limit(200);
  private static final EnumMap<PatronActionType, NoticeEventType> actionToEventMap;

  static {
    actionToEventMap = new EnumMap<>(PatronActionType.class);
    actionToEventMap.put(PatronActionType.CHECK_OUT, NoticeEventType.CHECK_OUT);
    actionToEventMap.put(PatronActionType.CHECK_IN, NoticeEventType.CHECK_IN);
  }

  private final PatronActionSessionRepository patronActionSessionRepository;
  private final ImmediatePatronNoticeService patronNoticeService;
  private final LoanRepository loanRepository;
  protected final EventPublisher eventPublisher;

  public static PatronActionSessionService using(Clients clients,
    PatronActionSessionRepository patronActionSessionRepository, LoanRepository loanRepository) {

    return new PatronActionSessionService(patronActionSessionRepository,
      new ImmediatePatronNoticeService(clients, new LoanNoticeContextCombiner()),
      loanRepository,
      new EventPublisher(clients.pubSubPublishingService()));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> saveCheckOutSessionRecord(
    LoanAndRelatedRecords records) {

    UUID patronId = UUID.fromString(records.getUserId());
    UUID loanId = UUID.fromString(records.getLoan().getId());

    PatronSessionRecord patronSessionRecord = new PatronSessionRecord(UUID.randomUUID(), patronId,
      loanId, PatronActionType.CHECK_OUT);

    return patronActionSessionRepository.create(patronSessionRecord)
      .thenApply(mapResult(v -> records));
  }

  public CompletableFuture<Result<CheckInContext>> saveCheckInSessionRecord(CheckInContext context) {
    Loan loan = context.getLoan();
    if (loan == null) {
      log.info("CheckInSessionRecord is not saved, context doesn't have a valid loan.");
      return completedFuture(of(() -> context));
    }
    UUID patronId = UUID.fromString(loan.getUserId());
    UUID loanId = UUID.fromString(loan.getId());
    PatronSessionRecord patronSessionRecord = new PatronSessionRecord(UUID.randomUUID(),
        patronId, loanId, context.getSessionId(), PatronActionType.CHECK_IN);

    return patronActionSessionRepository.create(patronSessionRecord)
      .thenApply(mapResult(v -> context));
  }

  public CompletableFuture<Result<Void>> endSessions(String patronId, PatronActionType actionType) {
    return safelyInitialise(() -> findSessions(patronId, actionType))
      .thenCompose(r -> r.after(this::processSessions))
      .thenApply(this::handleResult);
  }

  public CompletableFuture<Result<Void>> endExpiredSessions(List<ExpiredSession> expiredSessions) {
    return ofAsync(() -> expiredSessions)
      .thenCompose(r -> r.after(this::findSessions))
      .thenCompose(r -> r.after(this::groupAndProcessSessions))
      .thenApply(this::handleResult);
  }

  private CompletableFuture<Result<List<PatronSessionRecord>>> findSessions(String patronId,
    PatronActionType actionType) {

    return patronActionSessionRepository.findPatronActionSessions(patronId, actionType,
      DEFAULT_SESSION_SIZE_PAGE_LIMIT);
  }

  private CompletableFuture<Result<List<PatronSessionRecord>>> findSessions(
    List<ExpiredSession> expiredSessions) {

    return patronActionSessionRepository.findPatronActionSessions(expiredSessions);
  }

  private CompletableFuture<Result<List<PatronSessionRecord>>> processSessions(
    List<PatronSessionRecord> sessions) {

    return ofAsync(() -> sessions)
      .thenApply(mapResult(this::discardInvalidSessions))
      .thenCompose(r -> r.after(this::sendNotice))
      .thenCompose(ignored -> deleteSessions(sessions));
  }

  private List<PatronSessionRecord> discardInvalidSessions(List<PatronSessionRecord> sessions) {
    List<PatronSessionRecord> validSessions = new ArrayList<>();

    for (PatronSessionRecord session : sessions) {
      Loan loan = session.getLoan();
      String errorMessage = null;

      if (loan == null) {
        errorMessage = "referenced loan was not found";
      } else {
        if (loan.getItem() == null || loan.getItem().isNotFound()) {
          errorMessage = "referenced item was not found";
        }
        if (loan.getUser() == null) {
          errorMessage = "referenced user was not found";
        }
      }

      if (errorMessage != null) {
        errorMessage += ". " + session;
        log.error("Failed to send notice for patron action session: {}", errorMessage);
        publishNoticeErrorEvent(singletonList(session), errorMessage);
      } else {
        validSessions.add(session);
      }
    }

    if (validSessions.size() < sessions.size()) {
      log.warn("{} session(s) are invalid in the group of {} session(s)",
        sessions.size() - validSessions.size(), sessions.size());
    }

    return validSessions;
  }

  private CompletableFuture<Result<Void>> groupAndProcessSessions(
    List<PatronSessionRecord> sessions) {

    var groupedSessions = sessions.stream()
      .collect(groupingBy(PatronSessionRecord::getPatronId))
      .values();

    return allOf(groupedSessions, this::processSessions)
      .thenApply(mapResult(v -> null));
  }

  // all sessions must be for the same patron
  private CompletableFuture<Result<List<PatronSessionRecord>>> sendNotice(
    List<PatronSessionRecord> sessions) {

    if (sessions.isEmpty()) {
      log.info("No patron action sessions to process");
      return ofAsync(() -> null);
    }

    //The user is the same for all sessions
    User user = sessions.get(0).getLoan().getUser();

    log.info("Attempting to send a notice for a group of {} action sessions to user {}",
      sessions.size(), user.getId());

    return patronNoticeService.acceptNoticeEvents(buildNoticeEvents(sessions))
      .thenApply(mapResult(v -> sessions));
  }

  private CompletableFuture<Result<Void>> publishNoticeErrorEvent(
    List<PatronSessionRecord> sessions, String errorMessage) {

    return eventPublisher.publishNoticeErrorLogEvent(NoticeLogContext.from(sessions), errorMessage);
  }

  private CompletableFuture<Result<List<PatronSessionRecord>>> deleteSessions(
    List<PatronSessionRecord> sessions) {

    return sessions == null || sessions.isEmpty()
      ? ofAsync(() -> sessions)
      : allOf(sessions, patronActionSessionRepository::delete);
  }

  private Result<Void> handleResult(Result<?> result) {
    if (result.failed()) {
      log.error("Failed to process patron action sessions: {}", result.cause());
    }

    return succeeded(null);
  }

  private List<PatronNoticeEvent> buildNoticeEvents(List<PatronSessionRecord> sessions) {
    return sessions.stream()
      .map(this::buildPatronNoticeEvent)
      .collect(Collectors.toList());
  }

  private PatronNoticeEvent buildPatronNoticeEvent(PatronSessionRecord session) {
    Loan loan = session.getLoan();
    loanRepository.fetchLatestPatronInfoAddedComment(loan).join();
    return new PatronNoticeEventBuilder()
      .withItem(loan.getItem())
      .withUser(loan.getUser())
      .withEventType(actionToEventMap.get(session.getActionType()))
      .withNoticeContext(createLoanNoticeContextWithoutUser(loan))
      .withNoticeLogContext(NoticeLogContext.from(loan))
      .build();
  }

}
