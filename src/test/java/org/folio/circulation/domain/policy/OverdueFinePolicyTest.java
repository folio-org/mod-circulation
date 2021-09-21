package org.folio.circulation.domain.policy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class OverdueFinePolicyTest {

  private static JsonObject overdueFinePolicyJsonObject;

  private OverdueFinePolicy overdueFinePolicy;

  @BeforeEach
  public void setUp() {
    overdueFinePolicyJsonObject = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", "Overdue Fine Policy")
      .put("overdueFine", new JsonObject()
        .put("intervalId", "minute")
        .put("quantity", 5.0))
      .put("overdueRecallFine", new JsonObject()
        .put("intervalId", "hour")
        .put("quantity", 6.0))
      .put("maxOverdueFine", 10.0)
      .put("maxOverdueRecallFine", 12.0)
      .put("gracePeriodRecall", true)
      .put("countClosed", true);
  }

  @Test
  void shouldAcceptValidJsonRepresentation() {
    overdueFinePolicy = OverdueFinePolicy.from(overdueFinePolicyJsonObject);

    checkId();
    checkName();
    checkOverdueFineInterval();
    checkOverdueFineQuantity();
    checkOverdueRecallFineInterval();
    checkOverdueRecallFineQuantity();
    checkMaxOverdueFine();
    checkMaxOverdueRecallFine();
    checkIgnoreGracePeriodForRecalls();
    checkCountPeriodsWhenServicePointIsClosed();
  }

  @Test
  void shouldAcceptOverdueFinePolicyWithMissingOverdueFineQuantity() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueFine").remove("quantity");

    overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatQuantityIsNull();
  }

  @Test
  void shouldAcceptOverdueFinePolicyWithNullOverdueFineQuantity() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueFine").put("quantity", (Double) null);

    overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatQuantityIsNull();
  }

  @Test
  void shouldAcceptOverdueFinePolicyWithMissingOverdueFineInterval() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueFine").remove("intervalId");

    overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatIntervalIsNull();
  }

  @Test
  void shouldAcceptOverdueFinePolicyWithNullOverdueFineInterval() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueFine").put("intervalId", (String) null);

    overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatIntervalIsNull();
  }

  @Test
  void shouldAcceptOverdueFinePolicyWithOverdueFineMissing() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.remove("overdueFine");

    overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatQuantityAndIntervalAreNull();
  }

  @Test
  void shouldAcceptOverdueFinePolicyWithMissingOverdueRecallFineQuantity() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueRecallFine").remove("quantity");

    overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatRecallQuantityIsNull();
  }

  @Test
  void shouldAcceptOverdueFinePolicyWithNullOverdueRecallFineQuantity() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueRecallFine").put("quantity", (Double) null);

    overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatRecallQuantityIsNull();
  }

  @Test
  void shouldAcceptOverdueFinePolicyWithMissingOverdueRecallFineInterval() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueRecallFine").remove("intervalId");

    overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatRecallIntervalIsNull();
  }

  @Test
  void shouldAcceptOverdueFinePolicyWithNullOverdueRecallFineInterval() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueRecallFine").put("intervalId", (String) null);

    overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatRecallIntervalIsNull();
  }

  @Test
  void shouldAcceptOverdueFinePolicyWithOverdueRecallFineMissing() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.remove("overdueRecallFine");

    overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    checkAllFieldsAndAssertThatRecallQuantityAndIntervalAreNull();
  }

  @Test
  void shouldAcceptOverdueFinePolicyWithOnlyIdAndName() {
    overdueFinePolicy = OverdueFinePolicy.from(new JsonObject()
      .put("id", overdueFinePolicyJsonObject.getString("id"))
      .put("name", "Overdue Fine Policy"));

    assertThatAllFieldsHaveDefaultValuesExceptIdAndName();
  }

  @Test
  void shouldAcceptOverdueFinePolicyWithOnlyId() {
    overdueFinePolicy = OverdueFinePolicy.from(new JsonObject()
      .put("id", overdueFinePolicyJsonObject.getString("id")));

    assertThatAllFieldsHaveDefaultValuesExceptId();
  }

  @Test
  void unknownPolicyShouldBeInitializedWithDefaultValues() {
    overdueFinePolicy = OverdueFinePolicy.unknown(overdueFinePolicyJsonObject.getString("id"));
    assertThatAllFieldsHaveDefaultValuesExceptId();
  }

  @Test
  void calculationParametersAreCorrectForValidJsonRepresentation() {
    overdueFinePolicy = OverdueFinePolicy.from(overdueFinePolicyJsonObject);

    OverdueFineCalculationParameters calculationParameters =
      overdueFinePolicy.getCalculationParameters(false);
    assertThat(calculationParameters.getFinePerInterval(), is(BigDecimal.valueOf(5.0)));
    assertThat(calculationParameters.getInterval(), is(OverdueFineInterval.MINUTE));
    assertThat(calculationParameters.getMaxFine(), is(BigDecimal.valueOf(10.0)));

    OverdueFineCalculationParameters recallCalculationParameters =
      overdueFinePolicy.getCalculationParameters(true);
    assertThat(recallCalculationParameters.getFinePerInterval(), is(BigDecimal.valueOf(6.0)));
    assertThat(recallCalculationParameters.getInterval(), is(OverdueFineInterval.HOUR));
    assertThat(recallCalculationParameters.getMaxFine(), is(BigDecimal.valueOf(12.0)));
  }

  @Test
  void calculationParametersIsNullForUnknownPolicy() {
    overdueFinePolicy = OverdueFinePolicy.unknown(overdueFinePolicyJsonObject.getString("id"));
    assertThat(overdueFinePolicy.getCalculationParameters(false), nullValue());
    assertThat(overdueFinePolicy.getCalculationParameters(true), nullValue());
  }

  private void checkAllFieldsAndAssertThatQuantityIsNull() {
    checkId();
    checkName();
    checkOverdueFineInterval();
    assertThat(overdueFinePolicy.getFineInfo().getOverdueFine(), nullValue());
    checkOverdueRecallFineInterval();
    checkOverdueRecallFineQuantity();
    checkMaxOverdueFine();
    checkMaxOverdueRecallFine();
    checkIgnoreGracePeriodForRecalls();
    checkCountPeriodsWhenServicePointIsClosed();
  }

  private void checkAllFieldsAndAssertThatRecallQuantityIsNull() {
    checkId();
    checkName();
    checkOverdueFineInterval();
    checkOverdueFineQuantity();
    checkOverdueRecallFineInterval();
    assertThat(overdueFinePolicy.getFineInfo().getOverdueRecallFine(), nullValue());
    checkMaxOverdueFine();
    checkMaxOverdueRecallFine();
    checkIgnoreGracePeriodForRecalls();
    checkCountPeriodsWhenServicePointIsClosed();
  }

  private void checkAllFieldsAndAssertThatIntervalIsNull() {
    checkId();
    checkName();
    assertThat(overdueFinePolicy.getFineInfo().getOverdueFineInterval(), nullValue());
    checkOverdueFineQuantity();
    checkOverdueRecallFineInterval();
    checkOverdueRecallFineQuantity();
    checkMaxOverdueFine();
    checkMaxOverdueRecallFine();
    checkIgnoreGracePeriodForRecalls();
    checkCountPeriodsWhenServicePointIsClosed();
  }

  private void checkAllFieldsAndAssertThatRecallIntervalIsNull() {
    checkId();
    checkName();
    checkOverdueFineInterval();
    checkOverdueFineQuantity();
    assertThat(overdueFinePolicy.getFineInfo().getOverdueRecallFineInterval(), nullValue());
    checkOverdueRecallFineQuantity();
    checkMaxOverdueFine();
    checkMaxOverdueRecallFine();
    checkIgnoreGracePeriodForRecalls();
    checkCountPeriodsWhenServicePointIsClosed();
  }

  private void checkAllFieldsAndAssertThatQuantityAndIntervalAreNull() {
    checkId();
    checkName();
    assertThat(overdueFinePolicy.getFineInfo().getOverdueFineInterval(), nullValue());
    assertThat(overdueFinePolicy.getFineInfo().getOverdueFine(), nullValue());
    checkOverdueRecallFineInterval();
    checkOverdueRecallFineQuantity();
    checkMaxOverdueFine();
    checkMaxOverdueRecallFine();
    checkIgnoreGracePeriodForRecalls();
    checkCountPeriodsWhenServicePointIsClosed();
  }

  private void checkAllFieldsAndAssertThatRecallQuantityAndIntervalAreNull() {
    checkId();
    checkName();
    checkOverdueFineInterval();
    checkOverdueFineQuantity();
    assertThat(overdueFinePolicy.getFineInfo().getOverdueRecallFineInterval(), nullValue());
    assertThat(overdueFinePolicy.getFineInfo().getOverdueRecallFine(),nullValue());
    checkMaxOverdueFine();
    checkMaxOverdueRecallFine();
    checkIgnoreGracePeriodForRecalls();
    checkCountPeriodsWhenServicePointIsClosed();
  }

  private void assertThatAllFieldsHaveDefaultValuesExceptIdAndName() {
    checkId();
    checkName();
    assertThat(overdueFinePolicy.getFineInfo().getOverdueFineInterval(), nullValue());
    assertThat(overdueFinePolicy.getFineInfo().getOverdueFine(), nullValue());
    assertThat(overdueFinePolicy.getFineInfo().getOverdueFineInterval(), nullValue());
    assertThat(overdueFinePolicy.getFineInfo().getOverdueFine(),nullValue());
    assertThat(overdueFinePolicy.getLimitInfo().getMaxOverdueFine(), nullValue());
    assertThat(overdueFinePolicy.getLimitInfo().getMaxOverdueRecallFine(), nullValue());
    assertThat(overdueFinePolicy.getIgnoreGracePeriodForRecalls(), is(false));
    assertThat(overdueFinePolicy.getCountPeriodsWhenServicePointIsClosed(), is(false));
  }

  private void assertThatAllFieldsHaveDefaultValuesExceptId() {
    checkId();
    assertThat(overdueFinePolicy.getName(), nullValue());
    assertThat(overdueFinePolicy.getFineInfo().getOverdueFineInterval(), nullValue());
    assertThat(overdueFinePolicy.getFineInfo().getOverdueFine(), nullValue());
    assertThat(overdueFinePolicy.getLimitInfo().getMaxOverdueFine(), nullValue());
    assertThat(overdueFinePolicy.getLimitInfo().getMaxOverdueRecallFine(), nullValue());
    assertThat(overdueFinePolicy.getIgnoreGracePeriodForRecalls(), is(false));
    assertThat(overdueFinePolicy.getCountPeriodsWhenServicePointIsClosed(), is(false));
  }

  private void checkId() {
    assertThat(overdueFinePolicy.getId(), is(overdueFinePolicyJsonObject.getString("id")));
  }

  private void checkName() {
    assertThat(overdueFinePolicy.getName(), is(overdueFinePolicyJsonObject.getString("name")));
  }

  private void checkOverdueFineInterval() {
    assertThat(overdueFinePolicy.getFineInfo().getOverdueFineInterval(),
      is(OverdueFineInterval.fromValue(overdueFinePolicyJsonObject.getJsonObject("overdueFine")
        .getString("intervalId"))));
  }

  private void checkOverdueFineQuantity() {
    assertThat(overdueFinePolicy.getFineInfo().getOverdueFine(),
      is(new BigDecimal(overdueFinePolicyJsonObject.getJsonObject("overdueFine")
        .getString("quantity"))));
  }

  private void checkOverdueRecallFineInterval() {
    assertThat(overdueFinePolicy.getFineInfo().getOverdueRecallFineInterval(),
      is(OverdueFineInterval.fromValue(
        overdueFinePolicyJsonObject.getJsonObject("overdueRecallFine").getString("intervalId"))));
  }

  private void checkOverdueRecallFineQuantity() {
    assertThat(overdueFinePolicy.getFineInfo().getOverdueRecallFine(),
      is(new BigDecimal(overdueFinePolicyJsonObject.getJsonObject("overdueRecallFine")
        .getString("quantity"))));
  }

  private void checkMaxOverdueFine() {
    assertThat(overdueFinePolicy.getLimitInfo().getMaxOverdueFine(),
      is(new BigDecimal(overdueFinePolicyJsonObject.getString("maxOverdueFine"))));
  }

  private void checkMaxOverdueRecallFine() {
    assertThat(overdueFinePolicy.getLimitInfo().getMaxOverdueRecallFine(),
      is(new BigDecimal(overdueFinePolicyJsonObject.getString("maxOverdueRecallFine"))));
  }

  private void checkIgnoreGracePeriodForRecalls() {
    assertThat(overdueFinePolicy.getIgnoreGracePeriodForRecalls(),
      is(overdueFinePolicyJsonObject.getBoolean("gracePeriodRecall")));
  }

  private void checkCountPeriodsWhenServicePointIsClosed() {
    assertThat(overdueFinePolicy.getCountPeriodsWhenServicePointIsClosed(),
      is(overdueFinePolicyJsonObject.getBoolean("countClosed")));
  }
}
