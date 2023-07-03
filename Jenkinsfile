//
// buildMvn {
//   publishModDescriptor = true
//   mvnDeploy = true
//   doKubeDeploy = true
//   publishPreview = false
//   buildNode = 'jenkins-agent-java17'
//
//   doDocker = {
//     buildJavaDocker {
//       publishPreview = false
//       overrideConfig  = 'no'
//       publishMaster = 'yes'
//     }
//   }
// }
buildMvn {
  publishModDescriptor = 'yes'
  mvnDeploy = 'yes'
  doKubeDeploy = true
  buildNode = 'jenkins-agent-java17'

  doDocker = {
    buildJavaDocker {
      publishMaster = 'yes'
      healthChk = 'yes'
      healthChkCmd = 'wget --no-verbose --tries=1 --spider http://localhost:8081/admin/health || exit 1'
    }
  }
}
