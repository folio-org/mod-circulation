package org.folio.circulation.domain.anonymization.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.folio.circulation.domain.policy.Period;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class LoanAnonymizationConfigurationTest {

  @ParameterizedTest
  @CsvSource(nullValues = "NULL", value = {
    "NULL, DISABLED, false",
    "disabled, DISABLED, false",
    "immediately, IMMEDIATELY, true",
    "interval, INTERVAL, true",
    "never, UNKNOWN, true",
    "Interval, UNKNOWN, true",
    "'', UNKNOWN, true",
    "sometimes, UNKNOWN, true"
  })
  void closingTypeIsRead(String value, UserSpecificClosingType expected, boolean enabled) {
    var configuration = LoanAnonymizationConfiguration.from(settingWithUserSpecific(value));

    assertThat(configuration.getUserSpecificClosingType(), is(expected));
    assertThat(configuration.isUserSpecificAnonymizationEnabled(), is(enabled));
  }

  @Test
  void intervalIsReadWithPeriod() {
    var configuration = LoanAnonymizationConfiguration.from(settingWithUserSpecific("interval")
      .put("userSpecific", period(7, "Days")));

    assertThat(configuration.getUserSpecificClosePeriod(), is(Period.days(7)));
  }

  @Test
  void asEarlyAsPossibleImmediatelyReplacesTheLoanRule() {
    var configuration = LoanAnonymizationConfiguration.from(
      settingWithUserSpecific("immediately")).forAsEarlyAsPossible();

    assertThat(configuration.getLoanClosingType(), is(ClosingType.IMMEDIATELY));
    assertFeeFineRuleIsShared(configuration);
  }

  @Test
  void asEarlyAsPossibleIntervalReplacesTheLoanRuleAndPeriod() {
    var configuration = LoanAnonymizationConfiguration.from(settingWithUserSpecific("interval")
      .put("userSpecific", period(7, "Days"))).forAsEarlyAsPossible();

    assertThat(configuration.getLoanClosingType(), is(ClosingType.INTERVAL));
    assertThat(configuration.getLoanClosePeriod(), is(Period.days(7)));
    assertFeeFineRuleIsShared(configuration);
  }

  @Test
  void unknownAsEarlyAsPossibleAnonymizesNoLoans() {
    var configuration = LoanAnonymizationConfiguration.from(settingWithUserSpecific("sometimes"))
      .forAsEarlyAsPossible();

    assertThat(configuration.getLoanClosingType(), is(ClosingType.UNKNOWN));
    assertFeeFineRuleIsShared(configuration);
  }

  private static void assertFeeFineRuleIsShared(LoanAnonymizationConfiguration configuration) {
    assertThat(configuration.getFeesAndFinesClosingType(), is(ClosingType.INTERVAL));
    assertThat(configuration.getFeeFineClosePeriod(), is(Period.months(6)));
    assertThat(configuration.treatLoansWithFeesAndFinesDifferently(), is(true));
  }

  /**
   * Library policy "12 months after the loan closes", fees/fines treated differently
   * ("6 months after the fee/fine closes"), as saved by ui-circulation.
   */
  private static JsonObject librarySetting() {
    return new JsonObject()
      .put("closingType", new JsonObject()
        .put("loan", "interval")
        .put("feeFine", "interval")
        .put("loanExceptions", new JsonArray()))
      .put("loan", period(12, "Months"))
      .put("feeFine", period(6, "Months"))
      .put("loanExceptions", new JsonArray())
      .put("treatEnabled", true);
  }

  private static JsonObject settingWithUserSpecific(String closingType) {
    JsonObject setting = librarySetting();
    setting.getJsonObject("closingType").put("userSpecific", closingType);
    return setting;
  }

  private static JsonObject period(int duration, String intervalId) {
    return new JsonObject().put("duration", duration).put("intervalId", intervalId);
  }
}
