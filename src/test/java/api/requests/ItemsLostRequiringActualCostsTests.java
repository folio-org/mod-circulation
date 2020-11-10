package api.requests;

import static api.support.JsonCollectionAssistant.getRecordById;
import static org.folio.circulation.domain.policy.Period.minutes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.folio.circulation.support.http.client.Response;
import org.hamcrest.CoreMatchers;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.DeclareItemLostRequestBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.ResourceClient;
import api.support.matchers.ItemMatchers;
import api.support.spring.SpringApiTest;
import io.vertx.core.json.JsonObject;

public class ItemsLostRequiringActualCostsTests extends SpringApiTest {

  @Test
  public void shouldIncludeAnyLostItemsRequiringActualCostFees() {
    final ItemResource closedItem = createClosedItem();
    final ItemResource openItem = createOpenItem();

    final LostItemFeePolicyBuilder setCostPolicy = createSetCostChargeFeePolicy();
    final LostItemFeePolicyBuilder setCostAgedLostPolicy = createSetCostAgeToLostAndBilledAfterOneMinutePolicy();
    final LostItemFeePolicyBuilder actualCostPolicy = createActualCostChargeFeePolicy();
    final LostItemFeePolicyBuilder actualCostAgedLostPolicy = createActualCostAgeToLostAndBilledAfterOneMinutePolicy();

    final ItemResource declaredLostSetCostItem = createDeclaredLostItem(setCostPolicy);
    final ItemResource agedToLostSetCostItem = createAgedToLostItem(setCostAgedLostPolicy);
    final ItemResource declaredLostActualCostItem = createDeclaredLostItem(actualCostPolicy);
    final ItemResource agedToLostActualCostItem = createAgedToLostItem(actualCostAgedLostPolicy);

    final Collection<JsonObject> items = ResourceClient.forLostItemsRequiringActualCosts().getAll();

    assertThat(items.size(), is(2));
    validateItemExistsInItems(items, declaredLostActualCostItem);
    validateItemInItemsIsDeclaredLost(items, declaredLostActualCostItem);
    validateItemExistsInItems(items, agedToLostActualCostItem);
    validateItemInItemsIsAgedToLost(items, agedToLostActualCostItem);
    validateItemNotExistsInItems(items, closedItem);
    validateItemNotExistsInItems(items, openItem);
    validateItemNotExistsInItems(items, declaredLostSetCostItem);
    validateItemNotExistsInItems(items, agedToLostSetCostItem);
  }

  @Test
  public void shouldIncludeItemsThatAreDeclaredLostUsingActualCostCharge() {
    final LostItemFeePolicyBuilder actualCostPolicy = createActualCostChargeFeePolicy();
    final ItemResource declaredLostActualCostItem = createDeclaredLostItem(actualCostPolicy);

    final Collection<JsonObject> response = ResourceClient.forLostItemsRequiringActualCosts().getAll();

    assertThat(response.size(), is(1));
    validateItemExistsInItems(response, declaredLostActualCostItem);
    validateItemInItemsIsDeclaredLost(response, declaredLostActualCostItem);
  }

  @Test
  public void shouldNotIncludeItemsThatAreDeclaredLostUsingSetCostCharge() {
    final LostItemFeePolicyBuilder setCostPolicy = createSetCostChargeFeePolicy();

    createDeclaredLostItem(setCostPolicy);

    final Collection<JsonObject> response = ResourceClient.forLostItemsRequiringActualCosts().getAll();

    assertThat(response.size(), is(0));
  }

  @Test
  public void shouldIncludeItemsThatHaveAgedToLostUsingActualCostCharge() {
    final LostItemFeePolicyBuilder actualCostAgedLostPolicy = createActualCostAgeToLostAndBilledAfterOneMinutePolicy();
    final ItemResource agedToLostItem = createAgedToLostItem(actualCostAgedLostPolicy);

    final Collection<JsonObject> items = ResourceClient.forLostItemsRequiringActualCosts().getAll();

    assertThat(items.size(), is(1));
    validateItemExistsInItems(items, agedToLostItem);
    validateItemInItemsIsAgedToLost(items, agedToLostItem);
  }

  @Test
  public void shouldNotIncludeItemsThatHaveAgedToLostUsingSetCostCharge() {
    final LostItemFeePolicyBuilder setCostPolicy = createSetCostAgeToLostAndBilledAfterOneMinutePolicy();

    createAgedToLostItem(setCostPolicy);

    final Collection<JsonObject> items = ResourceClient.forLostItemsRequiringActualCosts().getAll();

    assertThat(items.size(), is(0));
  }

  @Test
  public void shouldNotIncludeUnbilledAgedToLostItemUsingActualCostCharge() {
    final LostItemFeePolicyBuilder actualCostAgedLostPolicy = createActualCostAgeToLostAndBilledAfterOneMinutePolicy();

    createAgedToLostItem(actualCostAgedLostPolicy, false);

    Collection<JsonObject> items = ResourceClient.forLostItemsRequiringActualCosts().getAll();

    assertThat(items.size(), is(0));
  }

  @Test
  public void shouldNotIncludeUnbilledAgedToLostItemUsingSetCostCharge() {
    final LostItemFeePolicyBuilder setCostAgedLostPolicy = createSetCostAgeToLostAndBilledAfterOneMinutePolicy();

    createAgedToLostItem(setCostAgedLostPolicy, false);

    Collection<JsonObject> items = ResourceClient.forLostItemsRequiringActualCosts().getAll();

    assertThat(items.size(), is(0));
  }

  @Test
  public void isEmptyWhenNoLostItems() {
    createClosedItem();
    createOpenItem();

    final List<JsonObject> items = ResourceClient.forLostItemsRequiringActualCosts().getAll();

    assertTrue(items.isEmpty());
  }

  private DateTime getLoanOverdueDate() {
    return now(UTC).minusWeeks(3);
  }

  private DateTime getLoanDueDate() {
    return now(UTC).plusWeeks(3);
  }

  private ItemResource createDeclaredLostItem(LostItemFeePolicyBuilder policy) {
    useLostItemPolicy(lostItemFeePoliciesFixture.create(policy).getId());

    final ItemResource declaredLostItem = itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::withRandomBarcode);
    final IndividualResource checkOutDeclaredLost = checkOutFixture
      .checkOutByBarcode(declaredLostItem, usersFixture.charlotte(), getLoanDueDate());

    final DeclareItemLostRequestBuilder declaredLostLoanBuilder = new DeclareItemLostRequestBuilder()
      .forLoanId(checkOutDeclaredLost.getId());

    Response declareLostResponse = declareLostFixtures.declareItemLost(declaredLostLoanBuilder);
    assertThat(declareLostResponse.getStatusCode(), CoreMatchers.is(204));

    JsonObject declaredLostLoan = loansFixture.getLoanById(checkOutDeclaredLost.getId()).getJson();
    assertThat(declaredLostLoan.getJsonObject("item"), ItemMatchers.isDeclaredLost());

    return declaredLostItem;
  }

  private ItemResource createAgedToLostItem(LostItemFeePolicyBuilder policy) {
    return createAgedToLostItem(policy, true);
  }

  private ItemResource createAgedToLostItem(LostItemFeePolicyBuilder policy, boolean charge) {
    useLostItemPolicy(lostItemFeePoliciesFixture.create(policy).getId());

    final ItemResource agedToLostItemResource = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);

    final IndividualResource checkOutAgeToLost = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(agedToLostItemResource)
        .at(servicePointsFixture.cd1())
        .to(usersFixture.james())
        .on(getLoanOverdueDate().minusMinutes(2)));

    ageToLostFixture.ageToLost();

    if (charge) {
      ageToLostFixture.chargeFees();
    }

    JsonObject agedToLostLoan = loansFixture.getLoanById(checkOutAgeToLost.getId()).getJson();
    assertThat(agedToLostLoan.getJsonObject("item"), ItemMatchers.isAgedToLost());

    return agedToLostItemResource;
  }

  private ItemResource createClosedItem() {
    final ItemResource closedItem = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);
    checkOutFixture.checkOutByBarcode(closedItem, usersFixture.jessica());
    checkInFixture.checkInByBarcode(closedItem);

    return closedItem;
  }

  private ItemResource createOpenItem() {
    final ItemResource openItem = itemsFixture.basedUponInterestingTimes(ItemBuilder::withRandomBarcode);
    checkOutFixture.checkOutByBarcode(openItem, usersFixture.charlotte());

    return openItem;
  }

  private void validateItemExistsInItems(Collection<JsonObject> items, ItemResource item) {
    Optional<JsonObject> itemJsonOptional = getRecordById(items, item.getId());
    assertTrue(itemJsonOptional.isPresent());
  }

  private void validateItemNotExistsInItems(Collection<JsonObject> items, ItemResource item) {
    assertTrue(getRecordById(items, item.getId()).isEmpty());
  }

  private void validateItemInItemsIsDeclaredLost(Collection<JsonObject> items, ItemResource item) {
    JsonObject itemJson = getRecordById(items, item.getId()).get();
    assertThat(itemJson, ItemMatchers.isDeclaredLost());
  }

  private void validateItemInItemsIsAgedToLost(Collection<JsonObject> items, ItemResource item) {
    JsonObject itemJson = getRecordById(items, item.getId()).get();
    assertThat(itemJson, ItemMatchers.isAgedToLost());
  }

  private LostItemFeePolicyBuilder createSetCostChargeFeePolicy() {
    lostItemFeePoliciesFixture.createReferenceData();

    return lostItemFeePoliciesFixture.chargeFeePolicy()
      .withName("set cost fees policy");
  }

  private LostItemFeePolicyBuilder createActualCostChargeFeePolicy() {
    lostItemFeePoliciesFixture.createReferenceData();

    return lostItemFeePoliciesFixture.chargeFeePolicy()
      .withName("actual cost fees policy")
      .withActualCost(20.00);
  }

  private LostItemFeePolicyBuilder createSetCostAgeToLostAndBilledAfterOneMinutePolicy() {
    return createSetCostChargeFeePolicy()
      .withName("Age to lost and billed after one minute overdue, with set cost")
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withPatronBilledAfterAgedLost(minutes(5))
      // disable lost item processing fee
      .withChargeAmountItemPatron(false)
      .withChargeAmountItemSystem(true);
  }

  private LostItemFeePolicyBuilder createActualCostAgeToLostAndBilledAfterOneMinutePolicy() {
    return createActualCostChargeFeePolicy()
      .withName("Age to lost and billed after one minute overdue, with actual cost")
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withPatronBilledAfterAgedLost(minutes(5))
      // disable lost item processing fee
      .withChargeAmountItemPatron(false)
      .withChargeAmountItemSystem(true);
  }

}
