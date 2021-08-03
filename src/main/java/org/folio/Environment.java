package org.folio;

public class Environment {
  private Environment() { }

  public static int getScheduledAnonymizationNumberOfLoansToCheck() {
    return Integer.parseInt(System.getenv()
      .getOrDefault("SCHEDULED_ANONYMIZATION_NUMBER_OF_LOANS_TO_CHECK", "50000"));
  }
}
