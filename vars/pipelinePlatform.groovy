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

                        sh 'mvn clean compile -s settings-ci.xml'
                    }
                }
            }

        }
    }
}