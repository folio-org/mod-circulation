package api.support.matchers;

import static org.hamcrest.CoreMatchers.is;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import com.jayway.jsonpath.JsonPath;

import io.vertx.core.json.JsonObject;

public class JsonPathMatchers {

  private JsonPathMatchers() {}

  public static <T> Matcher<JsonObject> hasJsonPath(String path, Matcher<T> valueMatcher) {
    return new TypeSafeMatcher<JsonObject>() {
      @Override
      protected boolean matchesSafely(JsonObject item) {
        try {
          final Object actualValue = JsonPath.parse(item.toString())
            .read(path);

          return valueMatcher.matches(actualValue);
        } catch (Exception ex) {
          return false;
        }
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("Value by json path ")
          .appendValue(path).appendText(" matches ")
          .appendDescriptionOf(valueMatcher);
      }
    };
  }

  public static <T> Matcher<JsonObject> hasJsonPath(String path, T value) {
    return hasJsonPath(path, is(value));
  }
}
