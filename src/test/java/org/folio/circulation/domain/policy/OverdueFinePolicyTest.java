package org.folio.circulation.domain.policy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class OverdueFinePolicyTest {

  private static JsonObject overdueFinePolicyJsonObject;

  @Before
  public void setUp() {
    overdueFinePolicyJsonObject = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", "Overdue Fine Policy")
      .put("overdueFine", new JsonObject()
        .put("intervalId", "minute")
        .put("quantity", 5.0))
      .put("maxOverdueFine", 10.0)
      .put("maxOverdueRecallFine", 10.0)
      .put("gracePeriodRecall", true)
      .put("countClosed", true);
  }

  @Test
  public void shouldAcceptValidJsonRepresentation() {
    OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.from(overdueFinePolicyJsonObject);

    assertThat(overdueFinePolicy.getId(), is(overdueFinePolicyJsonObject.getString("id")));
    assertThat(overdueFinePolicy.getName(), is(overdueFinePolicyJsonObject.getString("name")));
    assertThat(overdueFinePolicy.getOverdueFineInterval(), is(OverdueFineInterval.fromValue(
      overdueFinePolicyJsonObject.getJsonObject("overdueFine").getString("intervalId"))));
    assertThat(overdueFinePolicy.getOverdueFine(),
      is(overdueFinePolicyJsonObject.getJsonObject("overdueFine").getDouble("quantity")));
    assertThat(overdueFinePolicy.getMaxOverdueFine(),
      is(overdueFinePolicyJsonObject.getDouble("maxOverdueFine")));
    assertThat(overdueFinePolicy.getMaxOverdueRecallFine(),
      is(overdueFinePolicyJsonObject.getDouble("maxOverdueRecallFine")));
    assertThat(overdueFinePolicy.getIgnoreGracePeriodForRecalls(),
      is(overdueFinePolicyJsonObject.getBoolean("gracePeriodRecall")));
    assertThat(overdueFinePolicy.getCountPeriodsWhenServicePointIsClosed(),
      is(overdueFinePolicyJsonObject.getBoolean("countClosed")));
  }

  @Test
  public void shouldAcceptOverdueFinePolicyWithMissingOverdueFineQuantity() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueFine").remove("quantity");

    OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatQuantityIsNull(overdueFinePolicy);
  }

  @Test
  public void shouldAcceptOverdueFinePolicyWithNullOverdueFineQuantity() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueFine").put("quantity", (Double) null);

    OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatQuantityIsNull(overdueFinePolicy);
  }

  @Test
  public void shouldAcceptOverdueFinePolicyWithMissingOverdueFineInterval() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueFine").remove("intervalId");

    OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatIntervalIsNull(overdueFinePolicy);
  }

  @Test
  public void shouldAcceptOverdueFinePolicyWithNullOverdueFineInterval() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueFine").put("intervalId", (String) null);

    OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatIntervalIsNull(overdueFinePolicy);
  }

  @Test
  public void shouldAcceptOverdueFinePolicyWithOverdueFineMissing() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.remove("overdueFine");

    OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatQuantityAndIntervalAreNull(overdueFinePolicy);
  }

  @Test
  public void shouldAcceptOverdueFinePolicyWithOnlyIdAndName() {
    OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.from(new JsonObject()
      .put("id", overdueFinePolicyJsonObject.getString("id"))
      .put("name", "Overdue Fine Policy"));

    assertThatAllFieldsHaveDefaultValuesExceptIdAndName(overdueFinePolicy);
  }

  @Test
  public void shouldAcceptOverdueFinePolicyWithOnlyId() {
    OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.from(new JsonObject()
      .put("id", overdueFinePolicyJsonObject.getString("id")));

    assertThatAllFieldsHaveDefaultValuesExceptId(overdueFinePolicy);
  }

  @Test
  public void unknownPolicyShouldBeInitializedWithDefaultValues() {
    assertThatAllFieldsHaveDefaultValuesExceptId(OverdueFinePolicy.unknown(
      overdueFinePolicyJsonObject.getString("id")));
  }

  private void checkAllFieldsAndAssertThatQuantityIsNull(OverdueFinePolicy overdueFinePolicy) {
    assertThat(overdueFinePolicy.getId(), is(overdueFinePolicyJsonObject.getString("id")));
    assertThat(overdueFinePolicy.getName(), is(overdueFinePolicyJsonObject.getString("name")));
    assertThat(overdueFinePolicy.getOverdueFineInterval(), is(OverdueFineInterval.fromValue(
      overdueFinePolicyJsonObject.getJsonObject("overdueFine").getString("intervalId"))));
    assertThat(overdueFinePolicy.getOverdueFine(), nullValue());
    assertThat(overdueFinePolicy.getMaxOverdueFine(),
      is(overdueFinePolicyJsonObject.getDouble("maxOverdueFine")));
    assertThat(overdueFinePolicy.getMaxOverdueRecallFine(),
      is(overdueFinePolicyJsonObject.getDouble("maxOverdueRecallFine")));
    assertThat(overdueFinePolicy.getIgnoreGracePeriodForRecalls(),
      is(overdueFinePolicyJsonObject.getBoolean("gracePeriodRecall")));
    assertThat(overdueFinePolicy.getCountPeriodsWhenServicePointIsClosed(),
      is(overdueFinePolicyJsonObject.getBoolean("countClosed")));
  }

  private void checkAllFieldsAndAssertThatIntervalIsNull(OverdueFinePolicy overdueFinePolicy) {
    assertThat(overdueFinePolicy.getId(), is(overdueFinePolicyJsonObject.getString("id")));
    assertThat(overdueFinePolicy.getName(), is(overdueFinePolicyJsonObject.getString("name")));
    assertThat(overdueFinePolicy.getOverdueFineInterval(), nullValue());
    assertThat(overdueFinePolicy.getOverdueFine(),
      is(overdueFinePolicyJsonObject.getJsonObject("overdueFine").getDouble("quantity")));
    assertThat(overdueFinePolicy.getMaxOverdueFine(),
      is(overdueFinePolicyJsonObject.getDouble("maxOverdueFine")));
    assertThat(overdueFinePolicy.getMaxOverdueRecallFine(),
      is(overdueFinePolicyJsonObject.getDouble("maxOverdueRecallFine")));
    assertThat(overdueFinePolicy.getIgnoreGracePeriodForRecalls(),
      is(overdueFinePolicyJsonObject.getBoolean("gracePeriodRecall")));
    assertThat(overdueFinePolicy.getCountPeriodsWhenServicePointIsClosed(),
      is(overdueFinePolicyJsonObject.getBoolean("countClosed")));
  }

  private void checkAllFieldsAndAssertThatQuantityAndIntervalAreNull(
    OverdueFinePolicy overdueFinePolicy) {
    assertThat(overdueFinePolicy.getId(), is(overdueFinePolicyJsonObject.getString("id")));
    assertThat(overdueFinePolicy.getName(), is(overdueFinePolicyJsonObject.getString("name")));
    assertThat(overdueFinePolicy.getOverdueFineInterval(), nullValue());
    assertThat(overdueFinePolicy.getOverdueFine(), nullValue());
    assertThat(overdueFinePolicy.getMaxOverdueFine(),
      is(overdueFinePolicyJsonObject.getDouble("maxOverdueFine")));
    assertThat(overdueFinePolicy.getMaxOverdueRecallFine(),
      is(overdueFinePolicyJsonObject.getDouble("maxOverdueRecallFine")));
    assertThat(overdueFinePolicy.getIgnoreGracePeriodForRecalls(),
      is(overdueFinePolicyJsonObject.getBoolean("gracePeriodRecall")));
    assertThat(overdueFinePolicy.getCountPeriodsWhenServicePointIsClosed(),
      is(overdueFinePolicyJsonObject.getBoolean("countClosed")));
  }

  private void assertThatAllFieldsHaveDefaultValuesExceptIdAndName(OverdueFinePolicy overdueFinePolicy) {
    assertThat(overdueFinePolicy.getId(), is(overdueFinePolicyJsonObject.getString("id")));
    assertThat(overdueFinePolicy.getName(), is(overdueFinePolicyJsonObject.getString("name")));
    assertThat(overdueFinePolicy.getOverdueFineInterval(), nullValue());
    assertThat(overdueFinePolicy.getOverdueFine(), nullValue());
    assertThat(overdueFinePolicy.getMaxOverdueFine(), nullValue());
    assertThat(overdueFinePolicy.getMaxOverdueRecallFine(), nullValue());
    assertThat(overdueFinePolicy.getIgnoreGracePeriodForRecalls(), is(false));
    assertThat(overdueFinePolicy.getCountPeriodsWhenServicePointIsClosed(), is(false));
  }

  private void assertThatAllFieldsHaveDefaultValuesExceptId(OverdueFinePolicy overdueFinePolicy) {
    assertThat(overdueFinePolicy.getId(), is(overdueFinePolicyJsonObject.getString("id")));
    assertThat(overdueFinePolicy.getName(), nullValue());
    assertThat(overdueFinePolicy.getOverdueFineInterval(), nullValue());
    assertThat(overdueFinePolicy.getOverdueFine(), nullValue());
    assertThat(overdueFinePolicy.getMaxOverdueFine(), nullValue());
    assertThat(overdueFinePolicy.getMaxOverdueRecallFine(), nullValue());
    assertThat(overdueFinePolicy.getIgnoreGracePeriodForRecalls(), is(false));
    assertThat(overdueFinePolicy.getCountPeriodsWhenServicePointIsClosed(), is(false));
  }
}
