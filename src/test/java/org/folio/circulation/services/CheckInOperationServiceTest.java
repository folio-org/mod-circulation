package org.folio.circulation.services;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.CheckInProcessRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.representations.CheckInByBarcodeRequest;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import io.vertx.core.json.JsonObject;

@RunWith(MockitoJUnitRunner.class)
public class CheckInOperationServiceTest {
  private static final int CHECK_IN_SERVER_TIMEOUT = 200;

  @Mock
  private Clients clients;
  @Mock
  private CollectionResourceClient checkInStorageClient;
  private CheckInOperationService checkInOperationService;
  private volatile Result<Response> spyLastResult;

  @Before
  public void setUp() {
    when(clients.checkInOperationStorageClient())
      .thenReturn(checkInStorageClient);
    checkInOperationService = new CheckInOperationService(clients);
  }

  @Test
  public void logCheckInOperationIsNotBlocked() {
    final CheckInProcessRecords context = checkInProcessRecords();

    when(checkInStorageClient.post(any(JsonObject.class)))
      .thenAnswer(checkInProcessTakesLongTime());

    final CompletableFuture<Result<CheckInProcessRecords>> logCheckInOperation =
      checkInOperationService.logCheckInOperation(context);

    final Result<CheckInProcessRecords> logResult = logCheckInOperation
      .getNow(Result.failed(new ServerErrorFailure("Uncompleted")));

    assertThat(logResult.succeeded(), is(true));
    assertThat(logResult.value(), is(context));

    Awaitility.await()
      .atMost(500, TimeUnit.MILLISECONDS)
      .until(() -> spyLastResult != null && spyLastResult.value() != null);

    verify(spyLastResult, times(1)).failed();
    // Verify the logging is not executed for successful result.
    verify(spyLastResult, never()).cause();
  }

  @Test
  public void logCheckInOperationFailureDoNotStopCheckInFlow() {
    final CheckInProcessRecords context = checkInProcessRecords();

    when(checkInStorageClient.post(any(JsonObject.class)))
      .thenAnswer(invocationOnMock -> {
        final ServerErrorFailure serverError = spy(new ServerErrorFailure("Server error"));
        spyLastResult = spy(Result.failed(serverError));
        return CompletableFuture.completedFuture(spyLastResult);
      });

    final CompletableFuture<Result<CheckInProcessRecords>> logCheckInOperation =
      checkInOperationService.logCheckInOperation(context);

    final Result<CheckInProcessRecords> logResult = logCheckInOperation
      .getNow(Result.failed(new ServerErrorFailure("Uncompleted")));

    assertThat(logResult.succeeded(), is(true));
    assertThat(logResult.value(), is(context));

    Awaitility.await()
      .atMost(500, TimeUnit.MILLISECONDS)
      .until(() -> spyLastResult != null && spyLastResult.value() == null);

    verify(spyLastResult, times(1)).failed();
    // Verify the logging is not executed for successful result.
    verify(spyLastResult, times(1)).cause();
    verify(spyLastResult.cause(), times(1)).getReason();
  }

  private CheckInProcessRecords checkInProcessRecords() {
    JsonObject requestRepresentation = new JsonObject()
      .put("servicePointId", UUID.randomUUID().toString())
      .put("itemBarcode", "barcode")
      .put("checkInDate", DateTime.now().toString());

    return new CheckInProcessRecords(
      CheckInByBarcodeRequest.from(requestRepresentation).value())
      .withItem(Item.from(new JsonObject().put("id", UUID.randomUUID().toString())))
      .withLoggedInUserId(UUID.randomUUID().toString());
  }

  private Answer<CompletableFuture<Result<Response>>> checkInProcessTakesLongTime() {
    return invocationOnMock -> {
      Thread.sleep(CHECK_IN_SERVER_TIMEOUT);

      final JsonObject json = invocationOnMock.getArgument(0);
      final Response response = new Response(201, json.toString(),
        "application/json");

      spyLastResult = spy(Result.succeeded(response));
      return CompletableFuture.completedFuture(spyLastResult);
    };
  }
}
