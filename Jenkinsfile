
buildMvn {
  publishModDescriptor = true
  publishAPI = true
  mvnDeploy = true
  runLintRamlCop = true
  doKubeDeploy = true

  doDocker = {
    buildJavaDocker {
      overrideConfig  = 'no'
      publishMaster = 'yes'
    }
  }
}
