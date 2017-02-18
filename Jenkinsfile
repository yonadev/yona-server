#!groovy
pipeline {
    agent label 'yona'
    stages {
        stage('Build') {
			steps {
				sh 'gradlew -PdockerHubUserName=yonabuild -PdockerHubPassword="${dockerpassword}" -PdockerUrl=unix:///var/run/docker.sock build pushDockerImage'
            }
        }
    }
    post { 
        always { 
            echo 'I will always say Hello again!'
        }
    }
}
