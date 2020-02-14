package org.folio.circulation.services;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
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
  @Mock
  private Clients clients;
  @Mock
  private CollectionResourceClient checkInStorageClient;
  private CheckInOperationService checkInOperationService;

  @Before
  public void setUp() {
    when(clients.checkInOperationStorageClient())
      .thenReturn(checkInStorageClient);

    when(checkInStorageClient.post(any(JsonObject.class)))
      .thenAnswer(emulateCheckInOperationStorageOutage());
    checkInOperationService = new CheckInOperationService(clients);
  }

  @Test
  public void logCheckInOperationIsNotBlocked() {
    try {
      final CheckInProcessRecords context = checkInProcessRecords();

      Result<CheckInProcessRecords> result = checkInOperationService.logCheckInOperation(context)
        .get(3000, TimeUnit.MILLISECONDS);

      assertThat(result.succeeded(), is(true));
      assertThat(result.value(), is(context));
    } catch (Exception ex) {
      ex.printStackTrace();
      fail("Expecting that recording of check-in operation won't block check-in flow");
    }
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

  private Answer<CompletableFuture<JsonObject>> emulateCheckInOperationStorageOutage() {
    return invocationOnMock -> {
      Thread.sleep(5000);
      return CompletableFuture.completedFuture(invocationOnMock.getArgument(0));
    };
  }

}
