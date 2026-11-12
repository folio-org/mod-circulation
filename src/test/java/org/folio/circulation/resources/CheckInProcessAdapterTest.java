package org.folio.circulation.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;

import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LoanCheckInService;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class CheckInProcessAdapterTest {
  @Mock
  private LoanCheckInService loanCheckInService;

  private CheckInProcessAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new CheckInProcessAdapter(null, null, loanCheckInService, null, null, null,
      null, null, null, null, null, null, null, null, null, null, null, null);
  }

  @Test
  void explicitInHouseUseTrueIsAcceptedWithoutConsultingInference() {
    final CheckInContext result = adapter.setInHouseUse(contextWith(true));

    assertThat(result.isInHouseUse(), is(true));
    verifyNoInteractions(loanCheckInService);
  }

  @Test
  void explicitInHouseUseFalseFallsBackToInference() {
    when(loanCheckInService.isInHouseUse(any(), any(), any())).thenReturn(true);

    final CheckInContext result = adapter.setInHouseUse(contextWith(false));

    assertThat(result.isInHouseUse(), is(true));
  }

  @Test
  void absentInHouseUseFallsBackToPositiveInference() {
    when(loanCheckInService.isInHouseUse(any(), any(), any())).thenReturn(true);

    final CheckInContext result = adapter.setInHouseUse(contextWith(null));

    assertThat(result.isInHouseUse(), is(true));
  }

  @Test
  void absentInHouseUseFallsBackToNegativeInference() {
    when(loanCheckInService.isInHouseUse(any(), any(), any())).thenReturn(false);

    final CheckInContext result = adapter.setInHouseUse(contextWith(null));

    assertThat(result.isInHouseUse(), is(false));
  }

  private CheckInContext contextWith(Boolean inHouseUse) {
    final JsonObject requestJson = new CheckInByBarcodeRequestBuilder()
      .withItemBarcode("barcode")
      .on(ClockUtil.getZonedDateTime())
      .at(UUID.randomUUID())
      .withInHouseUse(inHouseUse)
      .create();

    final CheckInByBarcodeRequest request = CheckInByBarcodeRequest.from(requestJson).value();

    return new CheckInContext(request)
      .withItem(Item.from(new JsonObject()))
      .withRequestQueue(new RequestQueue(Collections.emptyList()));
  }
}
