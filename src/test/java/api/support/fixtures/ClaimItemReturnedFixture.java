package api.support.fixtures;

import static api.support.http.InterfaceUrls.claimItemReturnedURL;
import static api.support.http.InterfaceUrls.declareClaimedReturnedItemAsMissingUrl;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.builders.DeclareClaimedReturnedItemAsMissingRequestBuilder;

public class ClaimItemReturnedFixture {
  private final RestAssuredClient restAssuredClient;

  public ClaimItemReturnedFixture(RestAssuredClient restAssuredClient) {
    this.restAssuredClient = restAssuredClient;
  }

  public Response claimItemReturned(UUID loanId) {
    return claimItemReturned(new ClaimItemReturnedRequestBuilder().forLoan(loanId));
  }

  public Response claimItemReturned(ClaimItemReturnedRequestBuilder request) {
    return restAssuredClient.post(request.create(),
      claimItemReturnedURL(request.getLoanId()), 204, "claim-item-returned-request");
  }

  public Response attemptClaimItemReturned(ClaimItemReturnedRequestBuilder request) {
    return restAssuredClient.post(request.create(),
      claimItemReturnedURL(request.getLoanId()), "attempt-claim-item-returned-request");
  }

  public Response declareClaimedReturnedItemAsMissing(
    DeclareClaimedReturnedItemAsMissingRequestBuilder request) {

    return restAssuredClient.post(request.create(),
      declareClaimedReturnedItemAsMissingUrl(request.getLoanId()), 204,
      "declare-claimed-returned-item-as-missing-request");
  }

  public Response attemptDeclareClaimedReturnedItemAsMissing(
    DeclareClaimedReturnedItemAsMissingRequestBuilder request) {

    return restAssuredClient.post(request.create(),
      declareClaimedReturnedItemAsMissingUrl(request.getLoanId()),
      "attempt-declare-claimed-returned-item-as-missing-request");
  }
}
