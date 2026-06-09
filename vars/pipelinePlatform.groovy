def call(Map overrides = [:]) {

    pipeline {
        agent { label 'node-unix' }
        environment {
            JavaH  = '/home/jenkins/jdk-25.0.3.9'
	        MvnH  = '/home/jenkins/apache-maven-3.9.15/bin'
        }
        options {
            skipDefaultCheckout(true)
        }

        stages {

            stage('Checkout') {
                steps {
                    deleteDir()
                    script {
                        if (overrides.gitUrl) {
                            git(
                                branch: overrides.branch ?: 'main',
                                credentialsId: overrides.gitCredentialsId,
                                url: overrides.gitUrl
                            )

                        } else {
                            checkout scm
                        }
                    }
                }
            }

            stage('Build') {
                steps {
                    script {

                        def config = ciConfig.read()

                        if (overrides.nexusPublicUrl) {
                            config['artifact.public.repository.url'] =
                                overrides.nexusPublicUrl
                        }

                        if (overrides.nexusReleaseUrl) {
                            config['artifact.release.repository.url'] =
                                overrides.nexusReleaseUrl
                        }

                        if (overrides.nexusSnapshotUrl) {
                            config['artifact.snapshot.repository.url'] =
                                overrides.nexusSnapshotUrl
                        }

                        if (overrides.nexusCredentialsId) {
                            config['artifact.repository.credentials.id'] =
                                overrides.nexusCredentialsId
                        }

                        deleteDir()
                        ciMaven.writeSettings(config)

                        writeJSON(
                            file: 'effective-config.json',
                            json: config
                        )

                        
                        sh '''
				                export JAVA_HOME="${JavaH}"
                                export PATH="$PATH:${MvnH}"
                                pwd
                                echo $PATH
                                echo "=="
                                ls -la
                                echo "=="
		                        mvn clean compile -Dmaven.repo.local=./fresh-repo -s settings-ci.xml
		            '''
                    }
                }
            }

        }
    }
}