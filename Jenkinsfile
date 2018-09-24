pipeline {
	agent none
	stages {
		stage('Build and test') {
			agent { label 'yona' }
			stages {
				stage('Build') {
					environment {
						DOCKER_HUB = credentials('docker-hub')
						GIT = credentials('github-yonabuild')
						HELM_HOME = "/opt/ope-cloudbees/yona/k8s/helm/.helm"
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
							sh '../../scripts/publish-helm-package.sh $BUILD_NUMBER 1.2.$BUILD_NUMBER yona $GIT_USR $GIT_PSW /opt/ope-cloudbees/yona/k8s/helm helm-charts'
						}
						sh 'git tag -a build-$BUILD_NUMBER -m "Jenkins"'
						sh 'git push https://${GIT_USR}:${GIT_PSW}@github.com/yonadev/yona-server.git --tags'
						script {
							env.BUILD_NUMBER_TO_DEPLOY = env.BUILD_NUMBER
						}
					}
					post {
						always {
							junit '**/build/test-results/*/*.xml'
						}
						failure {
							slackSend color: 'bad', channel: '#devops', message: "Server build ${env.BUILD_NUMBER} failed"
						}
					}
				}
				stage('Setup test server') {
					environment {
						YONA_DB = credentials('test-db')
						HELM_HOME = "/opt/ope-cloudbees/yona/k8s/helm/.helm"
						KUBECONFIG = "/opt/ope-cloudbees/yona/k8s/admin.conf"
					}
					steps {
						sh 'while ! $(curl -s -q -f -o /dev/null https://jump.ops.yona.nu/helm-charts/yona-1.2.$BUILD_NUMBER_TO_DEPLOY.tgz) ;do echo Waiting for Helm chart to become available; sleep 5; done'
						sh script: 'helm delete --purge yona; kubectl delete -n yona configmaps --all; kubectl delete -n yona job --all; kubectl delete -n yona secrets --all; kubectl delete pvc -n yona --all', returnStatus: true
						sh script: 'echo Waiting for purge to complete; sleep 30'
						sh 'helm repo update'
						sh 'helm upgrade --install --namespace yona --values /opt/ope-cloudbees/yona/k8s/helm/values.yaml --version 1.2.$BUILD_NUMBER_TO_DEPLOY yona yona/yona'
						sh 'scripts/wait-for-services.sh k8snew'
					}
					post {
						failure {
							slackSend color: 'bad', channel: '#devops', message: "Server build ${env.BUILD_NUMBER} failed to deploy to integration test server"
						}
					}
				}
				stage('Run integration tests') {
					steps {
						sh './gradlew -Pyona_adminservice_url=http://build.dev.yona.nu:31000 -Pyona_analysisservice_url=http://build.dev.yona.nu:31001 -Pyona_appservice_url=http://build.dev.yona.nu:31002 -Pyona_batchservice_url=http://build.dev.yona.nu:31003 intTest'
					}
					post {
						always {
							junit '**/build/test-results/*/*.xml'
						}
						success {
							slackSend color: 'good', channel: '#devops', message: "Server build ${env.BUILD_NUMBER} passed all tests"
						}
						failure {
							slackSend color: 'bad', channel: '#devops', message: "Server build ${env.BUILD_NUMBER} failed in integration test"
						}
					}
				}
			}
		}
		stage('Decide deploy to test servers') {
			agent none
			steps {
				script {
					env.DEPLOY_TO_TEST_SERVERS = input message: 'User input required',
							submitter: 'authenticated',
							parameters: [choice(name: 'Deploy to the test servers', choices: 'no\nyes', description: 'Choose "yes" if you want to deploy the test servers')]
				}
			}
		}
		stage('Deploy to beta test server') {
			agent { label 'beta' }
			when {
				environment name: 'DEPLOY_TO_TEST_SERVERS', value: 'yes'
			}
			steps {
				sh 'helm repo add yona https://jump.ops.yona.nu/helm-charts'
				sh 'helm upgrade --install -f /config/values.yaml --namespace yona --version 1.2.${BUILD_NUMBER_TO_DEPLOY} yona yona/yona'
				sh 'scripts/wait-for-services.sh k8snew'
			}
			post {
				failure {
					slackSend color: 'bad', channel: '#devops', message: "Server build ${env.BUILD_NUMBER} failed to deploy to beta"
				}
			}
		}
		stage('Deploy to load test server') {
			agent { label 'load' }
			when {
				environment name: 'DEPLOY_TO_TEST_SERVERS', value: 'yes'
			}
			steps {
				sh 'helm repo add yona https://jump.ops.yona.nu/helm-charts'
				sh 'helm upgrade --install -f /config/values.yaml --namespace loadtest --version 1.2.${BUILD_NUMBER_TO_DEPLOY} loadtest yona/yona'
				sh 'scripts/wait-for-services.sh k8snew'
			}
			post {
				failure {
					slackSend color: 'bad', channel: '#devops', message: "Server build ${env.BUILD_NUMBER} failed to deploy to load test"
				}
			}
		}
		stage('Decide deploy to production server') {
			agent none
			when {
				environment name: 'DEPLOY_TO_TEST_SERVERS', value: 'yes'
			}
			steps {
				slackSend color: 'good', channel: '#devops', message: "Server build ${env.BUILD_NUMBER} ready to deploy to production"
				script {
					env.DEPLOY_TO_PRD = input message: 'User input required',
					submitter: 'authenticated',
					parameters: [choice(name: 'Deploy to production server', choices: 'no\nyes', description: 'Choose "yes" if you want to deploy the production server')]
				}
			}
		}
		stage('Deploy to production server') {
			agent { label 'prd' }
			when {
				environment name: 'DEPLOY_TO_PRD', value: 'yes'
			}
			steps {
				sh 'helm repo add yona https://jump.ops.yona.nu/helm-charts'
				sh 'helm upgrade --install -f /config/values.yaml --namespace yona --version 1.2.${BUILD_NUMBER_TO_DEPLOY} yona yona/yona'
				sh 'scripts/wait-for-services.sh k8snew'
			}
			post {
				success {
					slackSend color: 'good', channel: '#devops', message: "Server build ${env.BUILD_NUMBER} successfully deployed to production"
				}
				failure {
					slackSend color: 'bad', channel: '#devops', message: "Server build ${env.BUILD_NUMBER} failed to deploy to production"
				}
			}
		}
	}
}
