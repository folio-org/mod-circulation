package org.folio.circulation.domain;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlHelper;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PatronGroupRepository {
  private final CollectionResourceClient patronGroupsStorageClient;
  private final String PATRON_GROUP_TYPE = "patrongroup";
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  
  public PatronGroupRepository(Clients clients) {
    patronGroupsStorageClient = clients.patronGroupsStorage();
  }
  
  public CompletableFuture<HttpResult<PatronGroup>> getPatronGroupById(String id) {
    return FetchSingleRecord.<PatronGroup>forRecord(PATRON_GROUP_TYPE)
        .using(patronGroupsStorageClient)
        .mapTo(PatronGroup::new)
        .whenNotFound(HttpResult.succeeded(null))
        .fetch(id);
  }
  
  public CompletableFuture<HttpResult<Request>> findPatronGroupsForSingleRequestUsers(HttpResult<Request> result) {
    List<String> clauses = new ArrayList<>();
    return result.after( request -> {
      if(request.getProxy() != null) {
        clauses.add(String.format("( id==%s )", request.getProxy().getPatronGroup()));
      }
      if(request.getRequester() != null) {
        clauses.add(String.format("( id==%s )", request.getRequester().getPatronGroup()));
      }
      if(clauses.isEmpty()) {
        return CompletableFuture.completedFuture(result);
      }
      final String pgQuery = String.join(" OR ", clauses);
      log.info(String.format("Querying patron groups with query %s", pgQuery));
      
      HttpResult<String> queryResult = CqlHelper.encodeQuery(pgQuery);
    
    return queryResult.after(query -> patronGroupsStorageClient.getMany(query)
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
        })));
    });
    //return result.after(request -> {return CompletableFuture.completedFuture(result);});
    
  }
  
  public CompletableFuture<HttpResult<MultipleRecords<Request>>> 
    findPatronGroupsForRequestsUsers(MultipleRecords<Request> multipleRequests) {
      
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
      }
    }
    if(clauses.isEmpty()) {
      log.info("No service points to query");
      return CompletableFuture.completedFuture(HttpResult.succeeded(multipleRequests));
    }
    
    final String pgQuery = String.join(" OR ", clauses);
    log.info(String.format("Querying patron groups with query %s", pgQuery));
    
    HttpResult<String> queryResult = CqlHelper.encodeQuery(pgQuery);
    
    return queryResult.after(query -> patronGroupsStorageClient.getMany(query)
      .thenApply(this::mapResponseToPatronGroups)
      .thenApply(multiplePatronGroupsResult -> multiplePatronGroupsResult.next(
        multiplePatronGroups -> {
          List<Request> newRequestList = new ArrayList<>();
          Collection<PatronGroup> pgCollection = multiplePatronGroups.getRecords();
          for(Request request : requests) {
            Boolean foundPG = false;
            String requesterPatronGroupId = request.getRequester() != null ?
                request.getRequester().getPatronGroup() :
                "";
            String proxyPatronGroupId = request.getProxy() != null ?
                request.getProxy().getPatronGroup() :
                "";
            Request newRequest = request;
            for(PatronGroup patronGroup : pgCollection ) {              
              if(requesterPatronGroupId.equals(patronGroup.getId())) {
                User newRequester = newRequest.getRequester().withPatronGroup(patronGroup);
                newRequest = newRequest.withRequester(newRequester);
                foundPG = true;
              }
              if(proxyPatronGroupId.equals(patronGroup.getId())) {
                User newProxy = newRequest.getProxy().withPatronGroup(patronGroup);
                newRequest = newRequest.withProxy(newProxy);
                foundPG = true;
              }
              if(foundPG) {
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
}
