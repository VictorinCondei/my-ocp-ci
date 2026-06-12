// vars/pipelineMicroservice.groovy
def call(Map overrides = [:]) {
    pipeline {

        agent { label 'node-unix' }

        environment {
            IMAGE_CONTEXT_DIR = 'target/ci-image'
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
                            sh "git checkout ${params.Branch}"
                        }
                    }
                }
            }

            stage('Build') {
                steps {
                    script {
                        def config = ciConfig.read()

                        if (overrides.nexusPublicUrl) {
                            config['artifact.public.repository.url'] = overrides.nexusPublicUrl
                        }
                        if (overrides.nexusReleaseUrl) {
                            config['artifact.release.repository.url'] = overrides.nexusReleaseUrl
                        }
                        if (overrides.nexusSnapshotUrl) {
                            config['artifact.snapshot.repository.url'] = overrides.nexusSnapshotUrl
                        }
                        if (overrides.nexusCredentialsId) {
                            config['artifact.repository.credentials.id'] = overrides.nexusCredentialsId
                        }

                        ciConfig.requireProperties(
                                config,
                                'artifact.public.repository.url',
                                'node.java.home',
                                'node.maven.home'
                        )

                        ciMaven.writeSettings(config)

                        env.JAVA_HOME  = config['node.java.home']
                        env.MAVEN_HOME = config['node.maven.home']
                        env.PATH       = "${config['node.maven.home']}:${env.PATH}"
                    }

                    sh 'pwd; mvn compile -s settings-ci.xml'
                }
            }

            stage('Test') {
                steps {
                    sh 'mvn test -s settings-ci.xml'
                }
            }

            stage('Code Quality') {
                steps {
                    echo 'TODO: SonarQube analysis'
                }
            }

            stage('Build Image') {
                steps {
                    script {

                        def config = ciConfig.read()

                        ciConfig.requireProperties(
                                config,
                                'registry.url',
                                'registry.namespace',
                                'registry.credentials.id'
                        )

                        def repositoryUrl = sh(
                                script: "git config --get remote.origin.url",
                                returnStdout: true
                        ).trim()

                        def imageName = repositoryUrl.tokenize('/').last().replace('.git', '')

                        def version = sh(
                                script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout",
                                returnStdout: true
                        ).trim()

                        def finalName = sh(
                                script: "mvn help:evaluate -Dexpression=project.build.finalName -q -DforceStdout -Pspring-boot-app",
                                returnStdout: true
                        ).trim()

                        def branchName = overrides.branch ?: params.Branch ?: 'main'
                        def imageTag   = "${sanitizeTagPart(branchName)}-${version}"

                        env.IMAGE_FULL_NAME =
                                "${config['registry.url']}/${config['registry.namespace']}/${imageName}:${imageTag}"

                        echo "Image: ${env.IMAGE_FULL_NAME}"

                        sh 'mvn package -DskipTests -Pspring-boot-app -s settings-ci.xml'

                        sh "rm -rf ${env.IMAGE_CONTEXT_DIR}"
                        sh "mkdir -p ${env.IMAGE_CONTEXT_DIR}"

                        sh "cp target/${finalName}.jar ${env.IMAGE_CONTEXT_DIR}/app.jar"

                        copyContainerResource('server.xml')
                        copyContainerResource('jvm.options')

                        writeFile(
                                file: "${env.IMAGE_CONTEXT_DIR}/Containerfile",
                                text: libraryResource('container/liberty/Containerfile.template')
                        )

                        sh "podman build -t ${env.IMAGE_FULL_NAME} -f ${env.IMAGE_CONTEXT_DIR}/Containerfile ${env.IMAGE_CONTEXT_DIR}"

                        env.REGISTRY_URL            = config['registry.url']
                        env.REGISTRY_CREDENTIALS_ID = config['registry.credentials.id']
                    }
                }
            }

            stage('Publish Image') {
                steps {
                    script {
                        withCredentials([usernamePassword(
                                credentialsId: env.REGISTRY_CREDENTIALS_ID,
                                usernameVariable: 'REGISTRY_USERNAME',
                                passwordVariable: 'REGISTRY_PASSWORD'
                        )]) {
                            podmanLogin(env.REGISTRY_URL)
                            sh "podman push ${env.IMAGE_FULL_NAME}"
                        }
                    }
                }
            }
        }

        post {
            always {
                deleteDir()
            }
        }
    }
}

def copyContainerResource(String fileName) {
    def overridePath = "src/main/liberty/config/${fileName}"
    def targetPath   = "${env.IMAGE_CONTEXT_DIR}/${fileName}"

    if (fileExists(overridePath)) {
        sh "cp ${overridePath} ${targetPath}"
    } else {
        writeFile(
                file: targetPath,
                text: libraryResource("container/liberty/config/${fileName}")
        )
    }
}

def sanitizeTagPart(String value) {
    return value
            .replaceAll('/', '-')
            .replaceAll('[^A-Za-z0-9_.-]', '-')
            .toLowerCase()
}

def podmanLogin(String registryUrl) {
    sh """
        echo \$REGISTRY_PASSWORD | podman login ${registryUrl} \
        -u \$REGISTRY_USERNAME --password-stdin
    """
}
