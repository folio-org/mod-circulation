package api.support.spring;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import api.support.APITests;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestSpringConfiguration.class)
public abstract class SpringApiTest extends APITests {

  public SpringApiTest() {
    super();
  }

  public SpringApiTest(boolean initRules, boolean enableLoanHistory) {
    super(initRules, enableLoanHistory);
  }
}
