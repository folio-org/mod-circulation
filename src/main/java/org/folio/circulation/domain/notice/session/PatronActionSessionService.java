package org.folio.circulation.domain.notice.session;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContextWithoutUser;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createUserContext;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public class PatronActionSessionService {

  private static final int DEFAULT_SESSION_SIZE_LIMIT = 200;

  private static EnumMap<PatronActionType, NoticeEventType> actionToEventMap;

  static {
    actionToEventMap = new EnumMap<>(PatronActionType.class);
    actionToEventMap.put(PatronActionType.CHECK_OUT, NoticeEventType.CHECK_OUT);
    actionToEventMap.put(PatronActionType.CHECK_IN, NoticeEventType.CHECK_IN);
  }

  private final PatronActionSessionRepository patronActionSessionRepository;
  private final PatronNoticeService patronNoticeService;

  public static PatronActionSessionService using(Clients clients) {
    return new PatronActionSessionService(
      PatronActionSessionRepository.using(clients),
      PatronNoticeService.using(clients));
  }

  public PatronActionSessionService(
    PatronActionSessionRepository patronActionSessionRepository,
    PatronNoticeService patronNoticeService) {
    this.patronActionSessionRepository = patronActionSessionRepository;
    this.patronNoticeService = patronNoticeService;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> saveCheckOutSessionRecord(LoanAndRelatedRecords records) {
    UUID patronId = UUID.fromString(records.getUserId());
    UUID loanId = UUID.fromString(records.getLoan().getId());

    PatronSessionRecord patronSessionRecord =
      new PatronSessionRecord(UUID.randomUUID(),
        patronId, loanId, PatronActionType.CHECK_OUT);

    return patronActionSessionRepository.create(patronSessionRecord)
      .thenApply(mapResult(v -> records));
  }

  public CompletableFuture<Result<Void>> endSession(String patronId, PatronActionType actionType) {
    return patronActionSessionRepository.findPatronActionSessions(patronId, actionType, DEFAULT_SESSION_SIZE_LIMIT)
      .thenCompose(r -> r.after(this::sendNotices))
      .thenCompose(r -> r.after(records ->
        allOf(Objects.isNull(records) ? Collections.emptyList() : records.getRecords(), patronActionSessionRepository::delete)))
      .thenApply(mapResult(v -> null));
  }

  private CompletableFuture<Result<MultipleRecords<PatronSessionRecord>>> sendNotices(
    MultipleRecords<PatronSessionRecord> records) {

    if (records.isEmpty()) {
      return completedFuture(succeeded(null));
    }
    List<PatronSessionRecord> sessionRecords = new ArrayList<>(records.getRecords());

    PatronSessionRecord recordSample = sessionRecords.get(0);

    //The user is the same for all records
    User user = recordSample.getLoan().getUser();

    List<PatronNoticeEvent> patronNoticeEvents = sessionRecords.stream()
      .map(r -> new PatronNoticeEventBuilder()
        .withItem(r.getLoan().getItem())
        .withUser(r.getLoan().getUser())
        .withEventType(actionToEventMap.get(r.getActionType()))
        .withNoticeContext(createLoanNoticeContextWithoutUser(r.getLoan()))
        .build())
      .collect(Collectors.toList());

    return patronNoticeService.acceptMultipleNoticeEvent(patronNoticeEvents,
      loanContexts -> new JsonObject()
        .put("user", createUserContext(user))
        .put("loans", loanContexts)
    )
      .thenApply(mapResult(v -> records));
  }
}
