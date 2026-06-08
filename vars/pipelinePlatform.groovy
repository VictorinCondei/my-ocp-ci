def call(Map overrides = [:]) {

    pipeline {
        agent { label 'node-unix' }

        options {
            skipDefaultCheckout(true)
        }

        stages {

            stage('Checkout') {
                steps {

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


                        ciMaven.writeSettings(config)

                        writeJSON(
                            file: 'effective-config.json',
                            json: config
                        )

                        
                        sh '''
				                export JAVA_HOME="/home/jenkins/jdk-25.0.3.9"
				                export PATH="$PATH:/home/jenkins/apache-maven-3.9.15/bin"
				                pwd
                                echo $PATH
                                echo "=="
                                ls -la
                                echo "=="
		                        mvn clean compile -s settings-ci.xml
		            '''
                    }
                }
            }

        }
    }
}