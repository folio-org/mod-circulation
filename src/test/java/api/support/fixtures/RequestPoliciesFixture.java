package api.support.fixtures;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.folio.circulation.domain.RequestType;
import api.support.http.IndividualResource;

import api.support.builders.RequestPolicyBuilder;
import api.support.http.ResourceClient;

public class RequestPoliciesFixture {
  private final RecordCreator requestPolicyRecordCreator;

  public RequestPoliciesFixture(ResourceClient requestPoliciesClient) {
    requestPolicyRecordCreator = new RecordCreator(requestPoliciesClient,
      reason -> getProperty(reason, "name"));
  }

  public IndividualResource allowAllRequestPolicy() {

    ArrayList<RequestType> types = new ArrayList<>();
    types.add(RequestType.HOLD);
    types.add(RequestType.PAGE);
    types.add(RequestType.RECALL);

    final RequestPolicyBuilder allowAllPolicy = new RequestPolicyBuilder(types);

    return requestPolicyRecordCreator.createIfAbsent(allowAllPolicy);
  }

  public IndividualResource allowHoldAndRecallRequestPolicy() {
    ArrayList<RequestType> types = new ArrayList<>();
    types.add(RequestType.HOLD);
    types.add(RequestType.RECALL);

    final RequestPolicyBuilder policyBuilder = new RequestPolicyBuilder(types,
      "Page requests not allowed", "");

    return requestPolicyRecordCreator.createIfAbsent(policyBuilder);
  }

  public IndividualResource allowAllRequestPolicy(UUID id) {

    List<RequestType> types = new ArrayList<>();
    types.add(RequestType.HOLD);
    types.add(RequestType.PAGE);
    types.add(RequestType.RECALL);

    return requestPolicyRecordCreator.createIfAbsent(new RequestPolicyBuilder(types, id));
  }

  public IndividualResource customRequestPolicy(ArrayList<RequestType> types) {

    final RequestPolicyBuilder customPolicy = new RequestPolicyBuilder(types);
    return requestPolicyRecordCreator.createIfAbsent(customPolicy);
  }

  public IndividualResource customRequestPolicy(List<RequestType> types, String name, String description) {

    final RequestPolicyBuilder customPolicy = new RequestPolicyBuilder(types, name, description);
    return requestPolicyRecordCreator.createIfAbsent(customPolicy);
  }

  public IndividualResource recallRequestPolicy() {

    ArrayList<RequestType> requestTypesList = new ArrayList<>();
    requestTypesList.add(RequestType.RECALL);

    return customRequestPolicy(requestTypesList, "Recall request policy", "sample recall policy");
  }

  public IndividualResource holdRequestPolicy() {

    ArrayList<RequestType> requestTypesList = new ArrayList<>();
    requestTypesList.add(RequestType.HOLD);

    return customRequestPolicy(requestTypesList);
  }

  public IndividualResource pageRequestPolicy() {

    ArrayList<RequestType> requestTypesList = new ArrayList<>();
    requestTypesList.add(RequestType.PAGE);

    return customRequestPolicy(requestTypesList);
  }

  public IndividualResource createRequestPolicyWithAllowedServicePoints(
    Map<RequestType, Set<UUID>> allowedServicePoints, RequestType... requestType) {

    var policyId = UUID.randomUUID();
    var customPolicy = new RequestPolicyBuilder(policyId, Arrays.asList(requestType),
      "Example Request Policy" + policyId,
      "An example request policy with allowed Service Points", allowedServicePoints);

    return requestPolicyRecordCreator.createIfAbsent(customPolicy);
  }

  public IndividualResource nonRequestableRequestPolicy() {
    ArrayList<RequestType> types = new ArrayList<>();

    final RequestPolicyBuilder policyBuilder = new RequestPolicyBuilder(types,
      "Nothing is allowed", "");

    return requestPolicyRecordCreator.createIfAbsent(policyBuilder);
  }

  public void deleteRequestPolicy(IndividualResource policyToDelete) {
      requestPolicyRecordCreator.delete(policyToDelete);
  }

  public IndividualResource findRequestPolicy(String requestPolicyName) {
    return requestPolicyRecordCreator.getExistingRecord(requestPolicyName);
  }

  public void cleanUp() {
    requestPolicyRecordCreator.cleanUp();
  }

  public IndividualResource create(RequestPolicyBuilder requestPolicyBuilder) {
    return requestPolicyRecordCreator.createIfAbsent(requestPolicyBuilder);
  }
}
