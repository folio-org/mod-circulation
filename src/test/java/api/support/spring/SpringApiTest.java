package api.support.spring;

import org.junit.ClassRule;
import org.junit.Rule;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;

import api.support.APITests;

@ContextConfiguration(classes = TestSpringConfiguration.class)
public class SpringApiTest extends APITests {
  @ClassRule
  public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();
  @Rule
  public final SpringMethodRule springMethodRule = new SpringMethodRule();

  public SpringApiTest() {
    super();
  }

  public SpringApiTest(boolean initRules, boolean enableLoanHistory) {
    super(initRules, enableLoanHistory);
  }
}
