package org.folio.circulation.domain;

import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.resources.context.RenewalContext.create;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.domain.representations.StoredAccount;
import org.folio.circulation.domain.representations.StoredFeeFineAction;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineActionRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineOwnerRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.FeeFineFacade;
import org.folio.circulation.services.feefine.FeeFineService;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.FeeFineBuilder;
import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.FeefineActionsBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import api.support.builders.UserBuilder;
import io.vertx.core.json.JsonObject;

class OverdueFineServiceTest {
  private static final UUID LOAN_ID = UUID.randomUUID();
  private static final UUID LOAN_USER_ID = UUID.randomUUID();
  private static final ZonedDateTime DUE_DATE = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, UTC);
  private static final ZonedDateTime RETURNED_DATE = ZonedDateTime.of(2020, 3, 1, 0, 0, 0, 0, UTC);
  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID ITEM_MATERIAL_TYPE_ID = UUID.randomUUID();
  private static final String ITEM_MATERIAL_TYPE = "material";
  private static final UUID FEE_FINE_OWNER_ID = UUID.randomUUID();
  private static final String FEE_FINE_OWNER = "fee-fine-owner";
  private static final String LOCATION_NAME = "location-name";
  private static final UUID SERVICE_POINT_ID = UUID.randomUUID();
  private static final UUID CHECK_IN_SERVICE_POINT_ID = UUID.randomUUID();
  private static final UUID FEE_FINE_ID = UUID.randomUUID();
  private static final UUID ACCOUNT_ID = UUID.randomUUID();
  private static final String FEE_FINE_TYPE = "Overdue fine";
  private static final String ITEM_MATERIAL_TYPE_NAME = "book";
  private static final String TITLE = "title";
  private static final String INSTANCE_HRID = "1234";
  private static final String BARCODE = "barcode";
  private static final String CALL_NUMBER = "call-number";
  private static final User LOGGED_IN_USER =
    new User(new UserBuilder().withUsername("admin").create());
  private static final String LOGGED_IN_USER_ID = LOGGED_IN_USER.getId();
  private static final String CHECK_IN_SERVICE_POINT_NAME = "test service point";

  private static final Map<String, Integer> MINUTES_IN_INTERVAL = new HashMap<>();
  static {
    MINUTES_IN_INTERVAL.put("minute", 1);
    MINUTES_IN_INTERVAL.put("hour", 60);
    MINUTES_IN_INTERVAL.put("day", 1440);
    MINUTES_IN_INTERVAL.put("week", 10080);
    MINUTES_IN_INTERVAL.put("month", 44640);
    MINUTES_IN_INTERVAL.put("year", 525600);
  }

  private OverdueFineService overdueFineService;
  private AccountRepository accountRepository;
  private OverdueFinePolicyRepository overdueFinePolicyRepository;
  private OverduePeriodCalculatorService overduePeriodCalculatorService;
  private ItemRepository itemRepository;
  private FeeFineOwnerRepository feeFineOwnerRepository;
  private FeeFineRepository feeFineRepository;
  private FeeFineActionRepository feeFineActionRepository;
  private ScheduledNoticesRepository scheduledNoticesRepository;
  private ServicePointRepository servicePointRepository;

  @BeforeEach
  public void setUp() {
    overdueFinePolicyRepository = mock(OverdueFinePolicyRepository.class);
    accountRepository = mock(AccountRepository.class);
    itemRepository = mock(ItemRepository.class);
    feeFineOwnerRepository = mock(FeeFineOwnerRepository.class);
    feeFineRepository = mock(FeeFineRepository.class);
    overduePeriodCalculatorService = mock(OverduePeriodCalculatorService.class);
    UserRepository userRepository = mock(UserRepository.class);
    feeFineActionRepository = mock(FeeFineActionRepository.class);
    scheduledNoticesRepository = mock(ScheduledNoticesRepository.class);
    servicePointRepository = mock(ServicePointRepository.class);
    FeeFineService feeFineService = mock(FeeFineService.class);
    FeeFineFacade feeFineFacade = new FeeFineFacade(accountRepository,
      feeFineActionRepository,
      userRepository,
      servicePointRepository, feeFineService);

    overdueFineService = new OverdueFineService(
      overdueFinePolicyRepository, itemRepository,
        feeFineOwnerRepository, feeFineRepository, scheduledNoticesRepository,
      overduePeriodCalculatorService, feeFineFacade);

    when(userRepository.getUser(any(String.class))).thenReturn(
      completedFuture(succeeded(LOGGED_IN_USER)));
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  void shouldNotCreateFeeFineRecordWhenLoanIsNotProvided(
      Boolean renewal, Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
      Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine)
      throws ExecutionException, InterruptedException {

    if (renewal) {
      RenewalContext context = createRenewalContext(null);

      overdueFineService.createOverdueFineIfNecessary(context).get();
    }
    else {
      CheckInContext context = mock(CheckInContext.class);
      when(context.getLoan()).thenReturn(null);

      overdueFineService.createOverdueFineIfNecessary(context, LOGGED_IN_USER_ID).get();
    }

    verifyNoInteractions(accountRepository);
    verifyNoInteractions(feeFineActionRepository);
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  void shouldCreateFeeFineRecordWhenAmountIsPositive(
      Boolean renewal, Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
      Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine)
      throws ExecutionException, InterruptedException {

    Loan loan = createLoan(overdueFine, overdueFineInterval, overdueRecallFine,
      overdueRecallFineInterval, maxOverdueFine, maxOverdueRecallFine,
      dueDateChangedByRecall);

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any(), any()))
      .thenReturn(completedFuture(succeeded(periodCalculatorResult)));
    when(itemRepository.fetchItemRelatedRecords(any()))
      .thenReturn(completedFuture(succeeded(createItem())));
    when(feeFineOwnerRepository.findOwnerForServicePoint(SERVICE_POINT_ID.toString()))
      .thenReturn(completedFuture(succeeded(createFeeFineOwner())));
    when(feeFineRepository.getFeeFine(FEE_FINE_TYPE, true))
      .thenReturn(completedFuture(succeeded(createFeeFine())));
    when(accountRepository.create(any())).thenReturn(completedFuture(
      succeeded(createAccount(correctOverdueFine))));
    when(servicePointRepository.getServicePointById(CHECK_IN_SERVICE_POINT_ID.toString()))
      .thenReturn(completedFuture(succeeded(createServicePoint())));

    if (renewal) {
      RenewalContext context = createRenewalContext(loan);

      overdueFineService.createOverdueFineIfNecessary(context).get();
    }
    else {
      CheckInContext context = new CheckInContext(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineService.createOverdueFineIfNecessary(context, LOGGED_IN_USER_ID).get();
    }

    verify(accountRepository, times(1)).create(any());

    ArgumentCaptor<StoredAccount> account =
      ArgumentCaptor.forClass(StoredAccount.class);

    verify(accountRepository).create(account.capture());

    assertEquals(FEE_FINE_OWNER_ID.toString(), account.getValue().getString("ownerId"));
    assertEquals(FEE_FINE_ID.toString(), account.getValue().getString("feeFineId"));
    assertEquals(correctOverdueFine, account.getValue().getDouble("amount"));
    assertEquals(correctOverdueFine, account.getValue().getDouble("remaining"));
    assertEquals(FEE_FINE_TYPE, account.getValue().getString("feeFineType"));
    assertEquals(FEE_FINE_OWNER, account.getValue().getString("feeFineOwner"));
    assertEquals(TITLE, account.getValue().getString("title"));
    assertEquals(BARCODE, account.getValue().getString("barcode"));
    assertEquals(CALL_NUMBER, account.getValue().getString("callNumber"));
    assertEquals(LOCATION_NAME, account.getValue().getString("location"));
    assertEquals(ITEM_MATERIAL_TYPE_NAME, account.getValue().getString("materialType"));
    assertEquals(ITEM_MATERIAL_TYPE_ID.toString(), account.getValue().getString("materialTypeId"));
    assertEquals(LOAN_ID.toString(), account.getValue().getString("loanId"));
    assertEquals(LOAN_USER_ID.toString(), account.getValue().getString("userId"));
    assertEquals(ITEM_ID.toString(), account.getValue().getString("itemId"));
    assertEquals(DUE_DATE, getDateTimeProperty(account.getValue(), "dueDate"));
    assertEquals(RETURNED_DATE, getDateTimeProperty(account.getValue(), "returnedDate"));

    assertEquals(2, account.getValue().getJsonArray("contributors").size());
    assertEquals("Contributor 1",
      account.getValue().getJsonArray("contributors").getJsonObject(0).getString("name"));
    assertEquals("Contributor 2",
      account.getValue().getJsonArray("contributors").getJsonObject(1).getString("name"));

    ArgumentCaptor<StoredFeeFineAction> feeFineAction =
      ArgumentCaptor.forClass(StoredFeeFineAction.class);

    verify(feeFineActionRepository).create(feeFineAction.capture());

    assertEquals(LOAN_USER_ID.toString(), feeFineAction.getValue().getString("userId"));
    assertEquals(ACCOUNT_ID.toString(), feeFineAction.getValue().getString("accountId"));
    assertEquals(String.format("%s, %s", LOGGED_IN_USER.getLastName(),
      LOGGED_IN_USER.getFirstName()), feeFineAction.getValue().getString("source"));
    assertEquals(CHECK_IN_SERVICE_POINT_ID.toString(), feeFineAction.getValue().getString("createdAt"));
    assertEquals("-", feeFineAction.getValue().getString("transactionInformation"));
    assertEquals(correctOverdueFine, feeFineAction.getValue().getDouble("balance"));
    assertEquals(correctOverdueFine, feeFineAction.getValue().getDouble("amountAction"));
    assertEquals(FEE_FINE_TYPE, feeFineAction.getValue().getString("typeAction"));
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  void shouldNotCreateFeeFineRecordWhenAmountIsNotPositive(
      Boolean renewal, Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
      Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine)
      throws ExecutionException, InterruptedException {
    Loan loan = createLoan(overdueFine, overdueFineInterval, overdueRecallFine,
      overdueRecallFineInterval, maxOverdueFine, maxOverdueRecallFine,
      dueDateChangedByRecall);

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any(), any()))
      .thenReturn(completedFuture(succeeded(0)));

    if (renewal) {
      RenewalContext context = createRenewalContext(loan);

      overdueFineService.createOverdueFineIfNecessary(context).get();
    }
    else {
      CheckInContext context = new CheckInContext(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineService.createOverdueFineIfNecessary(context, LOGGED_IN_USER_ID).get();
    }

    verifyNoInteractions(accountRepository);
    verifyNoInteractions(feeFineActionRepository);
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  void shouldNotCreateFeeFineRecordWhenItemDoesNotExist(
      Boolean renewal, Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
      Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine)
      throws ExecutionException, InterruptedException {
    Loan loan = createLoan(overdueFine, overdueFineInterval, overdueRecallFine,
      overdueRecallFineInterval, maxOverdueFine, maxOverdueRecallFine,
      dueDateChangedByRecall);

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any(), any()))
      .thenReturn(completedFuture(succeeded(periodCalculatorResult)));
    when(itemRepository.fetchItemRelatedRecords(any()))
      .thenReturn(completedFuture(succeeded(null)));
    when(feeFineOwnerRepository.findOwnerForServicePoint(SERVICE_POINT_ID.toString()))
      .thenReturn(completedFuture(succeeded(createFeeFineOwner())));
    when(feeFineRepository.getFeeFine(FEE_FINE_TYPE, true))
      .thenReturn(completedFuture(succeeded(createFeeFine())));

    if (renewal) {
      RenewalContext context = createRenewalContext(loan);

      overdueFineService.createOverdueFineIfNecessary(context).get();
    }
    else {
      CheckInContext context = new CheckInContext(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineService.createOverdueFineIfNecessary(context, LOGGED_IN_USER_ID).get();
    }

    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(accountRepository);
    verifyNoInteractions(feeFineActionRepository);
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  void shouldNotCreateFeeFineRecordWhenOwnerDoesNotExist(
      Boolean renewal, Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
      Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine)
      throws ExecutionException, InterruptedException {
    Loan loan = createLoan(overdueFine, overdueFineInterval, overdueRecallFine,
      overdueRecallFineInterval, maxOverdueFine, maxOverdueRecallFine,
      dueDateChangedByRecall);

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any(), any()))
      .thenReturn(completedFuture(succeeded(5)));
    when(itemRepository.fetchItemRelatedRecords(any()))
      .thenReturn(completedFuture(succeeded(createItem())));
    when(feeFineRepository.getFeeFine(FEE_FINE_TYPE, true))
      .thenReturn(completedFuture(succeeded(createFeeFine())));
    when(feeFineOwnerRepository.findOwnerForServicePoint(SERVICE_POINT_ID.toString()))
      .thenReturn(completedFuture(succeeded(null)));

    if (renewal) {
      RenewalContext context = createRenewalContext(loan);

      overdueFineService.createOverdueFineIfNecessary(context).get();
    }
    else {
      CheckInContext context = new CheckInContext(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineService.createOverdueFineIfNecessary(context, LOGGED_IN_USER_ID).get();
    }

    verifyNoInteractions(accountRepository);
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  void shouldNotCreateFeeFineRecordWhenFeeFineDoesNotExist(
      Boolean renewal, Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
      Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine)
      throws ExecutionException, InterruptedException {
    Loan loan = createLoan(overdueFine, overdueFineInterval, overdueRecallFine,
      overdueRecallFineInterval, maxOverdueFine, maxOverdueRecallFine,
      dueDateChangedByRecall);

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any(), any()))
      .thenReturn(completedFuture(succeeded(periodCalculatorResult)));
    when(itemRepository.fetchItemRelatedRecords(any()))
      .thenReturn(completedFuture(succeeded(createItem())));
    when(feeFineRepository.getFeeFine(FEE_FINE_TYPE, true))
      .thenReturn(completedFuture(succeeded(null)));
    when(feeFineOwnerRepository.findOwnerForServicePoint(SERVICE_POINT_ID.toString()))
      .thenReturn(completedFuture(succeeded(createFeeFineOwner())));

    if (renewal) {
      RenewalContext context = createRenewalContext(loan);

      overdueFineService.createOverdueFineIfNecessary(context).get();
    }
    else {
      CheckInContext context = new CheckInContext(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineService.createOverdueFineIfNecessary(context, LOGGED_IN_USER_ID).get();
    }

    verifyNoInteractions(itemRepository);
    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(accountRepository);
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  void shouldNotCreateFeeFineRecordWhenOverduePolicyDoesNotExist(
      Boolean renewal, Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
      Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine)
      throws ExecutionException, InterruptedException {

    Loan loan = createLoan(overdueFine, overdueFineInterval, overdueRecallFine,
      overdueRecallFineInterval, maxOverdueFine, maxOverdueRecallFine,
      dueDateChangedByRecall)
      .withOverdueFinePolicy(OverdueFinePolicy.unknown(null));

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));

    if (renewal) {
      RenewalContext context = createRenewalContext(loan);

      overdueFineService.createOverdueFineIfNecessary(context).get();
    }
    else {
      CheckInContext context = new CheckInContext(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineService.createOverdueFineIfNecessary(context, LOGGED_IN_USER_ID).get();
    }

    verifyNoInteractions(feeFineRepository);
    verifyNoInteractions(itemRepository);
    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(accountRepository);
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  void shouldNotCreateFeeFineRecordWhenLoanIsNull(
      Boolean renewal, Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
      Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine)
      throws ExecutionException, InterruptedException {

    if (renewal) {
      RenewalContext context = createRenewalContext(null);

      overdueFineService.createOverdueFineIfNecessary(context).get();
    }
    else {
      CheckInContext context = new CheckInContext(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(null);

      overdueFineService.createOverdueFineIfNecessary(context, LOGGED_IN_USER_ID).get();
    }

    verifyNoInteractions(feeFineRepository);
    verifyNoInteractions(overdueFinePolicyRepository);
    verifyNoInteractions(itemRepository);
    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(accountRepository);
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  void shouldNotCreateFeeFineWhenLoanIsNotOverdue(
      Boolean renewal, Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
      Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine)
      throws ExecutionException, InterruptedException {

    ZonedDateTime dueDateInFuture = ClockUtil.getZonedDateTime().plusDays(1);
    Loan loan = createLoan(overdueFine, overdueFineInterval, overdueRecallFine,
      overdueRecallFineInterval, maxOverdueFine, maxOverdueRecallFine,
      dueDateChangedByRecall).changeDueDate(dueDateInFuture);

    if (renewal) {
      RenewalContext context = createRenewalContext(loan);

      overdueFineService.createOverdueFineIfNecessary(context).get();
    }
    else {
      CheckInContext context = new CheckInContext(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineService.createOverdueFineIfNecessary(context, LOGGED_IN_USER_ID).get();
    }

    verifyNoInteractions(feeFineRepository);
    verifyNoInteractions(overdueFinePolicyRepository);
    verifyNoInteractions(itemRepository);
    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(accountRepository);
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  void shouldNotCreateFeeFineForRenewalWhenShouldForgiveOverdueFine(
      Boolean renewal, Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
      Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine)
      throws ExecutionException, InterruptedException {

    JsonObject overdueFinePolicyJson = createOverdueFinePolicyJson(
      overdueFine, overdueFineInterval, overdueRecallFine,
      overdueRecallFineInterval, maxOverdueFine, maxOverdueRecallFine)
      .put("forgiveOverdueFine", true);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(overdueFinePolicyJson);
    Loan loan = createLoan(overdueFine, overdueFineInterval, overdueRecallFine,
      overdueRecallFineInterval, maxOverdueFine, maxOverdueRecallFine,
      dueDateChangedByRecall).withOverdueFinePolicy(overdueFinePolicy);

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));

    RenewalContext renewalRecords = createRenewalContext(loan);
    overdueFineService.createOverdueFineIfNecessary(renewalRecords).get();

    verifyNoInteractions(feeFineRepository);
    verifyNoInteractions(itemRepository);
    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(accountRepository);
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  void shouldDeleteOverdueNoticesWhenFeeFineRecordCreated(
      Boolean renewal, Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
      Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine)
      throws ExecutionException, InterruptedException {
    Loan loan = createLoan(overdueFine, overdueFineInterval, overdueRecallFine,
      overdueRecallFineInterval, maxOverdueFine, maxOverdueRecallFine,
      dueDateChangedByRecall);

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any(), any()))
      .thenReturn(completedFuture(succeeded(periodCalculatorResult)));
    when(itemRepository.fetchItemRelatedRecords(any()))
      .thenReturn(completedFuture(succeeded(createItem())));
    when(feeFineOwnerRepository.findOwnerForServicePoint(SERVICE_POINT_ID.toString()))
      .thenReturn(completedFuture(succeeded(createFeeFineOwner())));
    when(feeFineRepository.getFeeFine(FEE_FINE_TYPE, true))
      .thenReturn(completedFuture(succeeded(createFeeFine())));
    when(accountRepository.create(any())).thenReturn(completedFuture(succeeded(createAccount(correctOverdueFine))));
    when(feeFineActionRepository.create(any()))
      .thenReturn(completedFuture(succeeded(createFeeFineAction())));
    when(scheduledNoticesRepository.deleteOverdueNotices(any()))
      .thenReturn(completedFuture(succeeded(null)));
    when(servicePointRepository.getServicePointById(CHECK_IN_SERVICE_POINT_ID.toString()))
      .thenReturn(completedFuture(succeeded(createServicePoint())));

    if (renewal) {
      RenewalContext context = createRenewalContext(loan);

      overdueFineService.createOverdueFineIfNecessary(context).get();
    }
    else {
      CheckInContext context = new CheckInContext(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineService.createOverdueFineIfNecessary(context, LOGGED_IN_USER_ID).get();
    }

    verify(scheduledNoticesRepository, times(1)).deleteOverdueNotices(any());
  }

  private RenewalContext createRenewalContext(Loan loan) {
    return create(loan, new JsonObject(), LOGGED_IN_USER_ID);
  }

  private JsonObject createCheckInByBarcodeRequest() {
    return new CheckInByBarcodeRequestBuilder()
      .withItemBarcode(BARCODE)
      .on(DUE_DATE)
      .at(UUID.randomUUID().toString())
      .create();
  }

  private Loan createLoan(
      Double overdueFine, String overdueFineInterval,
      Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueFine, Double maxOverdueRecallFine,
      Boolean dueDateChangedByRecall) {

    return createLoan(createOverdueFinePolicy(overdueFine,
      overdueFineInterval, overdueRecallFine, overdueRecallFineInterval,
      maxOverdueFine, maxOverdueRecallFine), dueDateChangedByRecall);
  }


  private Loan createLoan(OverdueFinePolicy overdueFinePolicy, Boolean dueDateChangedByRecall) {
    return new LoanBuilder()
      .withId(LOAN_ID)
      .withUserId(LOAN_USER_ID)
      .withDueDate(DUE_DATE)
      .withStatus("Closed")
      .withReturnDate(RETURNED_DATE)
      .withDueDateChangedByRecall(dueDateChangedByRecall)
      .withCheckinServicePointId(CHECK_IN_SERVICE_POINT_ID)
      .asDomainObject()
      .withOverdueFinePolicy(overdueFinePolicy);
  }

  private Item createItem() {
    JsonObject item = new ItemBuilder()
      .withId(ITEM_ID)
      .withBarcode(BARCODE)
      .withMaterialType(ITEM_MATERIAL_TYPE_ID)
      .create();
    item.put("effectiveCallNumberComponents", new JsonObject().put("callNumber", CALL_NUMBER));

    final var contributors = List.of(
      new Contributor("Contributor 1", false),
      new Contributor("Contributor 2", false));

    return Item.from(item)
      .withLocation(new Location(null, LOCATION_NAME, null, null, emptyList(),
        SERVICE_POINT_ID, false, Institution.unknown(), Campus.unknown(), Library.unknown(),
        ServicePoint.unknown()))
      .withInstance(new Instance(UUID.randomUUID().toString(), INSTANCE_HRID, TITLE, emptyList(), contributors, emptyList(), emptyList(), emptyList()))
      .withMaterialType(new MaterialType(ITEM_MATERIAL_TYPE_ID.toString(), ITEM_MATERIAL_TYPE_NAME, null));
  }

  private OverdueFinePolicy createOverdueFinePolicy(
      Double overdueFine, String overdueFineInterval,
      Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueFine, Double maxOverdueRecallFine) {
    return OverdueFinePolicy.from(createOverdueFinePolicyJson(overdueFine,
      overdueFineInterval, overdueRecallFine, overdueRecallFineInterval,
      maxOverdueFine, maxOverdueRecallFine));
  }

  private OverdueFinePolicy createOverdueFinePolicy(JsonObject policyJson) {
    return OverdueFinePolicy.from(policyJson);
  }

  private JsonObject createOverdueFinePolicyJson(
      Double overdueFine, String overdueFineInterval,
      Double overdueRecallFine, String overdueRecallFineInterval,
      Double maxOverdueFine, Double maxOverdueRecallFine) {
    JsonObject overdueFineObject = new JsonObject();
    overdueFineObject.put("quantity", overdueFine);
    overdueFineObject.put("intervalId", overdueFineInterval);

    JsonObject overdueRecallFineObject = new JsonObject();
    overdueRecallFineObject.put("quantity", overdueRecallFine);
    overdueRecallFineObject.put("intervalId", overdueRecallFineInterval);

    JsonObject overdueFinePolicy = new OverdueFinePolicyBuilder()
      .withOverdueFine(overdueFineObject)
      .withOverdueRecallFine(overdueRecallFineObject)
      .create();
    overdueFinePolicy.put("maxOverdueFine", maxOverdueFine);
    overdueFinePolicy.put("maxOverdueRecallFine", maxOverdueRecallFine);
    overdueFinePolicy.put("forgiveOverdueFine", false);

    return overdueFinePolicy;
  }

  private FeeFineOwner createFeeFineOwner() {
    return FeeFineOwner.from(new FeeFineOwnerBuilder()
      .withId(FEE_FINE_OWNER_ID)
      .withOwner(FEE_FINE_OWNER)
      .create());
  }

  private FeeFine createFeeFine() {
    return FeeFine.from(new FeeFineBuilder()
      .withId(FEE_FINE_ID)
      .withFeeFineType(FEE_FINE_TYPE)
      .create());
  }

  private FeeFineAction createFeeFineAction() {
    return FeeFineAction.from(new FeefineActionsBuilder()
      .create());
  }

  private Account createAccount(Double correctOverdueFine) {
    return new Account(ACCOUNT_ID.toString(),
      new AccountRelatedRecordsInfo(
        new AccountFeeFineOwnerInfo(FEE_FINE_OWNER_ID.toString(), FEE_FINE_OWNER),
        new AccountFeeFineTypeInfo(FEE_FINE_ID.toString(), FEE_FINE_TYPE),
        new AccountLoanInfo(LOAN_ID.toString(), LOAN_USER_ID.toString(), DUE_DATE),
        new AccountItemInfo(ITEM_ID.toString(), TITLE, BARCODE, CALL_NUMBER,
          LOCATION_NAME, ITEM_MATERIAL_TYPE_ID.toString(), ITEM_MATERIAL_TYPE)
      ),
      new FeeAmount(correctOverdueFine), new FeeAmount(correctOverdueFine), "Open", "Outstanding",
      emptyList(), ClockUtil.getZonedDateTime(), null);
  }

  private ServicePoint createServicePoint() {
    return new ServicePoint(CHECK_IN_SERVICE_POINT_ID.toString(), CHECK_IN_SERVICE_POINT_NAME,
      null, false, null, null, null, null, null);
  }

  private static Collection<Object[]> testParameters() {
    List<Object[]> parameters = new ArrayList<>();
    Stream.of(new String[] {"minute", "hour", "day", "week", "month", "year"})
      .forEach(interval -> {
        int minutesInInterval = MINUTES_IN_INTERVAL.get(interval);
        parameters.add(new Object[]
          {false, false, 1.0, interval, 10.0, 1.0, interval, 10.0, 5 * minutesInInterval, 5.0});
        parameters.add(new Object[]
          {false, false, 1.0, interval, 10.0, 1.0, interval, 10.0, 15 * minutesInInterval, 10.0});
        parameters.add(new Object[]
          {false, true, 1.0, interval, 10.0, 3.0, interval, 30.0, 5 * minutesInInterval, 15.0});
        parameters.add(new Object[]
          {false, true, 1.0, interval, 10.0, 3.0, interval, 30.0, 15 * minutesInInterval, 30.0});
        parameters.add(new Object[]
          {true, false, 1.0, interval, 10.0, 1.0, interval, 10.0, 5 * minutesInInterval, 5.0});
        parameters.add(new Object[]
          {true, false, 1.0, interval, 10.0, 1.0, interval, 10.0, 15 * minutesInInterval, 10.0});
        parameters.add(new Object[]
          {true, true, 1.0, interval, 10.0, 3.0, interval, 30.0, 5 * minutesInInterval, 15.0});
        parameters.add(new Object[]
          {true, true, 1.0, interval, 10.0, 3.0, interval, 30.0, 15 * minutesInInterval, 30.0});
      });
    return parameters;
  }
}
