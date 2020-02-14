package org.folio.circulation.domain;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicyRepository;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;
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
  private static final String FEE_FINE_TYPE = "Overdue fine";
  private static final String TITLE = "title";
  private static final String BARCODE = "barcode";
  private static final String CALL_NUMBER = "call-number";

  private static final Map<String, Integer> MINUTES_IN_INTERVAL = new HashMap<>();
  static {
    MINUTES_IN_INTERVAL.put("minute", 1);
    MINUTES_IN_INTERVAL.put("hour", 60);
    MINUTES_IN_INTERVAL.put("day", 1440);
    MINUTES_IN_INTERVAL.put("week", 10080);
    MINUTES_IN_INTERVAL.put("month", 44640);
    MINUTES_IN_INTERVAL.put("year", 525600);
  }

  private OverdueFineCalculatorService overdueFineCalculatorService;
  private AccountRepository accountRepository;
  private OverdueFinePolicyRepository overdueFinePolicyRepository;
  private OverduePeriodCalculatorService overduePeriodCalculatorService;
  private ItemRepository itemRepository;
  private FeeFineOwnerRepository feeFineOwnerRepository;
  private FeeFineRepository feeFineRepository;
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
    Boolean dueDateChangedByRecall, Double overdueFine, String overdueFineInterval,
    Double maxOverdueFine, Double overdueRecallFine, String overdueRecallFineInterval,
    Double maxOverdueRecallFine, Integer periodCalculatorResult, Double correctOverdueFine) {
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
          {false, 1.0, interval, 10.0, 1.0, interval, 10.0, 5 * minutesInInterval, 5.0});
        parameters.add(new Object[]
          {false, 1.0, interval, 10.0, 1.0, interval, 10.0, 15 * minutesInInterval, 10.0});
        parameters.add(new Object[]
          {true, 1.0, interval, 10.0, 1.0, interval, 10.0, 5 * minutesInInterval, 5.0});
        parameters.add(new Object[]
          {true, 1.0, interval, 10.0, 1.0, interval, 10.0, 15 * minutesInInterval, 10.0});
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

    overdueFineCalculatorService = new OverdueFineCalculatorService(
      overdueFinePolicyRepository, accountRepository, itemRepository,
      feeFineOwnerRepository, feeFineRepository, overduePeriodCalculatorService);
  }

  @Test
  public void shouldNotCreateFeeFineRecordWhenLoanIsNotProvided() {
    CheckInProcessRecords records = mock(CheckInProcessRecords.class);
    when(records.getLoan()).thenReturn(null);

    overdueFineCalculatorService.calculateOverdueFine(records);

    verifyNoInteractions(accountRepository);
  }

  @Test
  public void shouldCreateFeeFineRecordWhenAmountIsPositive()
    throws ExecutionException, InterruptedException {
    Loan loan = createLoan();

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(periodCalculatorResult)));
    when(itemRepository.fetchItemRelatedRecords(any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(createItem())));
    doReturn(CompletableFuture.completedFuture(Result.succeeded(createFeeFineOwner())))
      .when(feeFineOwnerRepository).getFeeFineOwner(SERVICE_POINT_ID.toString());
    doReturn(CompletableFuture.completedFuture(Result.succeeded(createFeeFine())))
      .when(feeFineRepository).getOverdueFine(eq(FEE_FINE_OWNER_ID.toString()));

    CheckInProcessRecords records = new CheckInProcessRecords(
      CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
      .withLoan(loan);

    overdueFineCalculatorService.calculateOverdueFine(records).get();
    verify(accountRepository, times(1)).create(any());

    ArgumentCaptor<Account> argument = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).create(argument.capture());
    assertEquals(FEE_FINE_OWNER_ID.toString(), argument.getValue().getOwnerId());
    assertEquals(FEE_FINE_ID.toString(), argument.getValue().getFeeFineId());
    assertEquals(correctOverdueFine, argument.getValue().getAmount());
    assertEquals(correctOverdueFine, argument.getValue().getRemaining());
    assertEquals(FEE_FINE_TYPE, argument.getValue().getFeeFineType());
    assertEquals(FEE_FINE_OWNER, argument.getValue().getFeeFineOwner());
    assertEquals(TITLE, argument.getValue().getTitle());
    assertEquals(BARCODE, argument.getValue().getBarcode());
    assertEquals(CALL_NUMBER, argument.getValue().getCallNumber());
    assertEquals(SERVICE_POINT_ID.toString(), argument.getValue().getLocation());
    assertEquals(ITEM_MATERIAL_TYPE_ID.toString(), argument.getValue().getMaterialTypeId());
    assertEquals(LOAN_ID.toString(), argument.getValue().getLoanId());
    assertEquals(LOAN_USER_ID.toString(), argument.getValue().getUserId());
    assertEquals(ITEM_ID.toString(), argument.getValue().getItemId());
  }

  @Test
  public void shouldNotCreateFeeFineRecordWhenAmountIsNotPositive()
    throws ExecutionException, InterruptedException {
    Loan loan = createLoan();

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(0)));

    CheckInProcessRecords records = new CheckInProcessRecords(
      CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
      .withLoan(loan);

    overdueFineCalculatorService.calculateOverdueFine(records).get();
    verifyNoInteractions(accountRepository);
  }

  @Test
  public void shouldNotCreateFeeFineRecordWhenItemDoesNotExist()
    throws ExecutionException, InterruptedException {
    Loan loan = createLoan();

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(5)));
    when(itemRepository.fetchItemRelatedRecords(any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(null)));

    CheckInProcessRecords records = new CheckInProcessRecords(
      CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
      .withLoan(loan);

    overdueFineCalculatorService.calculateOverdueFine(records).get();
    verifyNoInteractions(feeFineOwnerRepository);
    verifyNoInteractions(feeFineRepository);
    verifyNoInteractions(accountRepository);
  }

  @Test
  public void shouldNotCreateFeeFineRecordWhenOwnerDoesNotExist()
    throws ExecutionException, InterruptedException {
    Loan loan = createLoan();

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(5)));
    when(itemRepository.fetchItemRelatedRecords(any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(createItem())));
    doReturn(CompletableFuture.completedFuture(Result.succeeded(null)))
      .when(feeFineOwnerRepository).getFeeFineOwner(SERVICE_POINT_ID.toString());

    CheckInProcessRecords records = new CheckInProcessRecords(
      CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
      .withLoan(loan);

    overdueFineCalculatorService.calculateOverdueFine(records).get();
    verifyNoInteractions(feeFineRepository);
    verifyNoInteractions(accountRepository);
  }

  @Test
  public void shouldCreateFeeFineTypeWhenFeeFineTypeDoesNotExist()
    throws ExecutionException, InterruptedException {
    Loan loan = createLoan();

    when(overdueFinePolicyRepository.findOverdueFinePolicyForLoan(any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(loan)));
    when(overduePeriodCalculatorService.getMinutes(any(), any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(periodCalculatorResult)));
    when(itemRepository.fetchItemRelatedRecords(any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(createItem())));
    doReturn(CompletableFuture.completedFuture(Result.succeeded(createFeeFineOwner())))
      .when(feeFineOwnerRepository).getFeeFineOwner(SERVICE_POINT_ID.toString());
    doReturn(CompletableFuture.completedFuture(Result.succeeded(null)))
      .when(feeFineRepository).getOverdueFine(eq(FEE_FINE_OWNER_ID.toString()));
    doReturn(CompletableFuture.completedFuture(Result.succeeded(createFeeFine())))
      .when(feeFineRepository).create(any());

    CheckInProcessRecords records = new CheckInProcessRecords(
      CheckInByBarcodeRequest.from(createCheckInByBarcodeRequest()).value())
      .withLoan(loan);

    overdueFineCalculatorService.calculateOverdueFine(records).get();

    verify(feeFineRepository, times(1)).create(any());

    ArgumentCaptor<FeeFine> argument = ArgumentCaptor.forClass(FeeFine.class);
    verify(feeFineRepository).create(argument.capture());
    assertEquals(FEE_FINE_TYPE, argument.getValue().getFeeFineType());

    verify(accountRepository, times(1)).create(any());
  }

  private JsonObject createCheckInByBarcodeRequest() {
    return new CheckInByBarcodeRequestBuilder()
      .withItemBarcode(BARCODE)
      .on(new DateTime(2020, 1, 1, 0, 0, 0, DateTimeZone.UTC))
      .at(UUID.randomUUID().toString())
      .create();
  }

  private Loan createLoan() {
    return new LoanBuilder()
      .withId(LOAN_ID)
      .withUserId(LOAN_USER_ID)
      .withDueDate(new DateTime(2020, 1, 1, 0, 0, 0, DateTimeZone.UTC))
      .withDueDateChangedByRecall(dueDateChangedByRecall)
      .asDomainObject()
      .withOverdueFinePolicy(createOverdueFinePolicy());
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

    return OverdueFinePolicy.from(overdueFinePolicy);
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
}
