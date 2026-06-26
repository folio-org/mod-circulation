package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.results.Result.ofAsync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.anonymization.config.ClosingType;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.configuration.PrintHoldRequestsConfiguration;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.services.CirculationSettingsService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.tomakehurst.wiremock.http.MimeType;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

@ExtendWith(MockitoExtension.class)
class CirculationSettingsServiceTest {

  @Mock
  private Clients clients;
  @Mock
  private CollectionResourceClient circulationSettingsClient;
  private CirculationSettingsService circulationSettingsService;

  @BeforeEach
  void beforeEach() {
    when(clients.circulationSettingsStorageClient())
      .thenReturn(circulationSettingsClient);
    circulationSettingsService = new CirculationSettingsService(clients);
  }

  @Test
  void shouldReturnMergedTlrSettingsWhenSplitSettingsAreFound() {
    JsonObject mockSettingsResponse = new JsonObject()
      .put("circulationSettings", new JsonArray()
        .add(new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "generalTlr")
          .put("value", new JsonObject()
            .put("titleLevelRequestsFeatureEnabled", true)
            .put("createTitleLevelRequestsByDefault", false)
            .put("tlrHoldShouldFollowCirculationRules", true)))
        .add(new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "regularTlr")
          .put("value", new JsonObject()
            .put("confirmationPatronNoticeTemplateId", "51958757-df1d-4c71-84d3-820575d73f81")
            .put("cancellationPatronNoticeTemplateId", "51958757-df1d-4c71-84d3-820575d73f82")
            .put("expirationPatronNoticeTemplateId", "51958757-df1d-4c71-84d3-820575d73f83"))))
      .put("totalRecords", 2);

    mockGetCirculationSettingsResponse(mockSettingsResponse);

    TlrSettingsConfiguration expected = new TlrSettingsConfiguration(true, false, true,
      UUID.fromString("51958757-df1d-4c71-84d3-820575d73f81"),
      UUID.fromString("51958757-df1d-4c71-84d3-820575d73f82"),
      UUID.fromString("51958757-df1d-4c71-84d3-820575d73f83"));

    assertEquals(expected, getTlrSettings());
  }

  @Test
  void shouldFallBackToLegacySettingsWhenSplitSettingsAreNotFound() {
    JsonObject mockSettingsResponse = new JsonObject()
      .put("circulationSettings", new JsonArray().add(
        new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "TLR")
          .put("value", new JsonObject()
            .put("titleLevelRequestsFeatureEnabled", true)
            .put("createTitleLevelRequestsByDefault", true)
            .put("tlrHoldShouldFollowCirculationRules", true)
            .put("confirmationPatronNoticeTemplateId", null)
            .put("cancellationPatronNoticeTemplateId", null)
            .put("expirationPatronNoticeTemplateId", null))))
      .put("totalRecords", 1);

    mockGetCirculationSettingsResponse(mockSettingsResponse);
    TlrSettingsConfiguration expected = new TlrSettingsConfiguration(true, true, true, null, null, null);
    assertEquals(expected, getTlrSettings());
  }

  @Test
  void shouldFallBackToDefaultSettingsWhenNoTlrSettingsAreFound() {
    mockEmptyGetCirculationSettingsResponse();
    TlrSettingsConfiguration expected = new TlrSettingsConfiguration(false, false, false, null, null, null);
    assertEquals(expected, getTlrSettings());
  }

  @Test
  void shouldReturnScheduledNoticesProcessingLimit() {
    JsonObject mockSettingsResponse = new JsonObject()
      .put("circulationSettings", new JsonArray().add(
        new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "noticesLimit")
          .put("value", new JsonObject()
            .put("value", "250"))))
      .put("totalRecords", 1);

    mockGetCirculationSettingsResponse(mockSettingsResponse);
    PageLimit result = getScheduledNoticesProcessingLimit();
    assertEquals(250, result.getLimit());
  }

  @Test
  void shouldReturnDefaultScheduledNoticesProcessingLimitWhenSettingNotFound() {
    mockEmptyGetCirculationSettingsResponse();
    PageLimit result = getScheduledNoticesProcessingLimit();
    assertEquals(100, result.getLimit());
  }

  @Test
  void shouldReturnDefaultScheduledNoticesProcessingLimitWhenValueIsNotNumeric() {
    JsonObject mockSettingsResponse = new JsonObject()
      .put("circulationSettings", new JsonArray().add(
        new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "noticesLimit")
          .put("value", new JsonObject()
            .put("value", "invalid"))))
      .put("totalRecords", 1);

    mockGetCirculationSettingsResponse(mockSettingsResponse);
    PageLimit result = getScheduledNoticesProcessingLimit();
    assertEquals(100, result.getLimit());
  }

  @Test
  void shouldReturnPrintHoldRequestsEnabled() {
    JsonObject mockSettingsResponse = new JsonObject()
      .put("circulationSettings", new JsonArray().add(
        new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "PRINT_HOLD_REQUESTS")
          .put("value", new JsonObject()
            .put("printHoldRequestsEnabled", true))))
      .put("totalRecords", 1);

    mockGetCirculationSettingsResponse(mockSettingsResponse);
    PrintHoldRequestsConfiguration result = getPrintHoldRequestsEnabled();
    assertTrue(result.isPrintHoldRequestsEnabled());
  }

  @Test
  void shouldReturnDefaultPrintHoldRequestsConfigurationWhenSettingNotFound() {
    mockEmptyGetCirculationSettingsResponse();
    PrintHoldRequestsConfiguration result = getPrintHoldRequestsEnabled();
    assertFalse(result.isPrintHoldRequestsEnabled());
  }

  @Test
  void shouldReturnLoanAnonymizationSettings() {
    JsonObject mockSettingsResponse = new JsonObject()
      .put("circulationSettings", new JsonArray().add(
        new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "loan_history")
          .put("value", new JsonObject()
            .put("treatEnabled", true)
            .put("closingType", new JsonObject()
              .put("loan", "interval")
              .put("feeFine", "never"))
            .put("loan", new JsonObject()
              .put("duration", 5)
              .put("intervalId", "Days"))
            .put("feeFine", new JsonObject()
              .put("duration", 10)
              .put("intervalId", "Weeks")))))
      .put("totalRecords", 1);

    mockGetCirculationSettingsResponse(mockSettingsResponse);
    LoanAnonymizationConfiguration expected = new LoanAnonymizationConfiguration(
      ClosingType.INTERVAL, ClosingType.NEVER, true, Period.days(5), Period.weeks(10));
    LoanAnonymizationConfiguration actual = getLoanAnonymizationSettings();
    verifyLoanAnonymizationSettings(actual, expected);
  }

  @Test
  void shouldReturnDefaultLoanAnonymizationSettingsWhenSettingNotFound() {
    mockEmptyGetCirculationSettingsResponse();
    LoanAnonymizationConfiguration expected = new LoanAnonymizationConfiguration(
      ClosingType.UNKNOWN, ClosingType.UNKNOWN, false, Period.from((Integer) null, null),
      Period.from((Integer) null, null));
    LoanAnonymizationConfiguration actual = getLoanAnonymizationSettings();
    verifyLoanAnonymizationSettings(actual, expected);
  }

  @Test
  void shouldReturnCheckOutSessionTimeout() {
    JsonObject mockSettingsResponse = new JsonObject()
      .put("circulationSettings", new JsonArray().add(
        new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "other_settings")
          .put("value", new JsonObject()
            .put("checkoutTimeout", true)
            .put("checkoutTimeoutDuration", 5))))
      .put("totalRecords", 1);

    mockGetCirculationSettingsResponse(mockSettingsResponse);
    Integer result = getCheckOutSessionTimeout();
    assertEquals(5, result);
  }

  @Test
  void shouldReturnDefaultCheckOutSessionTimeoutWhenSettingNotFound() {
    mockEmptyGetCirculationSettingsResponse();
    Integer result = getCheckOutSessionTimeout();
    assertEquals(3, result);
  }

  @Test
  void shouldReturnDefaultCheckOutSessionTimeoutWhenCheckoutTimeoutIsFalse() {
    JsonObject mockSettingsResponse = new JsonObject()
      .put("circulationSettings", new JsonArray().add(
        new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "other_settings")
          .put("value", new JsonObject()
            .put("checkoutTimeout", false)
            .put("checkoutTimeoutDuration", 5))))
      .put("totalRecords", 1);

    mockGetCirculationSettingsResponse(mockSettingsResponse);
    Integer result = getCheckOutSessionTimeout();
    assertEquals(3, result);
  }

  @Test
  void shouldReturnDefaultCheckOutSessionTimeoutWhenValueIsEmpty() {
    JsonObject mockSettingsResponse = new JsonObject()
      .put("circulationSettings", new JsonArray().add(
        new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "other_settings")
          .put("value", new JsonObject())))
      .put("totalRecords", 1);

    mockGetCirculationSettingsResponse(mockSettingsResponse);
    Integer result = getCheckOutSessionTimeout();
    assertEquals(3, result);
  }

  private void mockEmptyGetCirculationSettingsResponse() {
    mockGetCirculationSettingsResponse(new JsonObject()
      .put("circulationSettings", new JsonArray())
      .put("totalRecords", 0));
  }

  private void mockGetCirculationSettingsResponse(JsonObject responseBody) {
    when(circulationSettingsClient.getMany(any(), any()))
      .thenReturn(ofAsync(new Response(200, responseBody.encode(), MimeType.JSON.toString())));
  }

  private TlrSettingsConfiguration getTlrSettings() {
    return getFutureResult(circulationSettingsService.getTlrSettings());
  }

  private PageLimit getScheduledNoticesProcessingLimit() {
    return getFutureResult(circulationSettingsService.getScheduledNoticesProcessingLimit());
  }

  private PrintHoldRequestsConfiguration getPrintHoldRequestsEnabled() {
    return getFutureResult(circulationSettingsService.getPrintHoldRequestsEnabled());
  }

  private LoanAnonymizationConfiguration getLoanAnonymizationSettings() {
    return getFutureResult(circulationSettingsService.getLoanAnonymizationSettings());
  }


  private Integer getCheckOutSessionTimeout() {
    return getFutureResult(circulationSettingsService.getCheckOutSessionTimeout());
  }

  @SneakyThrows
  private static <T> T getFutureResult(CompletableFuture<Result<T>> future) {
    return future.get(30, TimeUnit.SECONDS).value();
  }

  private void verifyLoanAnonymizationSettings(LoanAnonymizationConfiguration expected,
    LoanAnonymizationConfiguration actual) {

    assertEquals(expected.getLoanClosingType(), actual.getLoanClosingType());
    assertEquals(expected.getFeesAndFinesClosingType(), actual.getFeesAndFinesClosingType());
    assertEquals(expected.treatLoansWithFeesAndFinesDifferently(), actual.treatLoansWithFeesAndFinesDifferently());
    assertEquals(expected.getLoanClosePeriod().getDuration(), actual.getLoanClosePeriod().getDuration());
    assertEquals(expected.getLoanClosePeriod().getInterval(), actual.getLoanClosePeriod().getInterval());
    assertEquals(expected.getFeeFineClosePeriod().getDuration(), actual.getFeeFineClosePeriod().getDuration());
    assertEquals(expected.getFeeFineClosePeriod().getInterval(), actual.getFeeFineClosePeriod().getInterval());

  }
}
