package org.folio.circulation.domain;

import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.infrastructure.storage.AutomatedPatronBlocksRepository;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;

public class CreateRequestRepositories {
  private final RequestRepository requestRepository;
  private final RequestPolicyRepository requestPolicyRepository;
  private final ConfigurationRepository configurationRepository;
  private final AutomatedPatronBlocksRepository automatedPatronBlocksRepository;

  public CreateRequestRepositories(RequestRepository requestRepository,
    RequestPolicyRepository requestPolicyRepository,
    ConfigurationRepository configurationRepository,
    AutomatedPatronBlocksRepository automatedPatronBlocksRepository) {

    this.requestRepository = requestRepository;
    this.requestPolicyRepository = requestPolicyRepository;
    this.configurationRepository = configurationRepository;
    this.automatedPatronBlocksRepository = automatedPatronBlocksRepository;
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

  public AutomatedPatronBlocksRepository getAutomatedPatronBlocksRepository() {
    return automatedPatronBlocksRepository;
  }
}
