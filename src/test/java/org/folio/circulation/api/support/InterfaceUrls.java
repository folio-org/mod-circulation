package org.folio.circulation.api.support;

import org.folio.circulation.api.APITestSuite;

import java.net.MalformedURLException;
import java.net.URL;

public class InterfaceUrls {
  public static URL materialTypesStorageUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/material-types" + subPath);
  }

  public static URL loanTypesStorageUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/loan-types" + subPath);
  }

  public static URL locationsStorageUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/shelf-locations" + subPath);
  }

  public static URL instanceTypesStorageUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/instance-types" + subPath);
  }

  public static URL creatorTypesStorageUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/creator-types" + subPath);
  }

  public static URL itemsStorageUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/item-storage/items" + subPath);
  }

  public static URL holdingsStorageUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/holdings-storage/holdings" + subPath);
  }

  public static URL loansStorageUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/loan-storage/loans" + subPath);
  }

  public static URL usersUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/users" + subPath);
  }

  public static URL requestsUrl()
    throws MalformedURLException {

    return requestsUrl("");
  }

  public static URL requestsUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.circulationModuleUrl("/circulation/requests" + subPath);
  }

  public static URL loansUrl()
    throws MalformedURLException {

    return loansUrl("");
  }

  public static URL loansUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.circulationModuleUrl("/circulation/loans" + subPath);
  }

  public static URL loanRulesURL() {
    return loanRulesURL("");
  }

  public static URL loanRulesURL(String subPath) {
    return APITestSuite.circulationModuleUrl("/circulation/loan-rules" + subPath);
  }
}
