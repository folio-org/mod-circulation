package org.folio.circulation.api.support.http;

import org.folio.circulation.api.APITestSuite;

import java.net.URL;

public class InterfaceUrls {
  public static URL materialTypesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/material-types" + subPath);
  }

  public static URL loanTypesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/loan-types" + subPath);
  }

  public static URL shelfLocationsStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/shelf-locations" + subPath);
  }

  public static URL institutionsStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/location-units/institutions" + subPath);
  }

  public static URL instanceTypesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/instance-types" + subPath);
  }

  public static URL contributorNameTypesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/contributor-name-types" + subPath);
  }

  public static URL itemsStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/item-storage/items" + subPath);
  }

  public static URL holdingsStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/holdings-storage/holdings" + subPath);
  }

  public static URL instancesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/instance-storage/instances" + subPath);
  }

  public static URL loansStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/loan-storage/loans" + subPath);
  }

  public static URL loanPoliciesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/loan-policy-storage/loan-policies" + subPath);
  }

  public static URL loanRulesStorageUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/loan-rules-storage" + subPath);
  }

  public static URL usersUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/users" + subPath);
  }

  public static URL usersProxyUrl(String subPath) {
      return APITestSuite.viaOkapiModuleUrl("/proxiesfor" + subPath);
    }

  public static URL groupsUrl(String subPath) {
    return APITestSuite.viaOkapiModuleUrl("/groups" + subPath);
  }

  public static URL requestsUrl() {
    return requestsUrl("");
  }

  public static URL requestsUrl(String subPath) {
    return APITestSuite.circulationModuleUrl("/circulation/requests" + subPath);
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
}
