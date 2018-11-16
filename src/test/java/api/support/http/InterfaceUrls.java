package api.support.http;

import java.net.URL;
import java.util.UUID;

import api.APITestSuite;

public class InterfaceUrls {
  static URL materialTypesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/material-types" + subPath);
  }

  static URL loanTypesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/loan-types" + subPath);
  }

  static URL institutionsStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/location-units/institutions" + subPath);
  }

  static URL campusesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/location-units/campuses" + subPath);
  }

  static URL librariesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/location-units/libraries" + subPath);
  }

  static URL locationsStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/locations" + subPath);
  }

  static URL instanceTypesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/instance-types" + subPath);
  }

  static URL contributorNameTypesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/contributor-name-types" + subPath);
  }

  static URL itemsStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/item-storage/items" + subPath);
  }

  static URL holdingsStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/holdings-storage/holdings" + subPath);
  }

  static URL instancesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/instance-storage/instances" + subPath);
  }

  static URL loansStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/loan-storage/loans" + subPath);
  }

  static URL loanPoliciesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/loan-policy-storage/loan-policies" + subPath);
  }

  static URL fixedDueDateSchedulesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/fixed-due-date-schedule-storage/fixed-due-date-schedules" + subPath);
  }

  static URL loanRulesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/loan-rules-storage" + subPath);
  }

  static URL usersUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/users" + subPath);
  }

  static URL proxyRelationshipsUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/proxiesfor" + subPath);
  }

  static URL groupsUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/groups" + subPath);
  }

  static URL addressTypesUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/addresstypes" + subPath);
  }

  public static URL requestsUrl() {
    return requestsUrl("");
  }

  public static URL requestsUrl(String subPath) {
    return APITestSuite.circulationModuleUrl("/circulation/requests" + subPath);
  }

  public static URL requestQueueUrl(UUID itemId) {
    return requestsUrl(String.format("/queue/%s", itemId));
  }

  public static URL checkOutByBarcodeUrl() {
    return APITestSuite.circulationModuleUrl("/circulation/check-out-by-barcode");
  }

  public static URL renewByBarcodeUrl() {
    return APITestSuite.circulationModuleUrl("/circulation/renew-by-barcode");
  }

  public static URL renewByIdUrl() {
    return APITestSuite.circulationModuleUrl("/circulation/renew-by-id");
  }

  public static URL loansUrl() {
    return loansUrl("");
  }

  public static URL loansUrl(String subPath) {
    return APITestSuite.circulationModuleUrl("/circulation/loans" + subPath);
  }

  public static URL loanRulesUrl() {
    return loanRulesUrl("");
  }

  public static URL loanRulesUrl(String subPath) {
    return APITestSuite.circulationModuleUrl("/circulation/loan-rules" + subPath);
  }

  public static URL cancellationReasonsStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl(
        "/cancellation-reason-storage/cancellation-reasons" + subPath);
  }

  public static URL cancellationReasonsStorageUrl() {
    return cancellationReasonsStorageUrl("");
  }
  
  public static URL servicePointsStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/service-points" + subPath);
  }
}
