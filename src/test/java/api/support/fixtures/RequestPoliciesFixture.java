package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.RequestPolicyBuilder;
import api.support.http.ResourceClient;

public class RequestPoliciesFixture {
  private final RecordCreator requestPolicyRecordCreator;

  public RequestPoliciesFixture(ResourceClient requestPoliciesClient) {
    requestPolicyRecordCreator = new RecordCreator(requestPoliciesClient,
      reason -> getProperty(reason, "name"));
  }

  public IndividualResource noAllowedTypes()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    ArrayList<RequestType> types = new ArrayList<>();
    types.add(RequestType.HOLD);
    types.add(RequestType.PAGE);
    types.add(RequestType.RECALL);
    types.add(RequestType.NONE);

    final RequestPolicyBuilder noAllowedTypesPolicy = new RequestPolicyBuilder(types);

    return requestPolicyRecordCreator.createIfAbsent(noAllowedTypesPolicy);
  }
}
