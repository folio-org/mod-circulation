package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicyRepository;
import org.folio.circulation.domain.representations.AccountStorageRepresentation;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.domain.representations.FeeFineActionStorageRepresentation;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.FeeFineBuilder;
import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.InstanceBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LocationBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import api.support.builders.UserBuilder;
import io.vertx.core.json.JsonObject;

@RunWith(value = Parameterized.class)
public class OverdueFineCalculatorServiceTest {
  private static final UUID LOAN_ID = UUID.randomUUID();
  private static final UUID LOAN_USER_ID = UUID.randomUUID();
  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID ITEM_MATERIAL_TYPE_ID = UUID.randomUUID();
  private static final UUID FEE_FINE_OWNER_ID = UUID.randomUUID();
  private static final String FEE_FINE_OWNER = "fee-fine-owner";
  private static final UUID SERVICE_POINT_ID = UUID.randomUUID();
  private static final UUID FEE_FINE_ID = UUID.randomUUID();
  private static final UUID ACCOUNT_ID = UUID.randomUUID();
  private static final String FEE_FINE_TYPE = "Overdue fine";
  private static final String TITLE = "title";
  private static final String BARCODE = "barcode";
  private static final String CALL_NUMBER = "call-number";
  private static final User LOGGED_IN_USER =
    new User(new UserBuilder().withUsername("admin").create());

  private static final Map<String, Integer> MINUTES_IN_INTERVAL = new HashMap<>();
  static {
    MINUTES_IN_INTERVAL.put("minute", 1);
    MINUTES_IN_INTERVAL.put("hour", 60);
    MINUTES_IN_INTERVAL.put("day", 1440);
    MINUTES_IN_INTERVAL.put("week", 10080);
    MINUTES_IN_INTERVAL.put("month", 44640);
    MINUTES_IN_INTERVAL.put("year", 525600);
  }

  private WebContext context;
  private OverdueFineCalculatorService overdueFineCalculatorService;
  private AccountRepository accountRepository;
  private OverdueFinePolicyRepository overdueFinePolicyRepository;
  private OverduePeriodCalculatorService overduePeriodCalculatorService;
  private ItemRepository itemRepository;
  private FeeFineOwnerRepository feeFineOwnerRepository;
  private FeeFineRepository feeFineRepository;
  private UserRepository userRepository;
  private FeeFineActionRepository feeFineActionRepository;
  private Boolean renewal;
  private Boolean dueDateChangedByRecall;
  private Double overdueFine;
  private String overdueFineInterval;
  private Double maxOverdueFine;
  private Double overdueRecallFine;
  private String overdueRecallFineInterval;
  private Double maxOverdueRecallFine;
  private Integer periodCalculatorResult;
  private Double correctOverdueFine;

  public OverdueFineCalculatorServiceTest(
    Boolean renewal, Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
    Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
    Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine) {
    this.renewal = renewal;
    this.dueDateChangedByRecall = dueDateChangedByRecall;
    this.overdueFine = overdueFine;
    this.overdueFineInterval = overdueFineInterval;
    this.maxOverdueFine = maxOverdueFine;
    this.overdueRecallFine = overdueRecallFine;
    this.overdueRecallFineInterval = overdueRecallFineInterval;
    this.maxOverdueRecallFine = maxOverdueRecallFine;
    this.periodCalculatorResult = periodCalculatorResult;
    this.correctOverdueFine = correctOverdueFine;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> testParameters() {
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

  @Before
  public void setUp() {
    overdueFinePolicyRepository = mock(OverdueFinePolicyRepository.class);
    accountRepository = mock(AccountRepository.class);
    itemRepository = mock(ItemRepository.class);
    feeFineOwnerRepository = mock(FeeFineOwnerRepository.class);
    feeFineRepository = mock(FeeFineRepository.class);
    overduePeriodCalculatorService = mock(OverduePeriodCalculatorService.class);
    userRepository = mock(UserRepository.class);
    feeFineActionRepository = mock(FeeFineActionRepository.class);

    overdueFineCalculatorService = new OverdueFineCalculatorService(
      new OverdueFineCalculatorService.Repos(
        overdueFinePolicyRepository, accountRepository, itemRepository,
        feeFineOwnerRepository, feeFineRepository, userRepository, feeFineActionRepository),
      overduePeriodCalculatorService);

    context = mock(WebContext.class);
    when(context.getUserId()).thenReturn(LOGGED_IN_USER.getId());
    when(userRepository.getUser(any(String.class))).thenReturn(
      completedFuture(succeeded(LOGGED_IN_USER)));
  }

  @Test
  public void shouldNotCreateFeeFineRecordWhenLoanIsNotProvided()
    throws ExecutionException, InterruptedException {

    if (renewal) {
      LoanAndRelatedRecords records = mock(LoanAndRelatedRecords.class);
      when(records.getLoan()).thenReturn(null);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }
    else {
      CheckInProcessRecords records = mock(CheckInProcessRecords.class);
      when(records.getLoan()).thenReturn(null);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }

    verifyNoInteractions(accountRepository);
    verifyNoInteractions(feeFineActionRepository);
  }

  @Test
  public void shouldCreateFeeFineRecordWhenAmountIsPositive()
    throws ExecutionException, InterruptedException {
    Loan loan = createLoan();

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any()))
      .thenReturn(completedFuture(succeeded(periodCalculatorResult)));
    when(itemRepository.fetchItemRelatedRecords(       any()))
      .thenReturn(completedFuture(succeeded(createItem())));
    when(feeFineOwnerRepository.getFeeFineOwner(SERVICE_POINT_ID.toString()))
      .thenReturn(completedFuture(succeeded(createFeeFineOwner())));
    when(feeFineRepository.getFeeFine(FEE_FINE_TYPE, true))
      .thenReturn(completedFuture(succeeded(createFeeFine())));
    when(accountRepository.create(any())).thenReturn(completedFuture(succeeded(createAccount())));

    if (renewal) {
      LoanAndRelatedRecords records = new LoanAndRelatedRecords(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }
    else {
      CheckInProcessRecords records = new CheckInProcessRecords(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }

    verify(accountRepository, times(1)).create(any());

    ArgumentCaptor<AccountStorageRepresentation> account =
      ArgumentCaptor.forClass(AccountStorageRepresentation.class);

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
    assertEquals(SERVICE_POINT_ID.toString(), account.getValue().getString("location"));
    assertEquals(ITEM_MATERIAL_TYPE_ID.toString(), account.getValue().getString("materialTypeId"));
    assertEquals(LOAN_ID.toString(), account.getValue().getString("loanId"));
    assertEquals(LOAN_USER_ID.toString(), account.getValue().getString("userId"));
    assertEquals(ITEM_ID.toString(), account.getValue().getString("itemId"));

    ArgumentCaptor<FeeFineActionStorageRepresentation> feeFineAction =
      ArgumentCaptor.forClass(FeeFineActionStorageRepresentation.class);

    verify(feeFineActionRepository).create(feeFineAction.capture());

    assertEquals(LOAN_USER_ID.toString(), feeFineAction.getValue().getString("userId"));
    assertEquals(ACCOUNT_ID.toString(), feeFineAction.getValue().getString("accountId"));
    assertEquals(String.format("%s, %s", LOGGED_IN_USER.getLastName(),
      LOGGED_IN_USER.getFirstName()), feeFineAction.getValue().getString("source"));
    assertEquals(FEE_FINE_OWNER, feeFineAction.getValue().getString("createdAt"));
    assertEquals("-", feeFineAction.getValue().getString("transactionInformation"));
    assertEquals(correctOverdueFine, feeFineAction.getValue().getDouble("balance"));
    assertEquals(correctOverdueFine, feeFineAction.getValue().getDouble("amountAction"));
    assertEquals(FEE_FINE_TYPE, feeFineAction.getValue().getString("typeAction"));
  }

  @Test
  public void shouldNotCreateFeeFineRecordWhenAmountIsNotPositive()
    throws ExecutionException, InterruptedException {
    Loan loan = createLoan();

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any()))
      .thenReturn(completedFuture(succeeded(0)));

    if (renewal) {
      LoanAndRelatedRecords records = new LoanAndRelatedRecords(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }
    else {
      CheckInProcessRecords records = new CheckInProcessRecords(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }

    verifyNoInteractions(accountRepository);
    verifyNoInteractions(feeFineActionRepository);
  }

  @Test
  public void shouldNotCreateFeeFineRecordWhenItemDoesNotExist()
    throws ExecutionException, InterruptedException {
    Loan loan = createLoan();

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any()))
      .thenReturn(completedFuture(succeeded(periodCalculatorResult)));
    when(itemRepository.fetchItemRelatedRecords(any()))
      .thenReturn(completedFuture(succeeded(null)));
    when(feeFineOwnerRepository.getFeeFineOwner(SERVICE_POINT_ID.toString()))
      .thenReturn(completedFuture(succeeded(createFeeFineOwner())));
    when(feeFineRepository.getFeeFine(FEE_FINE_TYPE, true))
      .thenReturn(completedFuture(succeeded(createFeeFine())));

    if (renewal) {
      LoanAndRelatedRecords records = new LoanAndRelatedRecords(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }
    else {
      CheckInProcessRecords records = new CheckInProcessRecords(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }

    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(accountRepository);
    verifyNoInteractions(feeFineActionRepository);
  }

  @Test
  public void shouldNotCreateFeeFineRecordWhenOwnerDoesNotExist()
    throws ExecutionException, InterruptedException {
    Loan loan = createLoan();

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any()))
      .thenReturn(completedFuture(succeeded(5)));
    when(itemRepository.fetchItemRelatedRecords(any()))
      .thenReturn(completedFuture(succeeded(createItem())));
    when(feeFineRepository.getFeeFine(FEE_FINE_TYPE, true))
      .thenReturn(completedFuture(succeeded(createFeeFine())));
    when(feeFineOwnerRepository.getFeeFineOwner(SERVICE_POINT_ID.toString()))
      .thenReturn(completedFuture(succeeded(null)));

    if (renewal) {
      LoanAndRelatedRecords records = new LoanAndRelatedRecords(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }
    else {
      CheckInProcessRecords records = new CheckInProcessRecords(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }

    verifyNoInteractions(accountRepository);
  }

  @Test
  public void shouldNotCreateFeeFineRecordWhenFeeFineDoesNotExist()
    throws ExecutionException, InterruptedException {
    Loan loan = createLoan();

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any()))
      .thenReturn(completedFuture(succeeded(periodCalculatorResult)));
    when(itemRepository.fetchItemRelatedRecords(any()))
      .thenReturn(completedFuture(succeeded(createItem())));
    when(feeFineRepository.getFeeFine(FEE_FINE_TYPE, true))
      .thenReturn(completedFuture(succeeded(null)));
    when(feeFineOwnerRepository.getFeeFineOwner(SERVICE_POINT_ID.toString()))
      .thenReturn(completedFuture(succeeded(createFeeFineOwner())));

    if (renewal) {
      LoanAndRelatedRecords records = new LoanAndRelatedRecords(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }
    else {
      CheckInProcessRecords records = new CheckInProcessRecords(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }

    verifyNoInteractions(itemRepository);
    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(accountRepository);
  }

  @Test
  public void shouldNotCreateFeeFineRecordWhenOverduePolicyDoesNotExist()
    throws ExecutionException, InterruptedException {

    final Loan loan = createLoan().withOverdueFinePolicy(OverdueFinePolicy.unknown(null));

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));

    if (renewal) {
      LoanAndRelatedRecords records = new LoanAndRelatedRecords(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }
    else {
      CheckInProcessRecords records = new CheckInProcessRecords(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }

    verifyNoInteractions(feeFineRepository);
    verifyNoInteractions(itemRepository);
    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(accountRepository);
  }

  @Test
  public void shouldNotCreateFeeFineRecordWhenLoanIsNull()
    throws ExecutionException, InterruptedException {

    if (renewal) {
      LoanAndRelatedRecords records = new LoanAndRelatedRecords(null);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }
    else {
      CheckInProcessRecords records = new CheckInProcessRecords(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(null);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }

    verifyNoInteractions(feeFineRepository);
    verifyNoInteractions(overdueFinePolicyRepository);
    verifyNoInteractions(itemRepository);
    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(accountRepository);
  }

  @Test
  public void shouldNotCreateFeeFineWhenLoanIsNotOverdue()
    throws ExecutionException, InterruptedException {

    DateTime dueDateInFuture = DateTime.now(DateTimeZone.UTC).plusDays(1);
    final Loan loan = createLoan().changeDueDate(dueDateInFuture);

    if (renewal) {
      LoanAndRelatedRecords records = new LoanAndRelatedRecords(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }
    else {
      CheckInProcessRecords records = new CheckInProcessRecords(
        CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
        .withLoan(loan);

      overdueFineCalculatorService.createOverdueFineIfNecessary(records, context).get();
    }

    verifyNoInteractions(feeFineRepository);
    verifyNoInteractions(overdueFinePolicyRepository);
    verifyNoInteractions(itemRepository);
    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(accountRepository);
  }

  @Test
  public void shouldNotCreateFeeFineForRenewalWhenShouldForgiveOverdueFine()
    throws ExecutionException, InterruptedException {

    JsonObject overdueFinePolicyJson = createOverdueFinePolicyJson()
      .put("forgiveOverdueFine", true);
    OverdueFinePolicy overdueFinePolicy = createOverdueFinePolicy(overdueFinePolicyJson);
    final Loan loan = createLoan()
      .withOverdueFinePolicy(overdueFinePolicy);

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(completedFuture(succeeded(loan)));

    LoanAndRelatedRecords renewalRecords = new LoanAndRelatedRecords(loan);
    overdueFineCalculatorService.createOverdueFineIfNecessary(renewalRecords, context).get();

    verifyNoInteractions(feeFineRepository);
    verifyNoInteractions(itemRepository);
    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(accountRepository);
  }

  private JsonObject createCheckInByBarcodeRequest() {
    return new CheckInByBarcodeRequestBuilder()
      .withItemBarcode(BARCODE)
      .on(new DateTime(2020, 1, 1, 0, 0, 0, DateTimeZone.UTC))
      .at(UUID.randomUUID().toString())
      .create();
  }

  private Loan createLoan() {
    return createLoan(createOverdueFinePolicy());
  }

  private Loan createLoan(OverdueFinePolicy overdueFinePolicy) {
    return new LoanBuilder()
      .withId(LOAN_ID)
      .withUserId(LOAN_USER_ID)
      .withDueDate(new DateTime(2020, 1, 1, 0, 0, 0, DateTimeZone.UTC))
      .withStatus("Closed")
      .withReturnDate(new DateTime(2020, 3, 1, 0, 0, 0, DateTimeZone.UTC))
      .withDueDateChangedByRecall(dueDateChangedByRecall)
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
    return Item.from(item).withLocation(
      Location.from(new LocationBuilder().withPrimaryServicePoint(SERVICE_POINT_ID).create()))
      .withInstance(new InstanceBuilder(TITLE, UUID.randomUUID()).create());
  }

  private OverdueFinePolicy createOverdueFinePolicy() {
    return OverdueFinePolicy.from(createOverdueFinePolicyJson());
  }

  private OverdueFinePolicy createOverdueFinePolicy(JsonObject policyJson) {
    return OverdueFinePolicy.from(policyJson);
  }

  private JsonObject createOverdueFinePolicyJson() {
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

  private Account createAccount() {
    return new Account(ACCOUNT_ID.toString(),
      new AccountRelatedRecordsInfo(
        new AccountFeeFineOwnerInfo(FEE_FINE_OWNER_ID.toString(), FEE_FINE_OWNER),
        new AccountFeeFineTypeInfo(FEE_FINE_ID.toString(), FEE_FINE_TYPE),
        new AccountLoanInfo(LOAN_ID.toString(), LOAN_USER_ID.toString()),
        new AccountItemInfo(ITEM_ID.toString(), TITLE, BARCODE, CALL_NUMBER,
          SERVICE_POINT_ID.toString(), ITEM_MATERIAL_TYPE_ID.toString())
      ),
      correctOverdueFine, correctOverdueFine, "Open", "Outstanding"
      );
  }
}
