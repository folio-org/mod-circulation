package org.folio.circulation.domain.policy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
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
  public void shouldAcceptOverdueFinePolicyWithoutOverdueFineQuantity() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueFine").remove("quantity");

    OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    assertThat(overdueFinePolicy, notNullValue());
  }

  @Test
  public void shouldAcceptOverdueFinePolicyWithoutOverdueFineInterval() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.getJsonObject("overdueFine").remove("intervalId");

    OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    assertThat(overdueFinePolicy, notNullValue());
  }

  @Test
  public void shouldAcceptOverdueFinePolicyWithoutOverdueFine() {
    JsonObject jsonObject = overdueFinePolicyJsonObject.copy();
    jsonObject.remove("overdueFine");

    OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.from(jsonObject);

    assertThat(overdueFinePolicy, notNullValue());
  }
}
