package api.support.fixtures;

import static api.APITestSuite.createReferenceRecord;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.http.ResourceClient;

public class LoanTypesFixture {
  private UUID canCirculateLoanTypeId;
  private UUID readingRoomLoanTypeId;

  private final ResourceClient loanTypesClient;

  public LoanTypesFixture(ResourceClient loanTypesClient) {
    this.loanTypesClient = loanTypesClient;
  }

  public UUID readingRoom()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    if(readingRoomLoanTypeId == null) {
      readingRoomLoanTypeId = createReferenceRecord(loanTypesClient,
        "Reading Room");
    }

    return readingRoomLoanTypeId;
  }

  public UUID canCirculate()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    if(canCirculateLoanTypeId == null) {
      canCirculateLoanTypeId = createReferenceRecord(loanTypesClient,
        "Can Circulate");
    }

    return canCirculateLoanTypeId;
  }

  public void cleanUp()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    if(canCirculateLoanTypeId != null) {
      loanTypesClient.delete(canCirculateLoanTypeId);
      canCirculateLoanTypeId = null;
    }

    if(readingRoomLoanTypeId != null) {
      loanTypesClient.delete(readingRoomLoanTypeId);
      readingRoomLoanTypeId = null;
    }
  }
}
