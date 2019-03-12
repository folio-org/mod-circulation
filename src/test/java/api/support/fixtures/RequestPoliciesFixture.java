package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.RequestPolicyBuilder;
import api.support.http.ResourceClient;

public class RequestPoliciesFixture {
  private final RecordCreator requestPolicyRecordCreator;

  public RequestPoliciesFixture(ResourceClient requestPoliciesClient) {
    requestPolicyRecordCreator = new RecordCreator(requestPoliciesClient,
      reason -> getProperty(reason, "name"));
  }

  public IndividualResource allowAllRequestPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    ArrayList<RequestType> types = new ArrayList<>();
    types.add(RequestType.HOLD);
    types.add(RequestType.PAGE);
    types.add(RequestType.RECALL);

    final RequestPolicyBuilder allowAllPolicy = new RequestPolicyBuilder(types);

    return requestPolicyRecordCreator.createIfAbsent(allowAllPolicy);
  }

  public IndividualResource customRequestPolicy(ArrayList<RequestType> types)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final RequestPolicyBuilder customPolicy = new RequestPolicyBuilder(types);
    return requestPolicyRecordCreator.createIfAbsent(customPolicy);
  }

  public IndividualResource customRequestPolicy(ArrayList<RequestType> types, String name, String description)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final RequestPolicyBuilder customPolicy = new RequestPolicyBuilder(types, name, description);
    return requestPolicyRecordCreator.createIfAbsent(customPolicy);
  }

  public IndividualResource recallRequestPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    ArrayList<RequestType> requestTypesList = new ArrayList<>();
    requestTypesList.add(RequestType.RECALL);

    return customRequestPolicy(requestTypesList, "Recall request policy", "sample recall policy");
  }

  public IndividualResource holdRequestPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    ArrayList<RequestType> requestTypesList = new ArrayList<>();
    requestTypesList.add(RequestType.HOLD);

    return customRequestPolicy(requestTypesList);
  }

  public IndividualResource pageRequestPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    ArrayList<RequestType> requestTypesList = new ArrayList<>();
    requestTypesList.add(RequestType.PAGE);

    return customRequestPolicy(requestTypesList);
  }

  public void deleteRequestPolicy(IndividualResource policyToDelete)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
      requestPolicyRecordCreator.delete(policyToDelete);
  }

  public IndividualResource findRequestPolicy(String requestPolicyName) {
    return requestPolicyRecordCreator.getExistingRecord(requestPolicyName);
  }
}
