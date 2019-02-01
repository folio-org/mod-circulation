package api.support.http;

import static api.support.APITestContext.circulationModuleUrl;

import java.net.URL;
import java.util.UUID;

import api.support.APITestContext;

public class InterfaceUrls {
  static URL materialTypesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/material-types" + subPath);
  }

  static URL loanTypesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/loan-types" + subPath);
  }

  static URL institutionsStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/location-units/institutions" + subPath);
  }

  static URL campusesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/location-units/campuses" + subPath);
  }

  static URL librariesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/location-units/libraries" + subPath);
  }

  static URL locationsStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/locations" + subPath);
  }

  static URL instanceTypesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/instance-types" + subPath);
  }

  static URL contributorNameTypesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/contributor-name-types" + subPath);
  }

  static URL itemsStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/item-storage/items" + subPath);
  }

  static URL holdingsStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/holdings-storage/holdings" + subPath);
  }

  static URL instancesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/instance-storage/instances" + subPath);
  }

  static URL loansStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/loan-storage/loans" + subPath);
  }

  static URL loanPoliciesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/loan-policy-storage/loan-policies" + subPath);
  }

  static URL fixedDueDateSchedulesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/fixed-due-date-schedule-storage/fixed-due-date-schedules" + subPath);
  }

  static URL loanRulesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/loan-rules-storage" + subPath);
  }

  static URL usersUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/users" + subPath);
  }

  static URL calendarUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/calendar/periods"+ subPath);
  }

  static URL proxyRelationshipsUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/proxiesfor" + subPath);
  }

  static URL patronGroupsStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/groups" + subPath);
  }

  static URL addressTypesUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/addresstypes" + subPath);
  }

  public static URL requestsUrl() {
    return requestsUrl("");
  }

  public static URL requestsUrl(String subPath) {
    return circulationModuleUrl("/circulation/requests" + subPath);
  }

  public static URL requestQueueUrl(UUID itemId) {
    return requestsUrl(String.format("/queue/%s", itemId));
  }

  public static URL checkOutByBarcodeUrl() {
    return circulationModuleUrl("/circulation/check-out-by-barcode");
  }

  public static URL checkInByBarcodeUrl() {
    return circulationModuleUrl("/circulation/check-in-by-barcode");
  }

  public static URL renewByBarcodeUrl() {
    return circulationModuleUrl("/circulation/renew-by-barcode");
  }

  public static URL overrideRenewalByBarcodeUrl() {
    return circulationModuleUrl("/circulation/override-renewal-by-barcode");
  }

  public static URL renewByIdUrl() {
    return circulationModuleUrl("/circulation/renew-by-id");
  }

  public static URL loansUrl() {
    return loansUrl("");
  }

  public static URL loansUrl(String subPath) {
    return circulationModuleUrl("/circulation/loans" + subPath);
  }

  public static URL loanRulesUrl() {
    return loanRulesUrl("");
  }

  public static URL loanRulesUrl(String subPath) {
    return circulationModuleUrl("/circulation/loan-rules" + subPath);
  }

  static URL cancellationReasonsStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl(
        "/cancellation-reason-storage/cancellation-reasons" + subPath);
  }

  static URL servicePointsStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/service-points" + subPath);
  }
}
