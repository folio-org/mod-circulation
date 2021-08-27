package org.folio.circulation.services;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class LogCheckInServiceTest {
  @Mock
  private Clients clients;
  @Mock
  private CollectionResourceClient checkInStorageClient;
  private LogCheckInService logCheckInService;

  @BeforeEach
  public void setUp() {
    when(clients.checkInStorageClient())
      .thenReturn(checkInStorageClient);
    logCheckInService = new LogCheckInService(clients);
  }

  @Test
  void logCheckInOperationPropagatesException() {
    final CheckInContext context = checkInProcessRecords();

    final ServerErrorFailure postError = new ServerErrorFailure("ServerError");
    when(checkInStorageClient.post(any(JsonObject.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.failed(postError)));

    final CompletableFuture<Result<CheckInContext>> logCheckInOperation =
      logCheckInService.logCheckInOperation(context);

    final Result<CheckInContext> logResult = logCheckInOperation
      .getNow(Result.failed(new ServerErrorFailure("Uncompleted")));

    assertThat(logResult.failed(), is(true));
    assertThat(logResult.cause(), is(postError));
  }

  private CheckInContext checkInProcessRecords() {
    JsonObject requestRepresentation = new JsonObject()
      .put("servicePointId", UUID.randomUUID().toString())
      .put("itemBarcode", "barcode")
      .put("checkInDate", ClockUtil.getDateTime().toString());

    JsonObject itemRepresentation = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("status", new JsonObject().put("name", "Available"));

    return new CheckInContext(
      CheckInByBarcodeRequest.from(requestRepresentation).value())
      .withItem(Item.from(itemRepresentation))
      .withRequestQueue(new RequestQueue(Collections.emptyList()))
      .withLoggedInUserId(UUID.randomUUID().toString());
  }
}
