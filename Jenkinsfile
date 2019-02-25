pipeline {
	agent none
	options {
		disableConcurrentBuilds()
	}
	stages {
		stage('Build') {
			agent { label 'yona' }
			environment {
				DOCKER_HUB = credentials('docker-hub')
				GIT = credentials('github-yonabuild')
				HELM_HOME = "/opt/ope-cloudbees/yona/k8s/helm/.helm"
			}
			steps {
				checkout scm
				sh './gradlew -PdockerHubUserName=$DOCKER_HUB_USR -PdockerHubPassword="$DOCKER_HUB_PSW" -PdockerUrl=unix:///var/run/docker.sock build pushDockerImage'
				dir ('k8s/helm') {
					sh '../../scripts/publish-helm-package.sh $BUILD_NUMBER 2.0.$BUILD_NUMBER yona $GIT_USR $GIT_PSW /opt/ope-cloudbees/yona/k8s/helm helm-charts'
				}
				sh 'git tag -a napi-build-$BUILD_NUMBER -m "Jenkins"'
				sh 'git push https://${GIT_USR}:${GIT_PSW}@github.com/yonadev/yona-server.git --tags'
				script {
					env.BUILD_NUMBER_TO_DEPLOY = env.BUILD_NUMBER
				}
			}
			post {
				always {
					junit '**/build/test-results/*/*.xml'
				}
				success {
					step([$class: 'hudson.plugins.jira.JiraIssueUpdater', 
						issueSelector: [$class: 'hudson.plugins.jira.selector.DefaultIssueSelector'], 
						scm: scm])
				}
				failure {
					slackSend color: 'danger', channel: '#devops', message: "YD-622 NAPI Server build ${env.BUILD_NUMBER} failed"
				}
			}
		}
		stage('Setup test server') {
			agent { label 'yona' }
			environment {
				YONA_DB = credentials('test-db')
				HELM_HOME = "/opt/ope-cloudbees/yona/k8s/helm/.helm"
				KUBECONFIG = "/opt/ope-cloudbees/yona/k8s/admin.conf"
			}
			steps {
				sh 'while ! $(curl -s -q -f -o /dev/null https://jump.ops.yona.nu/helm-charts/yona-2.0.$BUILD_NUMBER_TO_DEPLOY.tgz) ;do echo Waiting for Helm chart to become available; sleep 5; done'
				sh script: 'helm delete --purge yona; kubectl delete -n yona configmaps --all; kubectl delete -n yona job --all; kubectl delete -n yona secrets --all; kubectl delete pvc -n yona --all', returnStatus: true
				sh script: 'echo Waiting for purge to complete; sleep 30'
				sh 'helm repo update'
				sh 'helm upgrade --install --namespace yona --values /opt/ope-cloudbees/yona/k8s/helm/values.yaml --version 2.0.$BUILD_NUMBER_TO_DEPLOY yona yona/yona'
				sh 'scripts/wait-for-services.sh k8snew'
			}
			post {
				failure {
					slackSend color: 'danger', channel: '#devops', message: "YD-622 NAPI Server build ${env.BUILD_NUMBER} failed to deploy build ${env.BUILD_NUMBER_TO_DEPLOY} to integration test server"
				}
			}
		}
		stage('Run integration tests') {
			agent { label 'yona' }
			steps {
				sh './gradlew -Pyona_adminservice_url=http://build.dev.yona.nu:31000 -Pyona_analysisservice_url=http://build.dev.yona.nu:31001 -Pyona_appservice_url=https://build.dev.yona.nu -Pyona_batchservice_url=http://build.dev.yona.nu:31003 intTest'
			}
			post {
				always {
					junit testResults: '**/build/test-results/*/*.xml', keepLongStdio: true
				}
				success {
					slackSend color: 'good', channel: '#devops', message: "YD-622 NAPI Server build ${env.BUILD_NUMBER} passed all tests on build ${env.BUILD_NUMBER_TO_DEPLOY}"
				}
				failure {
					slackSend color: 'danger', channel: '#devops', message: "YD-622 NAPI Server build ${env.BUILD_NUMBER} failed integration test of build ${env.BUILD_NUMBER_TO_DEPLOY}"
				}
			}
		}
		stage('Deploy to test server') {
			agent { label 'load' }
			environment {
				BETA_DB = credentials('beta-db-jenkins')
				BETA_DB_IP = credentials('beta-db-ip')
			}
			steps {
				sh 'helm repo add yona https://jump.ops.yona.nu/helm-charts'
				sh 'helm upgrade --install -f /config/values.yaml --namespace loadtest --version 2.0.${BUILD_NUMBER_TO_DEPLOY} loadtest yona/yona'
				sh 'NAMESPACE=loadtest scripts/wait-for-services.sh k8snew'
			}
			post {
				success {
					slackSend color: 'good', channel: '#devops', message: "YD-622 NAPI Server build ${env.BUILD_NUMBER} successfully deployed build ${env.BUILD_NUMBER_TO_DEPLOY} to the test server"
				}
				failure {
					slackSend color: 'danger', channel: '#devops', message: "YD-622 NAPI Server build ${env.BUILD_NUMBER} failed to deploy build ${env.BUILD_NUMBER_TO_DEPLOY} to the test server"
				}
			}
		}
	}
}
