#!/usr/bin/env groovy

def call(Map config = [:]) {
    def gitUrl           = config.get('gitUrl', '[github.com](https://github.com/VictorinCondei/ocp-frontend-ci.git)')
    def gitCredentialsId = config.get('gitCredentialsId', 'github-Victorin')
    def registry         = config.get('registry', 'quay.apps.ocp1.cpd.fiscnet.ro')
    def organization     = config.get('organization', 'portal')
    def imageName        = config.imageName ?: error('imageName is required')
    def credentialsId    = config.get('credentialsId', 'quay-robot-creds')

    def gitBranch        = config.get('branch', 'main')
    def nodeLabel        = config.get('nodeLabel', 'jenkins-node')
    def dockerContext    = config.get('context', '.')
    def dockerfile       = config.get('dockerfile', 'Dockerfile')
    def pushLatest       = config.get('pushLatest', true)
    def extraBuildArgs   = config.get('buildArgs', '')

    pipeline {
        agent { label nodeLabel }

        options {
            timestamps()
            disableConcurrentBuilds()
            buildDiscarder(logRotator(numToKeepStr: '10'))
        }

        environment {
            REGISTRY       = "${registry}"
            ORGANIZATION   = "${organization}"
            IMAGE_NAME     = "${imageName}"
            DOCKERFILE     = "${dockerfile}"
            DOCKER_CONTEXT = "${dockerContext}"
            BUILD_ARGS     = "${extraBuildArgs}"
        }

        stages {
            stage('Checkout') {
                steps {
                    deleteDir()
                    git(
                        branch: gitBranch,
                        credentialsId: gitCredentialsId,
                        url: gitUrl
                    )
                    script {
                        def meta = resolveImageMeta(registry, organization, imageName)
                        echo "Checked out ${gitUrl} branch ${gitBranch}"
                        echo "Resolved image: ${meta.fullImage}:${meta.imageTag}"
                    }
                }
            }

            stage('Validate') {
                steps {
                    script {
                        validateStrapiProject()
                    }
                }
            }

            stage('Build Image') {
                steps {
                    script {
                        def meta = resolveImageMeta(registry, organization, imageName)

                        buildStrapiImage(
                            fullImage: meta.fullImage,
                            tag: meta.imageTag,
                            dockerfile: dockerfile,
                            context: dockerContext,
                            buildArgs: extraBuildArgs
                        )
                    }
                }
            }

            stage('Push Image') {
                steps {
                    script {
                        def meta = resolveImageMeta(registry, organization, imageName)

                        pushImageToQuay(
                            registry: registry,
                            fullImage: meta.fullImage,
                            tag: meta.imageTag,
                            credentialsId: credentialsId,
                            pushLatest: pushLatest
                        )
                    }
                }
            }

            stage('Cleanup') {
                steps {
                    script {
                        def meta = resolveImageMeta(registry, organization, imageName)
                        cleanupImages(meta.fullImage, meta.imageTag, pushLatest)
                    }
                }
            }
        }

        post {
            always {
                sh "podman logout ${registry} || true"
            }
            success {
                script {
                    def meta = resolveImageMeta(registry, organization, imageName)
                    echo "Image pushed successfully: ${meta.fullImage}:${meta.imageTag}"
                }
            }
            failure {
                script {
                    def meta = resolveImageMeta(registry, organization, imageName)
                    echo "Pipeline failed for image: ${meta.fullImage}:${meta.imageTag}"
                }
            }
        }
    }
}

def resolveImageMeta(String registry, String organization, String imageName) {
    def shortSha = env.GIT_COMMIT?.take(8)
    if (!shortSha) {
        shortSha = sh(
            script: "git rev-parse --short=8 HEAD",
            returnStdout: true
        ).trim()
    }

    return [
        fullImage: "${registry}/${organization}/${imageName}",
        imageTag : "${env.BUILD_NUMBER}-${shortSha}"
    ]
}
