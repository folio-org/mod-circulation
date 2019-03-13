package org.folio.circulation.support;

import io.vertx.core.http.HttpClient;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.server.WebContext;

import java.net.MalformedURLException;

public class Clients {
  private final CollectionResourceClient requestsStorageClient;
  private final CollectionResourceClient itemsStorageClient;
  private final CollectionResourceClient holdingsStorageClient;
  private final CollectionResourceClient instancesStorageClient;
  private final CollectionResourceClient usersStorageClient;
  private final CollectionResourceClient loansStorageClient;
  private final CollectionResourceClient locationsStorageClient;
  private final CollectionResourceClient materialTypesStorageClient;
  private final CollectionResourceClient proxiesForClient;
  private final CollectionResourceClient loanPoliciesStorageClient;
  private final CollectionResourceClient fixedDueDateSchedulesStorageClient;
  private final CirculationRulesClient circulationRulesClient;
  private final CollectionResourceClient circulationRulesStorageClient;
  private final CollectionResourceClient servicePointsStorageClient;
  private final CollectionResourceClient calendarStorageClient;
  private final CollectionResourceClient patronGroupsStorageClient;
  private final CollectionResourceClient configurationStorageClient;

  public static Clients create(WebContext context, HttpClient httpClient) {
    return new Clients(context.createHttpClient(httpClient), context);
  }

  private Clients(OkapiHttpClient client, WebContext context) {
    try {
      requestsStorageClient = createRequestsStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      holdingsStorageClient = createHoldingsStorageClient(client, context);
      instancesStorageClient = createInstanceStorageClient(client, context);
      usersStorageClient = createUsersStorageClient(client, context);
      loansStorageClient = createLoansStorageClient(client, context);
      locationsStorageClient = createLocationsStorageClient(client, context);
      materialTypesStorageClient = createMaterialTypesStorageClient(client, context);
      proxiesForClient = createProxyUsersStorageClient(client, context);
      circulationRulesClient = new CirculationRulesClient(client, context);
      circulationRulesStorageClient = createCirculationRulesStorageClient(client, context);
      loanPoliciesStorageClient = createLoanPoliciesStorageClient(client, context);
      fixedDueDateSchedulesStorageClient = createFixedDueDateSchedulesStorageClient(client, context);
      servicePointsStorageClient = createServicePointsStorageClient(client, context);
      patronGroupsStorageClient = createPatronGroupsStorageClient(client, context);
      calendarStorageClient = createCalendarStorageClient(client, context);
      configurationStorageClient = createConfigurationStorageClient(client, context);
    }
    catch(MalformedURLException e) {
      throw new InvalidOkapiLocationException(context.getOkapiLocation(), e);
    }
  }

  public CollectionResourceClient requestsStorage() {
    return requestsStorageClient;
  }

  public CollectionResourceClient itemsStorage() {
    return itemsStorageClient;
  }

  CollectionResourceClient holdingsStorage() {
    return holdingsStorageClient;
  }

  CollectionResourceClient instancesStorage() {
    return instancesStorageClient;
  }

  public CollectionResourceClient usersStorage() {
    return usersStorageClient;
  }

  public CollectionResourceClient loansStorage() {
    return loansStorageClient;
  }

  public CollectionResourceClient locationsStorage() {
    return locationsStorageClient;
  }

  public CollectionResourceClient materialTypesStorage() {
    return materialTypesStorageClient;
  }

  public CollectionResourceClient loanPoliciesStorage() {
    return loanPoliciesStorageClient;
  }

  public CollectionResourceClient fixedDueDateSchedules() {
    return fixedDueDateSchedulesStorageClient;
  }
  
  public CollectionResourceClient servicePointsStorage() {
    return servicePointsStorageClient;
  }
  
  public CollectionResourceClient patronGroupsStorage() {
    return patronGroupsStorageClient;
  }

  public CollectionResourceClient calendarStorageClient() {
    return calendarStorageClient;
  }

  public CollectionResourceClient configurationStorageClient() {
    return configurationStorageClient;
  }

  public CollectionResourceClient userProxies() {
    return proxiesForClient;
  }

  public CirculationRulesClient circulationRules() {
    return circulationRulesClient;
  }

  public CollectionResourceClient circulationRulesStorage() {
    return circulationRulesStorageClient;
  }

  private static CollectionResourceClient getCollectionResourceClient(
    OkapiHttpClient client,
    WebContext context,
    String path)
    throws MalformedURLException {

    return new CollectionResourceClient(client, context.getOkapiBasedUrl(path));
  }

  private static CollectionResourceClient createRequestsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/request-storage/requests");
  }

  private static CollectionResourceClient createItemsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/item-storage/items");
  }

  private static CollectionResourceClient createHoldingsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/holdings-storage/holdings"));
  }

  private static CollectionResourceClient createInstanceStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/instance-storage/instances"));
  }

  private static CollectionResourceClient createUsersStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/users");
  }

  private static CollectionResourceClient createLoansStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/loan-storage/loans");
  }

  private static CollectionResourceClient createLocationsStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/locations");
  }

  private CollectionResourceClient createProxyUsersStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/proxiesfor");
  }

  private CollectionResourceClient createMaterialTypesStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/material-types");
  }

  private CollectionResourceClient createLoanPoliciesStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/loan-policy-storage/loan-policies");
  }

  private CollectionResourceClient createFixedDueDateSchedulesStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/fixed-due-date-schedule-storage/fixed-due-date-schedules");
  }

  private CollectionResourceClient createCirculationRulesStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/circulation-rules-storage");
  }

  private CollectionResourceClient createServicePointsStorageClient(
      OkapiHttpClient client,
      WebContext context)
      throws MalformedURLException {
    return getCollectionResourceClient(client, context, "/service-points");
  }
  
  private CollectionResourceClient createPatronGroupsStorageClient(
      OkapiHttpClient client,
      WebContext context)
      throws MalformedURLException {
    return getCollectionResourceClient(client, context, "/groups");
  }

  private CollectionResourceClient createCalendarStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {
    return getCollectionResourceClient(client, context, "/calendar/periods");
  }

  private CollectionResourceClient createConfigurationStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {
    return getCollectionResourceClient(client, context, "/configurations/entries");
  }
  
}
