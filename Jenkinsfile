pipeline {
	agent none
	stages {
		stage('Decide run load test') {
			agent none
			//TODO: uncomment
			//when {
			//	environment name: 'RUN_LOAD_TEST', value: 'yes'
			//}
			steps {
				slackSend color: 'good', channel: '#devops', message: "Server build ${env.BUILD_NUMBER} ready to deploy to production"
				script {
					env.DEPLOY_TO_PRD = input message: 'User input required',
					submitter: 'authenticated',
					parameters: [choice(name: 'Deploy to production server', choices: 'no\nyes', description: 'Choose "yes" if you want to deploy the production server')]
				}
			}
		}
		stage('Run load test') {
			agent { label 'yona' }
			when {
				environment name: 'RUN_LOAD_TEST', value: 'yes'
			}
			environment {
				JM_PATH_IN_CONT = "/mnt/jmeter"
				JM_LOCAL_PATH = "jmeter"
				JM_THREADS = "100"
				JM_LOAD_DURATION = "600"
			}
			steps {
				checkout scm
				sh 'rm -rf ${JM_LOCAL_PATH}'
				sh 'mkdir ${JM_LOCAL_PATH}'
				sh 'cp scripts/load-test.jmx ${JM_LOCAL_PATH}'
				sh 'docker run --volume ${JM_LOCAL_PATH}:${JM_PATH_IN_CONT} yonadev/jmeter:JMeter3.3 -n -t ${JM_PATH_IN_CONT}/load-test.jmx -l ${JM_PATH_IN_CONT}/out/result.jtl -j ${JM_PATH_IN_CONT}/out/jmeter.log -Jthreads=${JM_THREADS} -JloadDuration=${JM_LOAD_DURATION}'
				perfReport '${JM_LOCAL_PATH}/result.jtl'
			}
			post {
				failure {
					slackSend color: 'bad', channel: '#devops', message: "Server build ${env.BUILD_NUMBER} failed in load test"
				}
			}
		}
	}
}
