package org.folio.circulation.loanrules;

/**
 * Exception about an error in loan rules.
 */
public class LoanRulesException extends IllegalArgumentException {
  private static final long serialVersionUID = 243533650859582936L;
  private int line;
  private int column;

  /**
   * Create an exception about an error in loan rules.
   *
   * @param message  description of the error
   * @param line  line of the error
   * @param column  column of the error
   */
  public LoanRulesException(String message, int line, int column) {
    super(message);
    this.line = line;
    this.column = column;
  }

  /**
   * @return line of the error
   */
  public int getLine() {
    return line;
  }

  /**
   * @return column of the error
   */
  public int getColumn() {
    return column;
  }
}
