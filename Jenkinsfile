
buildMvn {
  publishModDescriptor = true
  mvnDeploy = true
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
