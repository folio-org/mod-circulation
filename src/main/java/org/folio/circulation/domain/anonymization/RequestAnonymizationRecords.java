package org.folio.circulation.domain.anonymization;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.circulation.domain.Request;

public class RequestAnonymizationRecords {

  public static final String CAN_BE_ANONYMIZED_KEY = "_";

  private List<String> anonymizedRequestIds = new ArrayList<>();
  private List<Request> requestsFound = new ArrayList<>();
  private Map<String, Collection<String>> notAnonymizedRequests = new HashMap<>();

  public List<Request> getRequestsFound() {
    return requestsFound;
  }

  public RequestAnonymizationRecords withRequestsFound(Collection<Request> requests) {
    if (CollectionUtils.isEmpty(requests)) {
      return this;
    }

    RequestAnonymizationRecords newRecords = new RequestAnonymizationRecords();
    newRecords.requestsFound = new ArrayList<>(requests);
    newRecords.anonymizedRequestIds = new ArrayList<>(anonymizedRequestIds);
    newRecords.notAnonymizedRequests = new HashMap<>(notAnonymizedRequests);

    return newRecords;
  }

  public RequestAnonymizationRecords withAnonymizedRequests(Collection<String> requestIds) {
    if (CollectionUtils.isEmpty(requestIds)) {
      return this;
    }

    RequestAnonymizationRecords newRecords = new RequestAnonymizationRecords();
    newRecords.requestsFound = new ArrayList<>(requestsFound);
    newRecords.anonymizedRequestIds = new ArrayList<>(requestIds);
    newRecords.notAnonymizedRequests = new HashMap<>(notAnonymizedRequests);

    return newRecords;
  }

  public RequestAnonymizationRecords withNotAnonymizedRequests(
    Map<String, Set<String>> requestsByReason) {

    if (requestsByReason == null || requestsByReason.isEmpty()) {
      return this;
    }

    RequestAnonymizationRecords newRecords = new RequestAnonymizationRecords();
    newRecords.requestsFound = new ArrayList<>(requestsFound);
    newRecords.anonymizedRequestIds = new ArrayList<>(anonymizedRequestIds);
    newRecords.notAnonymizedRequests = new HashMap<>(requestsByReason);

    return newRecords;
  }

  public List<String> getAnonymizedRequestIds() {
    return anonymizedRequestIds;
  }

  public List<Request> getAnonymizedRequests() {
    return requestsFound.stream()
      .filter(request -> anonymizedRequestIds.contains(request.getId()))
      .toList();
  }

  public Map<String, Collection<String>> getNotAnonymizedRequests() {
    return notAnonymizedRequests;
  }

  @Override
  public String toString() {
    return "RequestAnonymizationRecords(" +
      "anonymizedRequestIds=" + anonymizedRequestIds +
      ", requestsFound=" + requestsFound +
      ", notAnonymizedRequests=" + notAnonymizedRequests +
      ")";
  }
}
