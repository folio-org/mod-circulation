package api.support.matchers;

import io.vertx.core.json.JsonObject;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.collection.IsMapContaining;

import java.util.Map;

public class RequestItemMatcher extends ValidationErrorMatchers {

  public static TypeSafeDiagnosingMatcher<JsonObject> hasItemLocationProperties(
    Matcher<Map<String, String>> matcher) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("Location which ").appendDescriptionOf(matcher);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation, Description description) {
        JsonObject item = representation.getJsonObject("item").getJsonObject("location");
        Map<String, Object> itemMap = item.getMap();
        matcher.describeMismatch(itemMap, description);
        return matcher.matches(itemMap);
      }
    };
  }

  public static TypeSafeDiagnosingMatcher<Object> hasLibraryName(String value) {
    return new TypeSafeDiagnosingMatcher<Object>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("has library name ").appendValue(value);
      }

      @Override
      protected boolean matchesSafely(Object map, Description description) {
        Matcher<Map<? extends String, ? extends String>> matcher = IsMapContaining.hasEntry("libraryName", value);
        matcher.describeMismatch(map, description);
        return matcher.matches(map);
      }

    };
  }

  public static TypeSafeDiagnosingMatcher<Object> hasLocationCode(String value) {
    return new TypeSafeDiagnosingMatcher<Object>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("has location code ").appendValue(value);
      }

      @Override
      protected boolean matchesSafely(Object map, Description description) {
        Matcher<Map<? extends String, ? extends String>> matcher = IsMapContaining.hasEntry("code", value);
        matcher.describeMismatch(map, description);
        return matcher.matches(map);
      }
    };

  }

  public static TypeSafeDiagnosingMatcher<Object> hasLocationName(String value) {
    return new TypeSafeDiagnosingMatcher<Object>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("has location name ").appendValue(value);
      }

      @Override
      protected boolean matchesSafely(Object map, Description description) {
        Matcher<Map<? extends String, ? extends String>> matcher = IsMapContaining.hasEntry("name", value);
        matcher.describeMismatch(map, description);
        return matcher.matches(map);
      }
    };
  }
}



