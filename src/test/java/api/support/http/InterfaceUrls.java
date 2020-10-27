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

  public static URL itemsStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/item-storage/items" + subPath);
  }

  public static URL holdingsStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/holdings-storage/holdings" + subPath);
  }

  static URL instancesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/instance-storage/instances" + subPath);
  }

  public static URL loansStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/loan-storage/loans" + subPath);
  }

  public static URL loanHistoryStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/loan-storage/loan-history" + subPath);
  }

  static URL requestStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/request-storage/requests" + subPath);
  }

  static URL loanPoliciesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/loan-policy-storage/loan-policies" + subPath);
  }

  static URL requestPoliciesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/request-policy-storage/request-policies" + subPath);
  }

  static URL noticePoliciesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/patron-notice-policy-storage/patron-notice-policies" + subPath);
  }

  static URL overdueFinesPoliciesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/overdue-fines-policies" + subPath);
  }

  static URL lostItemFeesPoliciesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/lost-item-fees-policies" + subPath);
  }

  static URL fixedDueDateSchedulesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/fixed-due-date-schedule-storage/fixed-due-date-schedules" + subPath);
  }

  public static URL circulationRulesStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/circulation-rules-storage" + subPath);
  }

  public static URL usersUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/users" + subPath);
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

  public static URL requestReportUrl(String subPath) {
    return circulationModuleUrl(
      "/circulation/requests-reports/hold-shelf-clearance" + subPath);
  }

  public static URL itemsInTransitReportUrl(String subPath) {
    return circulationModuleUrl("/inventory-reports/items-in-transit" + subPath);
  }

  public static URL itemsLostRequringActualCostsUrl(String subPath) {
    return circulationModuleUrl("/inventory-reports/items-lost-requiring-actual-costs" + subPath);
  }

   static URL pickSlipsUrl(String servicePointId) {
    return circulationModuleUrl("/circulation/pick-slips"  + servicePointId);
  }

  public static URL requestQueueUrl(UUID itemId) {
    return requestsUrl(String.format("/queue/%s", itemId));
  }

  public static URL checkOutByBarcodeUrl() {
    return circulationModuleUrl("/circulation/check-out-by-barcode");
  }

  public static URL overrideCheckOutByBarcodeUrl() {
    return circulationModuleUrl("/circulation/override-check-out-by-barcode");
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

  public static URL circulationAnonymizeLoansURL(String subPath) {
    return circulationModuleUrl("/loan-anonymization/by-user/" + subPath);
  }

  public static URL circulationAnonymizeLoansInTenantURL() {
    return circulationModuleUrl("/circulation/scheduled-anonymize-processing/");
  }

  public static URL declareLoanItemLostURL(String loanId) {
    return circulationModuleUrl(String.format("/circulation/loans/%s/declare-item-lost", loanId));
  }

  public static URL changeDueDateURL(String loanId) {
    return circulationModuleUrl(String.format("/circulation/loans/%s/change-due-date", loanId));
  }

  public static URL claimItemReturnedURL(String loanId) {
    return circulationModuleUrl(String.format("/circulation/loans/%s/claim-item-returned", loanId));
  }

  public static URL declareClaimedReturnedItemAsMissingUrl(String loanId) {
    return circulationModuleUrl(String.format(
      "/circulation/loans/%s/declare-claimed-returned-item-as-missing", loanId));
  }

  public static URL endSessionUrl() {
    return circulationModuleUrl("/circulation/end-patron-action-session");
  }

  public static URL accountsUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/accounts" + subPath);
  }

  public static URL feeFineActionsUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/feefineactions" + subPath);
  }

  public static URL feeFineOwnersUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/owners" + subPath);
  }

  public static URL feeFinesUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/feefines" + subPath);
  }

  public static URL circulationRulesUrl() {
    return circulationRulesUrl("");
  }

  public static URL circulationRulesUrl(String subPath) {
    return circulationModuleUrl("/circulation/rules" + subPath);
  }

  static URL cancellationReasonsStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl(
        "/cancellation-reason-storage/cancellation-reasons" + subPath);
  }

  static URL servicePointsStorageUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/service-points" + subPath);
  }

  static URL patronNoticesUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/patron-notice" + subPath);
  }

  static URL scheduledNoticesUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/scheduled-notice-storage/scheduled-notices" + subPath);
  }

  static URL patronActionSessionsUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/patron-action-session-storage/patron-action-sessions" + subPath);
  }

  static URL configurationUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/configurations/entries" + subPath);
  }

  static URL patronExpiredSessionsUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/patron-action-session-storage/expired-session-patron-ids");
  }

  public static URL reorderQueueUrl(String itemId) {
    return requestQueueUrl(itemId + "/reorder");
  }

  public static URL requestQueueUrl(String itemId) {
    return circulationModuleUrl(String
      .format("/circulation/requests/queue/%s", itemId));
  }

  static URL userManualBlocksStorageUrl() {
    return APITestContext.viaOkapiModuleUrl("/manualblocks");
  }

  static URL automatedPatronBlocksStorageUrl() {
    return APITestContext.viaOkapiModuleUrl("/automated-patron-blocks");
  }

  static URL identifierTypesUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/identifier-types" + subPath);
  }

  static URL templateUrl(String templateId) {
    return APITestContext.viaOkapiModuleUrl("/templates");
  }

  public static URL notesUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/notes");
  }

  public static URL noteTypesUrl(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/note-types");
  }

  public static URL checkInStorage(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/check-in-storage/check-ins" + subPath);
  }

  public static URL tenantStorage(String subPath) {
    return APITestContext.viaOkapiModuleUrl("/_/tenant");
  }

  public static URL scheduledAgeToLostUrl() {
    return circulationModuleUrl("/circulation/scheduled-age-to-lost");
  }

  public static URL scheduledAgeToLostFeeChargingUrl() {
    return circulationModuleUrl("/circulation/scheduled-age-to-lost-fee-charging");
  }
}
