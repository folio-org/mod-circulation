package org.folio.circulation.rules;

/**
 * Exception about an error in circulation rules.
 */
public class CirculationRulesException extends IllegalArgumentException {
  private static final long serialVersionUID = 243533650859582936L;
  private final int line;
  private final int column;

  /**
   * Create an exception about an error in circulation rules.
   *
   * @param message  description of the error
   * @param line  line of the error
   * @param column  column of the error
   */
  public CirculationRulesException(String message, int line, int column) {
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
