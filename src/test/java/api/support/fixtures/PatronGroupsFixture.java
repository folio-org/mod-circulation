package api.support.fixtures;

import api.support.builders.PatronGroupBuilder;
import api.support.http.ResourceClient;
import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.folio.circulation.support.http.client.IndividualResource;


public class PatronGroupsFixture {
  private final ResourceClient patronGroupsClient;
  
  public PatronGroupsFixture(ResourceClient client) {
    patronGroupsClient = client;
  }
  
  public IndividualResource regular() 
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {
    return patronGroupsClient.create(PatronGroupExamples.basedUponRegularUsers());
  }
  
  public IndividualResource regular(Function<PatronGroupBuilder,
      PatronGroupBuilder> additionalProperties) 
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {
    
    final PatronGroupBuilder builder = additionalProperties.apply(
        PatronGroupExamples.basedUponRegularUsers());
    return patronGroupsClient.create(builder);
  }
  
    public IndividualResource staff() 
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {
    return patronGroupsClient.create(PatronGroupExamples.basedUponStaff());
  }
    
  public IndividualResource staff(Function<PatronGroupBuilder,
      PatronGroupBuilder> additionalProperties) 
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {
    
    final PatronGroupBuilder builder = additionalProperties.apply(
        PatronGroupExamples.basedUponStaff());
    return patronGroupsClient.create(builder);
  }
    
    public IndividualResource faculty() 
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {
    return patronGroupsClient.create(PatronGroupExamples.basedUponFaculty());
  }
    
  public IndividualResource faculty(Function<PatronGroupBuilder,
      PatronGroupBuilder> additionalProperties) 
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {
    
    final PatronGroupBuilder builder = additionalProperties.apply(
        PatronGroupExamples.basedUponFaculty());
    return patronGroupsClient.create(builder);
  }
}
