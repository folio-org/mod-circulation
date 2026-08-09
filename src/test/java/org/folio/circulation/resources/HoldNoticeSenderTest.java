package org.folio.circulation.resources;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.notice.ImmediatePatronNoticeService;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import api.support.builders.ItemBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
@DisplayName("HoldNoticeSender Tests")
class HoldNoticeSenderTest {

    @Mock
    private ImmediatePatronNoticeService patronNoticeService;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RequestRepository requestRepository;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private ProxyRelationshipValidator proxyRelationshipValidator;
    @Mock
    private ItemByInstanceIdFinder itemByInstanceIdFinder;

    @InjectMocks
    private HoldNoticeSender holdNoticeSender;

    private Request holdRequest;
    private Loan openLoan;
    private Item item;
    private User borrower;
    private UUID itemId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        userId = UUID.randomUUID();

        borrower = buildUser("John", "Doe");
        item = Item.from(new ItemBuilder().withId(itemId).create());

        openLoan = Loan.from(new LoanBuilder()
            .withId(UUID.randomUUID())
            .withItemId(itemId)
            .withUserId(userId)
            .open()
            .create())
            .withItem(item)
            .withUser(borrower);

        holdRequest = Request.from(new RequestBuilder()
            .hold()
            .withId(UUID.randomUUID())
            .withItemId(itemId)
            .withRequesterId(UUID.randomUUID())
            .create())
            .withLoan(openLoan);


        
    }

    private static User buildUser(String firstName, String lastName) {
        return User.from(new JsonObject()
            .put("id", UUID.randomUUID().toString())
            .put("personal", new JsonObject()
                .put("firstName", firstName)
                .put("lastName", lastName)));
    }

    // ============================================================================
    // ITEM-LEVEL HOLD NOTICE TESTS
    // ============================================================================

    @Test
    @DisplayName("Should send notice for first hold request on checked-out item")
    void shouldSendNoticeForFirstHoldRequestOnCheckedOutItem() throws Exception {
        MultipleRecords<Request> singleHoldRequest = new MultipleRecords<>(
            Collections.singletonList(holdRequest), 1);
        when(requestRepository.findOpenRequestsByItemIds(Collections.singletonList(item.getItemId())))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(singleHoldRequest)));
        when(loanRepository.fetchLatestPatronInfoAddedComment(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(openLoan)));
        when(proxyRelationshipValidator
            .hasActiveProxyRelationshipWithNotificationsSentToProxy(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(false)));

        // Stub event publisher and notice service for this test
        when(eventPublisher.publishHoldRequestedEvent(any(Loan.class)))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(null)));

        when(patronNoticeService.acceptNoticeEvent(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(null)));

        Result<Void> result = holdNoticeSender.sendHoldNoticeIfNeeded(holdRequest).get();

        assertTrue(result.succeeded());
        verify(patronNoticeService, times(1)).acceptNoticeEvent(any());
        verify(eventPublisher, times(1)).publishHoldRequestedEvent(openLoan);
    }

    @Test
    @DisplayName("Should not send notice for second hold request on same item")
    void shouldNotSendNoticeForSecondHoldRequestOnSameItem() throws Exception {
        Request secondHoldRequest = Request.from(new RequestBuilder()
            .hold()
            .withId(UUID.randomUUID())
            .withItemId(itemId)
            .withRequesterId(UUID.randomUUID())
            .create());
        MultipleRecords<Request> twoHoldRequests = new MultipleRecords<>(
            Arrays.asList(holdRequest, secondHoldRequest), 2);
        when(requestRepository.findOpenRequestsByItemIds(Collections.singletonList(item.getItemId())))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(twoHoldRequests)));
        when(loanRepository.fetchLatestPatronInfoAddedComment(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(openLoan)));

        Result<Void> result = holdNoticeSender.sendHoldNoticeIfNeeded(holdRequest).get();

        assertTrue(result.succeeded());
        verify(patronNoticeService, never()).acceptNoticeEvent(any());
        verify(eventPublisher, never()).publishHoldRequestedEvent(any());
    }

    @Test
    @DisplayName("Should not send notice when loan is not open")
    void shouldNotSendNoticeWhenLoanIsNotOpen() throws Exception {
        Loan closedLoan = Loan.from(new LoanBuilder()
            .withId(UUID.randomUUID())
            .withItemId(itemId)
            .withUserId(userId)
            .closed()
            .create())
            .withItem(item)
            .withUser(borrower);

        Request requestWithClosedLoan = holdRequest.withLoan(closedLoan);
        when(loanRepository.fetchLatestPatronInfoAddedComment(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(closedLoan)));

        Result<Void> result = holdNoticeSender.sendHoldNoticeIfNeeded(requestWithClosedLoan).get();

        assertTrue(result.succeeded());
        verify(patronNoticeService, never()).acceptNoticeEvent(any());
        verify(eventPublisher, never()).publishHoldRequestedEvent(any());
    }

    @Test
    @DisplayName("Should not send notice for non-hold request")
    void shouldNotSendNoticeForNonHoldRequest() throws Exception {
        Request recallRequest = Request.from(new RequestBuilder()
            .recall()
            .withId(UUID.randomUUID())
            .withItemId(itemId)
            .withRequesterId(UUID.randomUUID())
            .create())
            .withLoan(openLoan);

        Result<Void> result = holdNoticeSender.sendHoldNoticeIfNeeded(recallRequest).get();

        assertTrue(result.succeeded());
        verify(patronNoticeService, never()).acceptNoticeEvent(any());
        verify(eventPublisher, never()).publishHoldRequestedEvent(any());
    }

    // ============================================================================
    // TITLE-LEVEL HOLD NOTICE TESTS
    // ============================================================================

    @Test
    @DisplayName("Should send notices to all borrowers for title-level hold")
    void shouldSendNoticesToAllBorrowersForTitleLevelHold() throws Exception {
        UUID instanceId = UUID.randomUUID();
        User borrower1 = buildUser("Alice", "Smith");
        User borrower2 = buildUser("Bob", "Jones");
        UUID itemId1 = UUID.randomUUID();
        UUID itemId2 = UUID.randomUUID();

        Item item1 = Item.from(new ItemBuilder().withId(itemId1).create());
        Item item2 = Item.from(new ItemBuilder().withId(itemId2).create());

        Loan loan1 = Loan.from(new LoanBuilder()
            .withId(UUID.randomUUID())
            .withItemId(itemId1)
            .withUserId(UUID.fromString(borrower1.getId()))
            .open()
            .create())
            .withItem(item1)
            .withUser(borrower1);

        Loan loan2 = Loan.from(new LoanBuilder()
            .withId(UUID.randomUUID())
            .withItemId(itemId2)
            .withUserId(UUID.fromString(borrower2.getId()))
            .open()
            .create())
            .withItem(item2)
            .withUser(borrower2);

        Request titleLevelHoldRequest = Request.from(new RequestBuilder()
            .hold()
            .withId(UUID.randomUUID())
            .withInstanceId(instanceId)
            .withRequestLevel("Title")
            .withRequesterId(UUID.randomUUID())
            .create())
            .withTlrSettingsConfiguration(
                TlrSettingsConfiguration.from(new JsonObject()
                    .put("titleLevelRequestsFeatureEnabled", true))
            );

        when(itemByInstanceIdFinder.getItemsByInstanceId(instanceId, false))
            .thenReturn(CompletableFuture.completedFuture(
                Result.succeeded(Arrays.asList(item1, item2))
            ));

        // Mock for first-hold check (no existing holds)
        MultipleRecords<Request> noExistingHolds = new MultipleRecords<>(
            Collections.singletonList(titleLevelHoldRequest), 1);
        when(requestRepository.findOpenRequestsByItemIds(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(noExistingHolds)));

        // Mock for individual loan fetching (parallel queries)
        when(loanRepository.findOpenLoanForItem(item1))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(loan1)));
        when(loanRepository.findOpenLoanForItem(item2))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(loan2)));

        // Mock for batch user enrichment
        Loan enrichedLoan1 = loan1.withUser(borrower1);
        Loan enrichedLoan2 = loan2.withUser(borrower2);
        Collection<Loan> enrichedLoans = Arrays.asList(enrichedLoan1, enrichedLoan2);
        when(userRepository.findUsersForLoans(any(Collection.class)))
            .thenReturn(CompletableFuture.completedFuture(
                Result.succeeded(enrichedLoans)));
        when(proxyRelationshipValidator
            .hasActiveProxyRelationshipWithNotificationsSentToProxy(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(false)));

        // Stub event publisher and notice service for this test
        when(eventPublisher.publishHoldRequestedEvent(any(Loan.class)))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(null)));

        when(patronNoticeService.acceptNoticeEvent(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(null)));

        Result<Void> result = holdNoticeSender.sendHoldNoticeIfNeeded(titleLevelHoldRequest).get();

        assertTrue(result.succeeded());
        verify(patronNoticeService, times(2)).acceptNoticeEvent(any());
        verify(eventPublisher, times(2)).publishHoldRequestedEvent(any());
    }

    @Test
    @DisplayName("Should handle empty loan list gracefully")
    void shouldHandleEmptyLoanListGracefully() throws Exception {
        UUID instanceId = UUID.randomUUID();
        Request titleLevelHoldRequest = Request.from(new RequestBuilder()
            .hold()
            .withId(UUID.randomUUID())
            .withInstanceId(instanceId)
            .withRequestLevel("Title")
            .withRequesterId(UUID.randomUUID())
            .create())
            .withTlrSettingsConfiguration(
                TlrSettingsConfiguration.from(new JsonObject()
                    .put("titleLevelRequestsFeatureEnabled", true))
            );

        when(itemByInstanceIdFinder.getItemsByInstanceId(instanceId, false))
            .thenReturn(CompletableFuture.completedFuture(
                Result.succeeded(Collections.emptyList())
            ));

        Result<Void> result = holdNoticeSender.sendHoldNoticeIfNeeded(titleLevelHoldRequest).get();

        assertTrue(result.succeeded());
        verify(patronNoticeService, never()).acceptNoticeEvent(any());
        verify(eventPublisher, never()).publishHoldRequestedEvent(any());
    }

    // ============================================================================
    // ERROR HANDLING TESTS
    // ============================================================================

    @Test
    @DisplayName("Should handle query failure in first hold check gracefully")
    void shouldHandleQueryFailureGracefully() throws Exception {
        when(requestRepository.findOpenRequestsByItemIds(Collections.singletonList(item.getItemId())))
            .thenReturn(CompletableFuture.completedFuture(Result.failed(null)));
        when(loanRepository.fetchLatestPatronInfoAddedComment(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(openLoan)));

        Result<Void> result = holdNoticeSender.sendHoldNoticeIfNeeded(holdRequest).get();

        assertTrue(result.succeeded());
        verify(patronNoticeService, never()).acceptNoticeEvent(any());
        verify(eventPublisher, never()).publishHoldRequestedEvent(any());
    }

    @Test
    @DisplayName("Should handle null loan gracefully")
    void shouldHandleNullLoanGracefully() throws Exception {
        Request requestWithNullLoan = holdRequest.withLoan(null);
        // No need to mock fetchLatestPatronInfoAddedComment - it won't be called with null loan

        Result<Void> result = holdNoticeSender.sendHoldNoticeIfNeeded(requestWithNullLoan).get();

        assertTrue(result.succeeded());
        verify(patronNoticeService, never()).acceptNoticeEvent(any());
        verify(eventPublisher, never()).publishHoldRequestedEvent(any());
    }

    @Test
    @DisplayName("Should handle loan with null item gracefully")
    void shouldHandleLoanWithNullItemGracefully() throws Exception {
        Loan loanWithNullItem = Loan.from(new LoanBuilder()
            .withId(UUID.randomUUID())
            .withItemId(itemId)
            .withUserId(userId)
            .open()
            .create())
            .withUser(borrower);
        // Item is null

        Request requestWithBadLoan = holdRequest.withLoan(loanWithNullItem);
        when(loanRepository.fetchLatestPatronInfoAddedComment(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(loanWithNullItem)));

        Result<Void> result = holdNoticeSender.sendHoldNoticeIfNeeded(requestWithBadLoan).get();

        assertTrue(result.succeeded());
        verify(patronNoticeService, never()).acceptNoticeEvent(any());
        verify(eventPublisher, never()).publishHoldRequestedEvent(any());
    }

    @Test
    @DisplayName("Should handle loan with null user gracefully")
    void shouldHandleLoanWithNullUserGracefully() throws Exception {
        Loan loanWithNullUser = Loan.from(new LoanBuilder()
            .withId(UUID.randomUUID())
            .withItemId(itemId)
            .withUserId(userId)
            .open()
            .create())
            .withItem(item);
        // User is null

        Request requestWithBadLoan = holdRequest.withLoan(loanWithNullUser);
        when(loanRepository.fetchLatestPatronInfoAddedComment(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(loanWithNullUser)));

        Result<Void> result = holdNoticeSender.sendHoldNoticeIfNeeded(requestWithBadLoan).get();

        assertTrue(result.succeeded());
        verify(patronNoticeService, never()).acceptNoticeEvent(any());
        verify(eventPublisher, never()).publishHoldRequestedEvent(any());
    }

    @Test
    @DisplayName("Should not send notices for second title-level hold on same instance")
    void shouldNotSendNoticesForSecondTitleLevelHold() throws Exception {
        UUID instanceId = UUID.randomUUID();
        UUID itemId1 = UUID.randomUUID();
        Item item1 = Item.from(new ItemBuilder().withId(itemId1).create());

        Request firstHoldRequest = Request.from(new RequestBuilder()
            .hold()
            .withId(UUID.randomUUID())
            .withItemId(itemId1)
            .create());

        Request secondTitleLevelHoldRequest = Request.from(new RequestBuilder()
            .hold()
            .withId(UUID.randomUUID())
            .withInstanceId(instanceId)
            .withRequestLevel("Title")
            .withRequesterId(UUID.randomUUID())
            .create())
            .withTlrSettingsConfiguration(
                TlrSettingsConfiguration.from(new JsonObject()
                    .put("titleLevelRequestsFeatureEnabled", true))
            );

        // Mock that items exist for the instance
        when(itemByInstanceIdFinder.getItemsByInstanceId(instanceId, false))
            .thenReturn(CompletableFuture.completedFuture(
                Result.succeeded(Collections.singletonList(item1))
            ));

        // Mock loan fetching (needed by new flow)
        User borrower1 = buildUser("Alice", "Smith");
        Loan loan1 = Loan.from(new LoanBuilder()
            .withId(UUID.randomUUID())
            .withItemId(itemId1)
            .withUserId(UUID.fromString(borrower1.getId()))
            .open()
            .create())
            .withItem(item1)
            .withUser(borrower1);

        when(loanRepository.findOpenLoanForItem(item1))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(loan1)));

        // Mock user enrichment (needed by new flow)
        Collection<Loan> enrichedLoans = Collections.singletonList(loan1.withUser(borrower1));
        when(userRepository.findUsersForLoans(any(Collection.class)))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(enrichedLoans)));

        // Mock that there's already a hold request (the first one)
        MultipleRecords<Request> existingHolds = new MultipleRecords<>(
            Arrays.asList(firstHoldRequest, secondTitleLevelHoldRequest), 2);
        when(requestRepository.findOpenRequestsByItemIds(Collections.singletonList(item1.getItemId())))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(existingHolds)));

        Result<Void> result = holdNoticeSender.sendHoldNoticeIfNeeded(secondTitleLevelHoldRequest).get();

        assertTrue(result.succeeded());
        // No notices should be sent for second hold
        verify(patronNoticeService, never()).acceptNoticeEvent(any());
        verify(eventPublisher, never()).publishHoldRequestedEvent(any());
    }

    @Test
    @DisplayName("Edge case: item-level hold on Copy 1, then title-level hold should notify Copy 2 borrower")
    void shouldNotifyBorrowerOfCopy2WhenCopy1HasItemLevelHoldAndTitleLevelHoldPlaced() throws Exception {
        UUID instanceId = UUID.randomUUID();
        User alice = buildUser("Alice", "Smith");
        User bob = buildUser("Bob", "Jones");
        UUID itemId1 = UUID.randomUUID(); // Copy 1
        UUID itemId2 = UUID.randomUUID(); // Copy 2

        Item item1 = Item.from(new ItemBuilder().withId(itemId1).create());
        Item item2 = Item.from(new ItemBuilder().withId(itemId2).create());

        Loan aliceLoan = Loan.from(new LoanBuilder()
            .withId(UUID.randomUUID())
            .withItemId(itemId1)
            .withUserId(UUID.fromString(alice.getId()))
            .open()
            .create())
            .withItem(item1)
            .withUser(alice);

        Loan bobLoan = Loan.from(new LoanBuilder()
            .withId(UUID.randomUUID())
            .withItemId(itemId2)
            .withUserId(UUID.fromString(bob.getId()))
            .open()
            .create())
            .withItem(item2)
            .withUser(bob);

        // item-level hold on Copy 1
        Request carolItemLevelHold = Request.from(new RequestBuilder()
            .hold()
            .withId(UUID.randomUUID())
            .withItemId(itemId1)
            .withRequesterId(UUID.randomUUID())
            .create());

        // title-level hold
        Request davidTitleLevelHold = Request.from(new RequestBuilder()
            .hold()
            .withId(UUID.randomUUID())
            .withInstanceId(instanceId)
            .withRequestLevel("Title")
            .withRequesterId(UUID.randomUUID())
            .create())
            .withTlrSettingsConfiguration(
                TlrSettingsConfiguration.from(new JsonObject()
                    .put("titleLevelRequestsFeatureEnabled", true))
            );

        when(itemByInstanceIdFinder.getItemsByInstanceId(instanceId, false))
            .thenReturn(CompletableFuture.completedFuture(
                Result.succeeded(Arrays.asList(item1, item2))
            ));

        // Mock for individual loan fetching (parallel queries)
        when(loanRepository.findOpenLoanForItem(item1))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(aliceLoan)));
        when(loanRepository.findOpenLoanForItem(item2))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(bobLoan)));

        // Mock for batch user enrichment
        Collection<Loan> enrichedLoans = Arrays.asList(
            aliceLoan.withUser(alice),
            bobLoan.withUser(bob)
        );
        when(userRepository.findUsersForLoans(any(Collection.class)))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(enrichedLoans)));

        // Mock for isFirstHoldRequestForItem checks
        // Copy 1 has hold
        MultipleRecords<Request> holdsOnItem1 = new MultipleRecords<>(
            Arrays.asList(carolItemLevelHold, davidTitleLevelHold), 2);
        when(requestRepository.findOpenRequestsByItemIds(Collections.singletonList(item1.getItemId())))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(holdsOnItem1)));

        // Copy 2 has no holds yet
        MultipleRecords<Request> holdsOnItem2 = new MultipleRecords<>(
            Collections.singletonList(davidTitleLevelHold), 1);
        when(requestRepository.findOpenRequestsByItemIds(Collections.singletonList(item2.getItemId())))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(holdsOnItem2)));

        when(proxyRelationshipValidator
            .hasActiveProxyRelationshipWithNotificationsSentToProxy(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(false)));

        when(eventPublisher.publishHoldRequestedEvent(any(Loan.class)))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(null)));

        when(patronNoticeService.acceptNoticeEvent(any()))
            .thenReturn(CompletableFuture.completedFuture(Result.succeeded(null)));

        Result<Void> result = holdNoticeSender.sendHoldNoticeIfNeeded(davidTitleLevelHold).get();

        assertTrue(result.succeeded());

        verify(patronNoticeService, times(1)).acceptNoticeEvent(any());
        verify(eventPublisher, times(1)).publishHoldRequestedEvent(any());
    }
}
