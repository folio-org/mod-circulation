pipeline {

   environment {
      docker_repository = 'folioci'
      docker_image = "${env.docker_repository}/mod-circulation"
   }
    
   agent {
      node {
         label 'folio-jenkins-slave-docker'
      }
   }
    
   stages {
      stage('Prep') {
         steps {
            script {
               currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
            }

            slackSend "Build started: ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
            step([$class: 'WsCleanup'])
         }
      }
 
      stage('Checkout') {
         steps {          
            checkout([
               $class: 'GitSCM',
               branches: scm.branches,
               extensions: scm.extensions + [[$class: 'SubmoduleOption', 
                                                       disableSubmodules: false, 
                                                       parentCredentials: false, 
                                                       recursiveSubmodules: true, 
                                                       reference: '', 
                                                       trackingSubmodules: false]], 
               userRemoteConfigs: scm.userRemoteConfigs
            ])

            echo " Checked out $env.BRANCH_NAME"
            
         }   
      } 
        
      stage('Build') {
         steps {
            script {
              def get_gradle_ver = $/grep "^version" build.gradle | awk -F '=' '{ print $2 }' | sed 's/[\s|"]//g'/$
              GRADLE_VERSION = sh(returnStdout: true, script: get_gradle_ver).trim()
              sh "echo Module Version: $GRADLE_VERSION"
  
            }

            sh 'gradle build fatJar'
         }
      }
        
      stage('Build Docker') {
         steps {
            echo 'Building Docker image'
            script {
               docker.build("${env.docker_image}:${GRADLE_VERSION}-${env.BUILD_NUMBER}", '--no-cache .')
            }
         } 
      } 
 
      stage('Deploy to Docker Repo') {
         when {
            branch 'master'
         }
         steps {
            echo "Pushing Docker image ${env.docker_image}:${GRADLE_VERSION} to Docker Hub..."
            script {
               docker.withRegistry('https://index.docker.io/v1/', 'DockerHubIDJenkins') {
                  def dockerImage =  docker.image("${env.docker_image}:${GRADLE_VERSION}-${env.BUILD_NUMBER}")
                  dockerImage.push()
                  dockerImage.push('latest')
               }
            }
         }
      }
   
      stage('Clean Up') {
         steps {
            sh "docker rmi ${docker_image}:${GRADLE_VERSION}-${env.BUILD_NUMBER} || exit 0"
            sh "docker rmi ${docker_image}:latest || exit 0"
         }
      }
   }  

   post { 
      success { 
         slackSend(color: '#008000',  
                   message: "Build successful: ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)")
      }

      failure {
         slackSend(color: '#FF0000', 
                   message: "Build failed: ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)")

         mail bcc: '', body: "${env.BUILD_URL}", cc: '', from: '', replyTo: '', 
                   subject: "Build failed: ${env.JOB_NAME} ${env.BUILD_NUMBER}", 
                   to: 'folio-jenkins.backend@indexdata.com'
      }

      unstable {
         slackSend(color:'#008000', 
                   message: "Build unstable: ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)")

         mail bcc: '', body: "${env.BUILD_URL}", cc: '', from: '', replyTo: '', 
                   subject: "Build unstable: ${env.JOB_NAME} ${env.BUILD_NUMBER}", 
                   to: 'folio-jenkins.backend@indexdata.com'
      }

      changed {
         slackSend(color:'#008000', 
                   message: "Build back to normal: ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)")
         mail bcc: '', body: "${env.BUILD_URL}", cc: '', from: '', replyTo: '', 
                  subject: "Build back to normal: ${env.JOB_NAME} ${env.BUILD_NUMBER}", 
                  to: 'folio-jenkins.backend@indexdata.com'
      }
   } 
}
