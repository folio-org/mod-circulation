
buildMvn {
  publishModDescriptor = true
  publishAPI = true
  mvnDeploy = true
  runLintRamlCop = true
  doKubeDeploy = true
  publishPreview = true

  doDocker = {
    buildJavaDocker {
      overrideConfig  = 'no'
      publishMaster = 'yes'
      publishPreview = true
    }
  }
}
