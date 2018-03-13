pipeline {
	agent none
	stages {
		stage('Build') {
			agent { label 'yona' }
			environment {
				DOCKER_HUB = credentials('docker-hub')
				GIT = credentials('65325e52-5ec0-46a7-a937-f81f545f3c1b')
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
				checkpoint 'Build done'
				sh 'while ! $(curl -s -q -f -o /dev/null https://jump.ops.yona.nu/helm-charts/yona-1.2.$BUILD_NUMBER_TO_DEPLOY.tgz) ;do echo Waiting for Helm chart to become available; sleep 5; done'
				sh script: 'helm delete yona; kubectl delete -n yona configmaps --all; kubectl delete -n yona job --all; kubectl delete -n yona secrets --all; kubectl delete pvc -n yona --all', returnStatus: true
				sh 'helm repo update'
				sh 'helm upgrade --install --namespace yona --values /opt/ope-cloudbees/yona/k8s/helm/values.yaml --version 1.2.$BUILD_NUMBER_TO_DEPLOY yona yona/yona'
				sh 'scripts/wait-for-services.sh k8snew'
			}
		}
		stage('Run integration tests') {
			agent { label 'yona' }
			steps {
				sh './gradlew -Pyona_adminservice_url=http://185.3.209.132:31000 -Pyona_analysisservice_url=http://185.3.209.132:31001 -Pyona_appservice_url=http://185.3.209.132:31002 -Pyona_batchservice_url=http://185.3.209.132:31003 intTest'
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
				HELM_HOME = "/opt/ope-cloudbees/yona/k8s/helm/.helm"
				KUBECONFIG = "/opt/ope-cloudbees/yona/k8s/admin.conf"
			}
			when {
				environment name: 'DEPLOY_TO_MOB_TEST', value: 'yes'
			}
			steps {
				sh 'helm repo update'
				sh 'helm upgrade --install --namespace yona --set mariadb.mariadbUser=$YONA_DB_USR --set mariadb.mariadbPassword="$YONA_DB_PSW" --values /opt/ope-cloudbees/yona/k8s/helm/values.yaml --version 1.2.$BUILD_NUMBER_TO_DEPLOY yona yona/yona'
				sh 'scripts/wait-for-services.sh k8snew'
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
				HELM_HOME = "/opt/ope-cloudbees/yona/k8s/helm/.helm"
				KUBECONFIG = "/opt/ope-cloudbees/yona/k8s/admin.conf"
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
		stage('Decide deploy to beta test server') {
			agent none
			when {
				environment name: 'DEPLOY_TO_ACC_TEST', value: 'yes'
			}
			steps {
				checkpoint 'Acceptance test server deployed'
				script {
					env.DEPLOY_TO_BETA = input message: 'User input required',
					submitter: 'authenticated',
					parameters: [choice(name: 'Deploy to beta test server', choices: 'no\nyes', description: 'Choose "yes" if you want to deploy the beta test server')]
				}
			}
		}
		stage('Deploy to beta test server') {
			agent { label 'beta' }
			when {
				environment name: 'DEPLOY_TO_BETA', value: 'yes'
			}
			steps {
				sh 'helm repo add yona https://jump.ops.yona.nu/helm-charts'
				sh 'helm upgrade --install -f /config/values.yaml --namespace yona --version 1.2.${BUILD_NUMBER_TO_DEPLOY} yona yona/yona'
				sh 'scripts/wait-for-services.sh k8snew'
			}
		}
		stage('Decide deploy to production server') {
			agent none
			when {
				environment name: 'DEPLOY_TO_BETA', value: 'yes'
			}
			steps {
				checkpoint 'Beta test server deployed'
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
		}
	}
}
