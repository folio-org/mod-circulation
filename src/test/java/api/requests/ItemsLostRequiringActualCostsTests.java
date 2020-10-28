package api.requests;

import static api.support.JsonCollectionAssistant.getRecordById;
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

  public ItemsLostRequiringActualCostsTests() {
    super(true, true);
  }

  @Test
  public void isEmptyWhenNoLostItems() {
    createClosedItem();
    createOpenItem();

    List<JsonObject> items = ResourceClient.forItemsLostRequiringActualCosts().getAll();

    assertTrue(items.isEmpty());
  }

  @Test
  public void hasManyItemsLost() {
    useLostItemPolicy(lostItemFeePoliciesFixture.chargeFee().getId());

    final ItemResource closedItem = createClosedItem();
    final ItemResource openItem = createOpenItem();
    final ItemResource declaredLostItem = createDeclaredLostItem();

    useLostItemPolicy(ageToLostAndBilledAfterOneMinute().getId());

    final ItemResource agedToLostItem = createAgedToLostItem();

    ageToLostFixture.chargeFees();

    Collection<JsonObject> items = ResourceClient.forItemsLostRequiringActualCosts().getAll();

    assertThat(items.size(), is(2));
    validateItemExistsInItems(items, declaredLostItem);
    validateItemInItemsIsDeclaredLost(items, declaredLostItem);
    validateItemExistsInItems(items, agedToLostItem);
    validateItemInItemsIsAgedToLost(items, agedToLostItem);
    validateItemNotExistsInItems(items, closedItem);
    validateItemNotExistsInItems(items, openItem);
  }

  @Test
  public void hasItemDeclaredLost() {
    useLostItemPolicy(lostItemFeePoliciesFixture.chargeFee().getId());

    final ItemResource declaredLostItem = createDeclaredLostItem();

    Collection<JsonObject> items = ResourceClient.forItemsLostRequiringActualCosts().getAll();

    assertThat(items.size(), is(1));
    validateItemExistsInItems(items, declaredLostItem);
    validateItemInItemsIsDeclaredLost(items, declaredLostItem);
  }

  @Test
  public void hasItemAgedToLost() {
    useLostItemPolicy(ageToLostAndBilledAfterOneMinute().getId());

    final ItemResource agedToLostItem = createAgedToLostItem();

    ageToLostFixture.chargeFees();

    Collection<JsonObject> items = ResourceClient.forItemsLostRequiringActualCosts().getAll();

    assertThat(items.size(), is(1));
    validateItemExistsInItems(items, agedToLostItem);
    validateItemInItemsIsAgedToLost(items, agedToLostItem);
  }

  @Test
  public void hasNoUnbilledItemAgedToLost() {
    useLostItemPolicy(lostItemFeePoliciesFixture.ageToLostAfterOneMinute().getId());

    createAgedToLostItem();

    Collection<JsonObject> items = ResourceClient.forItemsLostRequiringActualCosts().getAll();

    assertThat(items.size(), is(0));
  }

  @Test
  public void hasNoBilledWithActualCostItemAgedToLost() {
    useLostItemPolicy(ageToLostAndBilledWithActualCostAfterOneMinute().getId());

    createAgedToLostItem();

    Collection<JsonObject> items = ResourceClient.forItemsLostRequiringActualCosts().getAll();

    assertThat(items.size(), is(0));
  }

  private ItemResource createDeclaredLostItem() {
    final ItemResource declaredLostItem = itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::withRandomBarcode);
    final IndividualResource checkOutDeclaredLost = checkOutFixture
      .checkOutByBarcode(declaredLostItem, usersFixture.charlotte());

    final DeclareItemLostRequestBuilder declaredLostLoanBuilder = new DeclareItemLostRequestBuilder()
      .forLoanId(checkOutDeclaredLost.getId());

    Response declareLostResponse = declareLostFixtures.declareItemLost(declaredLostLoanBuilder);
    assertThat(declareLostResponse.getStatusCode(), CoreMatchers.is(204));

    JsonObject declaredLostLoan = loansFixture.getLoanById(checkOutDeclaredLost.getId()).getJson();
    assertThat(declaredLostLoan.getJsonObject("item"), ItemMatchers.isDeclaredLost());

    return declaredLostItem;
  }

  private DateTime getLoanOverdueDate() {
    return now(UTC).minusWeeks(3);
  }

  private ItemResource createAgedToLostItem() {
    final ItemResource agedToLostItemResource = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(agedToLostItemResource)
        .at(servicePointsFixture.cd1())
        .to(usersFixture.james())
        .on(getLoanOverdueDate().minusMinutes(1)));

    ageToLostFixture.ageToLost();

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

  private IndividualResource ageToLostAndBilledAfterOneMinute() {
    return lostItemFeePoliciesFixture.create(ageToLostAndBilledAfterOneMinutePolicy());
  }

  private IndividualResource ageToLostAndBilledWithActualCostAfterOneMinute() {
    return lostItemFeePoliciesFixture.create(ageToLostAndBilledWithActualCostAfterOneMinutePolicy());
  }

  public LostItemFeePolicyBuilder ageToLostAndBilledAfterOneMinutePolicy() {
    return lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("Age to lost and billed after one minute overdue")
      .billPatronImmediatelyWhenAgedToLost();
  }

  public LostItemFeePolicyBuilder ageToLostAndBilledWithActualCostAfterOneMinutePolicy() {
    return ageToLostAndBilledAfterOneMinutePolicy()
      .withName("Age to lost and billed after one minute overdue, with actual cost")
      .withActualCost(20.0);
  }

}
