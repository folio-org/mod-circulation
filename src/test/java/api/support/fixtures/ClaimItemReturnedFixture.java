package api.support.fixtures;

import static api.support.http.InterfaceUrls.claimItemReturnedURL;
import static api.support.http.InterfaceUrls.declareClaimedReturnedItemAsMissingUrl;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.builders.DeclareItemClaimedReturnedAsMissingRequestBuilder;

public class ClaimItemReturnedFixture {
  private final RestAssuredClient restAssuredClient;

  public ClaimItemReturnedFixture(RestAssuredClient restAssuredClient) {
    this.restAssuredClient = restAssuredClient;
  }

  public Response claimItemReturned(ClaimItemReturnedRequestBuilder request) {
    return restAssuredClient.post(request.create(),
      claimItemReturnedURL(request.getLoanId()), 204, "claim-item-returned-request");
  }

  public Response attemptClaimItemReturned(ClaimItemReturnedRequestBuilder request) {
    return restAssuredClient.post(request.create(),
      claimItemReturnedURL(request.getLoanId()), "attempt-claim-item-returned-request");
  }

  public Response declareItemClaimedReturnedAsMissing(
    DeclareItemClaimedReturnedAsMissingRequestBuilder request) {

    return restAssuredClient.post(request.create(),
      declareClaimedReturnedItemAsMissingUrl(request.getLoanId()), 204,
      "declare-item-claimed-returned-as-missing-request");
  }

  public Response attemptDeclareItemClaimedReturnedAsMissing(
    DeclareItemClaimedReturnedAsMissingRequestBuilder request) {

    return restAssuredClient.post(request.create(),
      declareClaimedReturnedItemAsMissingUrl(request.getLoanId()),
      "attempt-declare-item-claimed-returned-as-missing-request");
  }
}
