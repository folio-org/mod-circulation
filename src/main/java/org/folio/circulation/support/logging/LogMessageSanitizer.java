package org.folio.circulation.support.logging;

public class LogMessageSanitizer {
  private LogMessageSanitizer() { }

  /**
   * It is possible to inject incorrect information into a log
   * In order to reduce the chances of doing this,
   * any client provided parameters should be sanitized
   *
   * Sanitization approach is based upon guidance from Sonar
   * Could later be extended to use ESAPI tool - https://www.baeldung.com/jvm-log-forging
   *
   * See https://sonarcloud.io/organizations/folio-org/rules?open=javasecurity%3AS5145&rule_key=javasecurity%3AS5145
   * and https://www.owasp.org/index.php/Log_Injection
   *
   * @param parameterValue value of the parameter received from client input
   * @return a sanitized representation of the parameter value
   */
  public static String sanitizeLogParameter(String parameterValue) {
    return parameterValue
      .replace( '\n' , '_')
      .replace( '\r' , '_' )
      .replace( '\t' , '_' );
  }
}
