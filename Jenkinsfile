pipeline {
    agent { label 'yona' }
    stages {
        stage('Build') {
			steps {
				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'docker-hub',
								usernameVariable: 'DOCKER_HUB_USERNAME', passwordVariable: 'DOCKER_HUB_PASSWORD']]) {
					sh './gradlew -PdockerHubUserName=$DOCKER_HUB_USERNAME -PdockerHubPassword="$DOCKER_HUB_PASSWORD" -PdockerUrl=unix:///var/run/docker.sock build pushDockerImage'
				}
            }
        }
    }
    post { 
        always { 
            junit '**/build/test-results/*/*.xml'
        }
    }
}
