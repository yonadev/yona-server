pipeline {
	agent none
	stages {
		stage('Build') {
			agent { label 'yona' }
			environment {
				DOCKER_HUB = credentials('docker-hub')
			}
			steps {
				checkout scm
				sh './gradlew -PdockerHubUserName=$DOCKER_HUB_USR -PdockerHubPassword="$DOCKER_HUB_PSW" -PdockerUrl=unix:///var/run/docker.sock build pushDockerImage'
			}
			post {
				always {
					junit '**/build/test-results/*/*.xml'
				}
			}
		}
		stage('Setup test server') {
			agent { label 'yona' }
			environment {
				YONA_DB = credentials('test-db')
			}
			steps {
				sh 'scripts/install-test-server.sh $YONA_DB_USR "$YONA_DB_PSW" jdbc:mariadb://yonadbserver:3306/yona'
			}
		}
		stage('Run integration tests') {
			agent { label 'yona' }
			steps {
				sh './gradlew -Pyona_appservice_url=http://185.3.209.132 -Pyona_adminservice_url=http://185.3.209.132:8080 -Pyona_analysisservice_url=http://185.3.209.132:8081 intTest'
			}
			post {
				always {
					junit '**/build/test-results/*/*.xml'
				}
			}
		}
		stage('Tag revision on GitHub') {
			agent { label 'yona' }
			environment {
				GIT = credentials('65325e52-5ec0-46a7-a937-f81f545f3c1b')
			}
			steps {
				sh('git tag -a build-$BUILD_NUMBER -m "Jenkins"')
				sh('git push https://${GIT_USR}:${GIT_PSW}@github.com/yonadev/yona-server.git --tags')
			}
		}
		stage('Decide tag on Docker Hub') {
			agent none
			steps {
				script {
					env.TAG_ON_DOCKER_HUB = input message: 'User input required',
							parameters: [choice(name: 'Tag on Docker Hub', choices: 'no\nyes', description: 'Choose "yes" if you want to deploy this build')]
				}
			}
		}
		stage('Tag on Docker Hub') {
			agent { label 'yona' }
			when {
				environment name: 'TAG_ON_DOCKER_HUB', value: 'yes'
			}
			steps {
				sh('scripts/retag-images.sh ${BUILD_NUMBER}')
			}
		}
		stage('Decide deploy to Mobiquity test server') {
			agent none
			when {
				environment name: 'TAG_ON_DOCKER_HUB', value: 'yes'
			}
			steps {
				script {
					env.DEPLOY_TO_MOB_TEST = input message: 'User input required',
							parameters: [choice(name: 'Deploy to Mobiquity test server', choices: 'no\nyes', description: 'Choose "yes" if you want to deploy the Mobiquity test server')]
				}
			}
		}
		stage('Deploy to Mobiquity test server') {
			agent { label 'mob-test' }
			environment {
				YONA_DB = credentials('test-db')
			}
			when {
				environment name: 'DEPLOY_TO_MOB_TEST', value: 'yes'
			}
			steps {
				sh 'wget -O refresh-build.sh https://raw.githubusercontent.com/yonadev/yona-server/master/scripts/refresh-build.sh'
				sh 'chmod +x refresh-build.sh'
				sh 'wget -O wait-for-services.sh https://raw.githubusercontent.com/yonadev/yona-server/master/scripts/wait-for-services.sh'
				sh 'chmod +x wait-for-services.sh'
				sh './refresh-build.sh ${BUILD_NUMBER} $YONA_DB_USR "$YONA_DB_PSW" jdbc:mariadb://yonadbserver:3306/yona /opt/ope-cloudbees/yona/application.properties /opt/ope-cloudbees/yona/backup'
			}
		}
		stage('Decide deploy to acceptance test server') {
			agent none
			when {
				environment name: 'DEPLOY_TO_MOB_TEST', value: 'yes'
			}
			steps {
				script {
					env.DEPLOY_TO_ACC_TEST = input message: 'User input required',
							parameters: [choice(name: 'Deploy to acceptance test server', choices: 'no\nyes', description: 'Choose "yes" if you want to deploy the acceptance test server')]
				}
			}
		}
		stage('Deploy to acceptance test server') {
			agent { label 'acc-test' }
			environment {
				YONA_DB = credentials('test-db')
			}
			when {
				environment name: 'DEPLOY_TO_ACC_TEST', value: 'yes'
			}
			steps {
				sh 'wget -O refresh-build.sh https://raw.githubusercontent.com/yonadev/yona-server/master/scripts/refresh-build.sh'
				sh 'chmod +x refresh-build.sh'
				sh 'wget -O wait-for-services.sh https://raw.githubusercontent.com/yonadev/yona-server/master/scripts/wait-for-services.sh'
				sh 'chmod +x wait-for-services.sh'
				sh './refresh-build.sh ${BUILD_NUMBER} $YONA_DB_USR "$YONA_DB_PSW" jdbc:mariadb://yonadbserver:3306/yona /opt/ope-cloudbees/yona/application.properties /opt/ope-cloudbees/yona/backup'
			}
		}
	}
}
