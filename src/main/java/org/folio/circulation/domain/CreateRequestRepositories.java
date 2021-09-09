package org.folio.circulation.domain;

import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;

public class CreateRequestRepositories {
  private final RequestRepository requestRepository;
  private final RequestPolicyRepository requestPolicyRepository;
  private final ConfigurationRepository configurationRepository;

  public CreateRequestRepositories(RequestRepository requestRepository,
    RequestPolicyRepository requestPolicyRepository,
    ConfigurationRepository configurationRepository) {

    this.requestRepository = requestRepository;
    this.requestPolicyRepository = requestPolicyRepository;
    this.configurationRepository = configurationRepository;
  }

  public RequestRepository getRequestRepository() {
    return requestRepository;
  }

  public RequestPolicyRepository getRequestPolicyRepository() {
    return requestPolicyRepository;
  }

  public ConfigurationRepository getConfigurationRepository() {
    return configurationRepository;
  }
}
