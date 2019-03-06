package org.folio.circulation.rules;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Report a lexer or parser errors as a CirculationRulesException.
 */
public class ErrorListener extends BaseErrorListener {
  @Override
  public void syntaxError(Recognizer<?, ?> recognizer,
      Object offendingSymbol, int line, int charPositionInLine,
      String msg, RecognitionException e) {
    throw new CirculationRulesException(msg, line, charPositionInLine+1);
  }
}
