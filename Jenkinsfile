pipeline {
    agent { label 'yona' }
	offset = 300
    stages {
        stage('Build') {
			steps {
				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'docker-hub',
								usernameVariable: 'DOCKER_HUB_USERNAME', passwordVariable: 'DOCKER_HUB_PASSWORD']]) {
					sh './gradlew -PdockerHubUserName=$DOCKER_HUB_USERNAME -PdockerHubPassword="$DOCKER_HUB_PASSWORD" -PdockerUrl=unix:///var/run/docker.sock build pushDockerImage'
				}
            }
        }
        stage('Setup test server') {
			steps {
				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'test-db',
								usernameVariable: 'YONA_DB_USERNAME', passwordVariable: 'YONA_DB_PASSWORD']]) {
					sh 'scripts/install-test-server.sh $YONA_DB_USERNAME "$YONA_DB_PASSWORD" jdbc:mariadb://yonadbserver:3306/yona'
				}
            }
        }
        stage('Run integration tests') {
			steps {
				sh './gradlew -Pyona_appservice_url=http://185.3.209.132 -Pyona_adminservice_url=http://185.3.209.132:8080 -Pyona_analysisservice_url=http://185.3.209.132:8081 intTest'
            }
        }
    }
    post { 
        always { 
            junit '**/build/test-results/*/*.xml'
        }
    }
}
