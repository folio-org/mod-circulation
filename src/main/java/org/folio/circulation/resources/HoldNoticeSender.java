package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.NoticeEventType.HOLD_REQUEST_FOR_ITEM;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContext;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.notice.ImmediatePatronNoticeService;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.SingleImmediatePatronNoticeService;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class HoldNoticeSender {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ImmediatePatronNoticeService patronNoticeService;
  private final LoanRepository loanRepository;
  private final UserRepository userRepository;
  private final RequestRepository requestRepository;
  private final EventPublisher eventPublisher;
  private final ProxyRelationshipValidator proxyRelationshipValidator;
  private final ItemByInstanceIdFinder itemByInstanceIdFinder;

  public static HoldNoticeSender using(Clients clients) {
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var requestRepository = RequestRepository.using(clients, itemRepository,
      userRepository, loanRepository);

    return new HoldNoticeSender(
      new SingleImmediatePatronNoticeService(clients),
      loanRepository,
      userRepository,
      requestRepository,
      new EventPublisher(clients),
      new ProxyRelationshipValidator(clients),
      new ItemByInstanceIdFinder(clients.holdingsStorage(), itemRepository)
    );
  }

  public CompletableFuture<Result<Void>> sendHoldNoticeIfNeeded(Request request) {
    if (request == null) {
      log.warn("sendHoldNoticeIfNeeded:: request is null, skipping");
      return emptyAsync();
    }

    log.debug("sendHoldNoticeIfNeeded:: processing hold request {}", request.getId());

    if (!request.isHold()) {
      log.debug("sendHoldNoticeIfNeeded:: not a hold request, skipping");
      return emptyAsync();
    }

    if (request.isTitleLevel()) {
      return sendHoldNoticesForTitleLevelRequest(request);
    } else if (request.hasItemId()) {
      return sendHoldNoticeForItemLevelRequest(request);
    }

    log.debug("sendHoldNoticeIfNeeded:: request has no item ID and is not title-level");
    return emptyAsync();
  }

  private CompletableFuture<Result<Void>> sendHoldNoticeForItemLevelRequest(Request request) {
    return fetchLoanForRequest(request)
      .thenCompose(r -> r.after(this::sendHoldNoticeToCurrentBorrower));
  }

  private CompletableFuture<Result<Void>> sendHoldNoticesForTitleLevelRequest(Request request) {
    log.info("sendHoldNoticesForTitleLevelRequest:: sending hold notices for title-level request: {}",
      request.getId());

    return collectOpenLoansForTitle(request.getInstanceId())
      .thenCompose(loansResult -> loansResult.after(loans ->
        filterLoansWithoutExistingHolds(loans, request)
          .thenCompose(filteredResult -> filteredResult.after(this::sendHoldNoticesToAllBorrowers))
      ));
  }

  // ============================================================================
  // ITEM-LEVEL HOLD NOTICES
  // ============================================================================

  private CompletableFuture<Result<Request>> fetchLoanForRequest(Request request) {
    if (request.hasLoan()) {
      return loanRepository.fetchLatestPatronInfoAddedComment(request.getLoan())
        .thenApply(r -> r.map(request::withLoan));
    }
    return CompletableFuture.completedFuture(succeeded(request));
  }

  private CompletableFuture<Result<Void>> sendHoldNoticeToCurrentBorrower(Request request) {
    Loan loan = request.getLoan();

    if (!shouldSendHoldNoticeForLoan(loan)) {
      return emptyAsync();
    }

    return isFirstHoldRequestForItem(loan.getItemId(), request)
      .thenCompose(result -> {
        if (result.failed()) {
          log.warn("sendHoldNoticeToCurrentBorrower:: query failed, skipping notice for safety");
          return emptyAsync();
        }

        boolean isFirst = result.value();
        if (Boolean.FALSE.equals(isFirst)) {
          log.debug("sendHoldNoticeToCurrentBorrower:: not first hold request, skipping notice");
          return emptyAsync();
        }
        return sendNoticeAndPublishEvent(loan);
      });
  }

  private boolean shouldSendHoldNoticeForLoan(Loan loan) {
    if (loan == null || !loan.isOpen()) {
      log.debug("shouldSendHoldNoticeForLoan:: no open loan found");
      return false;
    }

    if (loan.getUser() == null || loan.getItem() == null) {
      log.debug("shouldSendHoldNoticeForLoan:: loan missing user or item data");
      return false;
    }

    return true;
  }

  private CompletableFuture<Result<Boolean>> isFirstHoldRequestForItem(String itemId, Request currentRequest) {
    return requestRepository.findOpenRequestsByItemIds(Collections.singletonList(itemId))
      .thenApply(result -> {
        if (result.failed()) {
          log.warn("isFirstHoldRequestForItem:: failed to query requests for item {}", itemId);
          return Result.failed(result.cause());
        }

        long holdCount = result.value().getRecords().stream()
          .filter(Request::isHold)
          .filter(r -> !r.getId().equals(currentRequest.getId())) // Exclude current request
          .count();

        boolean isFirst = holdCount == 0; // Zero other holds means this is the first
        log.debug("isFirstHoldRequestForItem:: found {} other hold requests, isFirst: {}", holdCount, isFirst);
        return Result.succeeded(isFirst);
      });
  }

  // ============================================================================
  // TITLE-LEVEL HOLD NOTICES
  // ============================================================================
  
  private CompletableFuture<Result<List<Loan>>> collectOpenLoansForTitle(String instanceId) {
    log.debug("collectOpenLoansForTitle:: collecting open loans for instance {}", instanceId);

    return fetchItemsForInstance(instanceId)
      .thenCompose(this::fetchOpenLoansForItems)
      .thenCompose(this::enrichLoansWithUserData)
      .thenApply(Result::succeeded);
  }
  
  private CompletableFuture<List<Item>> fetchItemsForInstance(String instanceId) {
    if (instanceId == null) {
      log.warn("fetchItemsForInstance:: instanceId is null");
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    try {
      UUID instanceUUID = UUID.fromString(instanceId);
      return itemByInstanceIdFinder.getItemsByInstanceId(instanceUUID, false)
        .thenApply(itemsResult -> {
          if (itemsResult.failed()) {
            log.warn("fetchItemsForInstance:: failed to fetch items: {}", itemsResult.cause());
            return Collections.emptyList();
          }

          var items = itemsResult.value();
          if (items == null || items.isEmpty()) {
            log.info("fetchItemsForInstance:: no items found for instance");
            return Collections.emptyList();
          }

          log.debug("fetchItemsForInstance:: found {} items", items.size());
          return new ArrayList<>(items);
        });
    } catch (IllegalArgumentException e) {
      log.error("fetchItemsForInstance:: invalid instanceId format: {}", instanceId, e);
      return CompletableFuture.completedFuture(Collections.emptyList());
    }
  }

  private CompletableFuture<List<Loan>> fetchOpenLoansForItems(List<Item> items) {
    if (items == null || items.isEmpty()) {
      log.debug("fetchOpenLoansForItems:: no items to fetch loans for");
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    List<CompletableFuture<Loan>> loanFutures = items.stream()
      .map(this::fetchOpenLoanForItem)
      .toList();

    return CompletableFuture.allOf(loanFutures.toArray(new CompletableFuture[0]))
      .thenApply(v -> loanFutures.stream()
        .map(CompletableFuture::join)
        .filter(Objects::nonNull)
        .toList());
  }

  private CompletableFuture<Loan> fetchOpenLoanForItem(Item item) {
    return loanRepository.findOpenLoanForItem(item)
      .thenApply(result -> {
        if (result.failed() || result.value() == null) {
          log.debug("fetchOpenLoanForItem:: no open loan for item {}", item.getItemId());
          return null;
        }
        return result.value();
      });
  }

  private CompletableFuture<List<Loan>> enrichLoansWithUserData(List<Loan> loans) {
    if (loans == null || loans.isEmpty()) {
      log.debug("enrichLoansWithUserData:: no loans to enrich");
      return CompletableFuture.completedFuture(Collections.emptyList());
    }

    return userRepository.findUsersForLoans(loans)
      .thenApply(result -> {
        if (result.failed()) {
          log.warn("enrichLoansWithUserData:: failed to fetch users: {}", result.cause());
          return Collections.emptyList();
        }

        List<Loan> validLoans = result.value().stream()
          .filter(loan -> loan.getUser() != null)
          .toList();

        if (validLoans.size() < loans.size()) {
          log.warn("enrichLoansWithUserData:: {} of {} loans could not be enriched with user data",
            loans.size() - validLoans.size(), loans.size());
        }

        log.debug("enrichLoansWithUserData:: enriched {} loans with user data", validLoans.size());
        return validLoans;
      });
  }

  private CompletableFuture<Result<List<Loan>>> filterLoansWithoutExistingHolds(
    List<Loan> loans, Request currentRequest) {

    if (loans == null || loans.isEmpty()) {
      log.debug("filterLoansWithoutExistingHolds:: no loans to filter");
      return CompletableFuture.completedFuture(Result.succeeded(Collections.emptyList()));
    }

    log.debug("filterLoansWithoutExistingHolds:: filtering {} loans", loans.size());

    List<CompletableFuture<Loan>> filteredLoanFutures = loans.stream()
      .map(loan -> isFirstHoldRequestForItem(loan.getItemId(), currentRequest)
        .thenApply(result -> {
          if (result.failed()) {
            log.warn("filterLoansWithoutExistingHolds:: failed to check holds for item {}, excluding from notifications",
              loan.getItemId());
            return null;
          }

          boolean isFirst = result.value();
          if (isFirst) {
            log.debug("filterLoansWithoutExistingHolds:: item {} has no holds, will notify borrower",
              loan.getItemId());
            return loan;
          } else {
            log.debug("filterLoansWithoutExistingHolds:: item {} already has holds, skipping notification",
              loan.getItemId());
            return null;
          }
        }))
      .toList();

    return CompletableFuture.allOf(filteredLoanFutures.toArray(new CompletableFuture[0]))
      .thenApply(v -> {
        List<Loan> filteredLoans = filteredLoanFutures.stream()
          .map(CompletableFuture::join)
          .filter(Objects::nonNull)
          .toList();

        log.info("filterLoansWithoutExistingHolds:: filtered {} loans to {} without existing holds",
          loans.size(), filteredLoans.size());

        return Result.succeeded(filteredLoans);
      });
  }

  private CompletableFuture<Result<Void>> sendHoldNoticesToAllBorrowers(List<Loan> loans) {
    if (loans.isEmpty()) {
      log.info("sendHoldNoticesToAllBorrowers:: no current borrowers to notify");
      return emptyAsync();
    }

    log.info("sendHoldNoticesToAllBorrowers:: sending notices to {} borrowers", loans.size());

    List<CompletableFuture<Result<Void>>> noticeAndEventFutures = loans.stream()
      .map(this::sendNoticeAndPublishEvent)
      .toList();

    return allOfResults(noticeAndEventFutures);
  }

  // ============================================================================
  // NOTICE SENDING
  // ============================================================================

  private CompletableFuture<Result<Void>> sendNoticeAndPublishEvent(Loan loan) {
    return sendLoanNotice(loan)
      .thenCompose(r -> r.after(v -> eventPublisher.publishHoldRequestedEvent(loan)));
  }

  private CompletableFuture<Result<Void>> sendLoanNotice(Loan loan) {
    return getRecipientId(loan)
      .thenCompose(result -> result.after(recipientId -> {
        var patronNoticeEvent = new PatronNoticeEventBuilder()
          .withItem(loan.getItem())
          .withUser(loan.getUser())
          .withRecipientId(recipientId)
          .withEventType(HOLD_REQUEST_FOR_ITEM)
          .withNoticeContext(createLoanNoticeContext(loan))
          .withNoticeLogContext(NoticeLogContext.from(loan))
          .build();

        return patronNoticeService.acceptNoticeEvent(patronNoticeEvent);
      }));
  }

  private CompletableFuture<Result<String>> getRecipientId(Loan loan) {
    return proxyRelationshipValidator.hasActiveProxyRelationshipWithNotificationsSentToProxy(loan)
      .thenApply(result -> result.map(sentToProxy -> {
        if (Boolean.TRUE.equals(sentToProxy)) {
          log.info("getRecipientId:: notice recipient is proxy user: {}", loan.getProxyUserId());
          return loan.getProxyUserId();
        }

        log.info("getRecipientId:: notice recipient is user: {}", loan.getUserId());
        return loan.getUserId();
      }));
  }

  // ============================================================================
  // UTILITY METHODS
  // ============================================================================

  private CompletableFuture<Result<Void>> allOfResults(List<CompletableFuture<Result<Void>>> futures) {
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> {
        List<Result<Void>> results = futures.stream()
          .map(CompletableFuture::join)
          .toList();

        List<Result<Void>> failures = results.stream()
          .filter(Result::failed)
          .toList();

        if (failures.isEmpty()) {
          return Result.succeeded(null);
        }
        
        log.error("allOfResults:: {} of {} notice sending operations failed",
          failures.size(), results.size());
        failures.forEach(failure ->
          log.error("allOfResults:: failure: {}", failure.cause()));

        return failures.get(0);
      });
  }
}
