package api.loans.agetolost;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.ItemMatchers.isAgedToLost;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.ItemMatchers.isClaimedReturned;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;

import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import api.support.MultipleJsonRecords;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.spring.SpringApiTest;
import api.support.spring.clients.ScheduledJobClient;
import io.vertx.core.json.JsonObject;
import lombok.val;

public class ScheduledAgeToLostApiTest extends SpringApiTest {
  private IndividualResource overdueLoan;
  private IndividualResource overdueItem;
  @Autowired
  private ScheduledJobClient scheduledAgeToLostClient;

  public ScheduledAgeToLostApiTest() {
    super(true, true);
  }

  @Before
  public void activateLostItemFeePolicy() {
    useLostItemPolicy(lostItemFeePoliciesFixture.ageToLostAfterOneMinute().getId());

    checkOutItem();
  }

  @Test
  public void shouldIgnoreOverdueLoansWhenItemIsClaimedReturned() {
    claimItemReturnedFixture.claimItemReturned(overdueLoan.getId());

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isClaimedReturned());
  }

  @Test
  public void shouldNotAgeAnyItemsToLostIfNoIntervalDefined() {
    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("Aged to lost disabled")
      .withItemAgedToLostAfterOverdue(null);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(policy).getId());

    checkOutItem();

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isCheckedOut());
  }

  @Test
  public void shouldNotAgeItemToLostWhenNotOverdueByMoreThanIntervalYet() {
    overdueItem = itemsFixture.basedUponNod();
    overdueLoan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(overdueItem)
        .at(servicePointsFixture.cd1())
        .to(usersFixture.james())
        .on(now(UTC)));

    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isCheckedOut());
  }

  @Test
  public void shouldAgeItemToLostWhenOverdueByMoreThanInterval() {
    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isAgedToLost());
    assertThat(getLoanActions(), hasAgedToLostAction());
  }

  @Test
  public void shouldNotProcessAgedToLostItemSecondTime() {
    scheduledAgeToLostClient.triggerJob();
    scheduledAgeToLostClient.triggerJob();

    assertThat(itemsClient.get(overdueItem).getJson(), isAgedToLost());
    assertThat(getLoanActions(), hasAgedToLostAction());

    val agedToLostActions = loanHistoryClient.getMany(
      queryFromTemplate("loan.id==%s and loan.action==%s", overdueLoan.getId(), "itemAgedToLost"));

    assertThat(agedToLostActions, iterableWithSize(1));
  }

  private DateTime getLoanOverdueDate() {
    return now(UTC).minusWeeks(3);
  }

  private void checkOutItem() {
    overdueItem = itemsFixture.basedUponNod(
      builder -> builder.withBarcode(generateString()));

    overdueLoan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(overdueItem)
        .at(servicePointsFixture.cd1())
        .to(usersFixture.charlotte())
        .on(getLoanOverdueDate().minusMinutes(2)));
  }

  private MultipleJsonRecords getLoanActions() {
    return loanHistoryClient
      .getMany(queryFromTemplate("loan.id==%s and operation==U", overdueLoan.getId()));
  }

  private Matcher<Iterable<? super JsonObject>> hasAgedToLostAction() {
    return hasItem(allOf(
      hasJsonPath("loan.status.name", "Open"),
      hasJsonPath("loan.action", "itemAgedToLost"),
      hasJsonPath("loan.itemStatus", "Aged to lost")
    ));
  }
}
