
buildMvn {
  publishModDescriptor = true
  publishAPI = true
  mvnDeploy = true
  runLintRamlCop = true
  doKubeDeploy = true
  publishPreview = false
  buildNode = 'jenkins-agent-java11'

  doDocker = {
    buildJavaDocker {
      publishPreview = false
      overrideConfig  = 'no'
      publishMaster = 'yes'
    }
  }
}
