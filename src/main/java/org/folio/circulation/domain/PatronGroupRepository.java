package org.folio.circulation.domain;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlHelper;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class PatronGroupRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient patronGroupsStorageClient;

  PatronGroupRepository(Clients clients) {
    patronGroupsStorageClient = clients.patronGroupsStorage();
  }

  CompletableFuture<HttpResult<Request>> findPatronGroupsForSingleRequestUsers(
    HttpResult<Request> result) {

    return result.after(request -> {
      final ArrayList<String> groupsToFetch = getGroupsFromUsers(request);

      final String query = CqlHelper.multipleRecordsCqlQuery(groupsToFetch);

      return patronGroupsStorageClient.getMany(query)
        .thenApply(this::mapResponseToPatronGroups)
        .thenApply(multiplePatronGroupsResult -> multiplePatronGroupsResult.next(
          multiplePatronGroups -> {
            Request newRequest = request;
            Collection<PatronGroup> pgCollection = multiplePatronGroups.getRecords();
            String requesterPGId = request.getRequester() != null ?
              request.getRequester().getPatronGroup() :
              "";
            String proxyPGId = request.getProxy() != null ?
              request.getProxy().getPatronGroup() :
              "";
            for(PatronGroup patronGroup : pgCollection) {
              if(requesterPGId != null && requesterPGId.equals(patronGroup.getId())) {
                User newRequester = newRequest.getRequester().withPatronGroup(patronGroup);
                newRequest = newRequest.withRequester(newRequester);
              }
              if(proxyPGId != null && proxyPGId.equals(patronGroup.getId())) {
                User newProxy = newRequest.getProxy().withPatronGroup(patronGroup);
                newRequest = newRequest.withProxy(newProxy);
              }
            }
            return HttpResult.succeeded(newRequest);
          }));
    });
  }

  CompletableFuture<HttpResult<MultipleRecords<Request>>> findPatronGroupsForRequestsUsers(
    MultipleRecords<Request> multipleRequests) {

    Collection<Request> requests = multipleRequests.getRecords();
    List<String> clauses = new ArrayList<>();
    
    for(Request request : requests) {
      User proxy = request.getProxy();
      User requester = request.getRequester();
      List<String> userClauses = new ArrayList<>();
      
      if(proxy != null) {
        userClauses.add(String.format("( id==%s )", proxy.getPatronGroup()));
      }
      
      if(requester != null) {
        userClauses.add(String.format("( id==%s )", requester.getPatronGroup()));
      }
            
      if(proxy != null || requester != null) {
        String patronGroupClause = String.join(" OR ", userClauses);
        clauses.add(patronGroupClause);
      } else {
        log.info("No proxy or requester found for request {}", request.getId());
      }
    }
    if(clauses.isEmpty()) {
      log.info("No patron groups to query (multiple requests)");
      return CompletableFuture.completedFuture(HttpResult.succeeded(multipleRequests));
    }
    
    final String pgQuery = String.join(" OR ", clauses);
    log.info("Querying patron groups with query {}", pgQuery);
    
    HttpResult<String> queryResult = CqlHelper.encodeQuery(pgQuery);
    
    return queryResult.after(query -> patronGroupsStorageClient.getMany(query)
      .thenApply(this::mapResponseToPatronGroups)
      .thenApply(multiplePatronGroupsResult -> multiplePatronGroupsResult.next(
        multiplePatronGroups -> {
          List<Request> newRequestList = new ArrayList<>();
          Collection<PatronGroup> pgCollection = multiplePatronGroups.getRecords();
          log.info("Traversing {} patron group records", pgCollection.size());
          for(Request request : requests) {            
            String requesterPatronGroupId = request.getRequester() != null ?
                request.getRequester().getPatronGroup() :
                "";
            String proxyPatronGroupId = request.getProxy() != null ?
                request.getProxy().getPatronGroup() :
                "";
            Request newRequest = request;
            Boolean foundProxyPG = false;
            Boolean foundRequesterPG = false;
            for(PatronGroup patronGroup : pgCollection ) {              
              if(requesterPatronGroupId.equals(patronGroup.getId())) {
                User newRequester = newRequest.getRequester().withPatronGroup(patronGroup);                
                newRequest = newRequest.withRequester(newRequester);
                foundRequesterPG = true;
              }
              if(proxyPatronGroupId.equals(patronGroup.getId())) {
                User newProxy = newRequest.getProxy().withPatronGroup(patronGroup);                
                newRequest = newRequest.withProxy(newProxy);
                foundProxyPG = true;
              }
              if(foundRequesterPG && foundProxyPG) {                
                break;
              }
            }
            newRequestList.add(newRequest);
          }
          return HttpResult.succeeded(new MultipleRecords<>(newRequestList, multipleRequests.getTotalRecords()));
        })));
    
  }
    
  private HttpResult<MultipleRecords<PatronGroup>> mapResponseToPatronGroups(Response response) {
    return MultipleRecords.from(response, PatronGroup::from, "usergroups");
  }

  private ArrayList<String> getGroupsFromUsers(Request request) {
    final ArrayList<String> groupsToFetch = new ArrayList<>();

    if(request.getRequester() != null) {
      groupsToFetch.add(request.getRequester().getPatronGroup());
    }

    if(request.getProxy() != null) {
      groupsToFetch.add(request.getProxy().getPatronGroup());
    }
    
    return groupsToFetch;
  }

}
