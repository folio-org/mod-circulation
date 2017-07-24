package org.folio.circulation.api.support;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.joda.time.DateTime;

public class TextDateTimeMatcher {
  public static Matcher<String> isEquivalentTo(DateTime expected) {
    return new TypeSafeMatcher<String>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a date time matching: %s", expected.toString()));
      }

      @Override
      protected boolean matchesSafely(String textRepresentation) {
        //response representation might vary from request representation
        DateTime actual = DateTime.parse(textRepresentation);

        return expected.isEqual(actual);
      }
    };
  }
}
