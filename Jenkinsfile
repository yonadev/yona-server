pipeline {
	agent none
	stages {
		stage('Build') {
			agent { label 'yona' }
			environment {
				DOCKER_HUB = credentials('docker-hub')
				GIT = credentials('65325e52-5ec0-46a7-a937-f81f545f3c1b')
			}
			steps {
				checkout scm
				sh './gradlew -PdockerHubUserName=$DOCKER_HUB_USR -PdockerHubPassword="$DOCKER_HUB_PSW" -PdockerUrl=unix:///var/run/docker.sock build pushDockerImage'
				script {
					def scannerHome = tool 'SonarQube scanner 3.0';
					withSonarQubeEnv('Yona SonarQube server') {
						sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectVersion=build-$BUILD_NUMBER"
					}
				}
				dir ('k8s/helm') {
					sh('../../scripts/publish-helm-package.sh $BUILD_NUMBER 1.2.$BUILD_NUMBER yona-server $GIT_USR $GIT_PSW /opt/ope-cloudbees/yona/helm helm-charts')
				}
				sh('git tag -a build-$BUILD_NUMBER -m "Jenkins"')
				sh('git push https://${GIT_USR}:${GIT_PSW}@github.com/yonadev/yona-server.git --tags')
				script {
					env.BUILD_NUMBER_TO_DEPLOY = env.BUILD_NUMBER
				}
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
				sh 'scripts/install-test-server.sh ${BUILD_NUMBER_TO_DEPLOY} $YONA_DB_USR "$YONA_DB_PSW" jdbc:mariadb://yonadbserver:3306/yona /opt/ope-cloudbees/yona/resources'
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
		stage('Decide deploy to Mobiquity test server') {
			agent none
			steps {
				checkpoint 'Build and tests done'
				script {
					env.DEPLOY_TO_MOB_TEST = input message: 'User input required',
							submitter: 'authenticated',
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
				sh 'wget -O copy-resources.sh https://raw.githubusercontent.com/yonadev/yona-server/master/scripts/copy-resources.sh'
				sh 'chmod +x copy-resources.sh'
				sh 'wget -O wait-for-services.sh https://raw.githubusercontent.com/yonadev/yona-server/master/scripts/wait-for-services.sh'
				sh 'chmod +x wait-for-services.sh'
				sh './refresh-build.sh ${BUILD_NUMBER_TO_DEPLOY} $YONA_DB_USR "$YONA_DB_PSW" jdbc:mariadb://yonadbserver:3306/yona /opt/ope-cloudbees/yona/application.properties /opt/ope-cloudbees/yona/resources /opt/ope-cloudbees/yona/backup'
			}
		}
		stage('Decide deploy to acceptance test server') {
			agent none
			when {
				environment name: 'DEPLOY_TO_MOB_TEST', value: 'yes'
			}
			steps {
				checkpoint 'Mobiquity test server deployed'
				script {
					env.DEPLOY_TO_ACC_TEST = input message: 'User input required',
							submitter: 'authenticated',
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
				sh 'wget -O copy-resources.sh https://raw.githubusercontent.com/yonadev/yona-server/master/scripts/copy-resources.sh'
				sh 'chmod +x copy-resources.sh'
				sh 'wget -O wait-for-services.sh https://raw.githubusercontent.com/yonadev/yona-server/master/scripts/wait-for-services.sh'
				sh 'chmod +x wait-for-services.sh'
				sh './refresh-build.sh ${BUILD_NUMBER_TO_DEPLOY} $YONA_DB_USR "$YONA_DB_PSW" jdbc:mariadb://yonadbserver:3306/yona /opt/ope-cloudbees/yona/application.properties /opt/ope-cloudbees/yona/resources /opt/ope-cloudbees/yona/backup'
			}
		}
	}
}
