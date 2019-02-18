package org.folio.circulation.rules;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

/**
 * Match a CirculationrulesRulesException that has the same line and column and contains the message as substring.
 */
public class CirculationRulesExceptionMatcher extends TypeSafeDiagnosingMatcher<CirculationRulesException> {

  private CirculationRulesException expected;

  /**
   * @param expected  match against line, column and message of expected
   */
  public CirculationRulesExceptionMatcher(CirculationRulesException expected) {
    this.expected = expected;
  }

  /**
   * A matcher for the parameters.
   * @param message  the substring that the message should contain
   * @param line  the line number
   * @param column  the column number
   * @return the matcher
   */
  public static Matcher<CirculationRulesException> matches(String message, int line, int column) {
    return new CirculationRulesExceptionMatcher(new CirculationRulesException(message, line, column));
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("Exception has position "
        + expected.getLine() + ":" + expected.getColumn()
        + " and contains this text: " + expected.getMessage());
  }

  @Override
  protected boolean matchesSafely(CirculationRulesException actual, Description description) {
    boolean matches = true;

    if (expected.getLine() != actual.getLine() ||
        expected.getColumn() != actual.getColumn()) {
      matches = false;
      description.appendText("\n\tposition (line:column) is "
          + actual.getLine() + ":" + actual.getColumn()
          + "; expecting "
          + expected.getLine() + ":" + expected.getColumn());
    }

    if (! actual.getMessage().contains(expected.getMessage())) {
      matches = false;
      description.appendText(
          "\n\tmessage is: " + actual.getMessage() +
          "\n\tmessage doesn't contain this text: " + expected.getMessage());
    }

    return matches;
  }
}
