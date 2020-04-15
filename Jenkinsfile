
buildMvn {
  publishModDescriptor = true
  publishAPI = true
  mvnDeploy = true
  runLintRamlCop = true
  doKubeDeploy = true
  publishPreview = true

  doDocker = {
    buildJavaDocker {
      publishPreview = true
      overrideConfig  = 'no'
      publishMaster = 'yes'
    }
  }
}
