#!/usr/bin/env groovy

def call(Map config = [:]) {

    // ── required parameters ──────────────────────────────────────────────
    def gitUrl      = config.get('gitUrl','https://github.com/VictorinCondei/ocp-frontend-ci.git')
    def gitCredentialsId = config.get('gitCredentialsId', 'github-Victorin')
    def registry      = config.get('registry',     'quay.apps.ocp1.cpd.fiscnet.ro')
    def organization  = config.get('organization', 'portal')
    def imageName     = config.imageName ?: error('imageName is required')
    def credentialsId = config.get('credentialsId', 'quay-robot-creds')

    // ── optional parameters ──────────────────────────────────────────────
    def nodeLabel     = config.get('nodeLabel',   'jenkins-node')
    def dockerContext = config.get('context',     '.')
    def dockerfile    = config.get('dockerfile',  'Dockerfile')
    def pushLatest    = config.get('pushLatest',  true)
    def extraBuildArgs= config.get('buildArgs',   '')

    // ── derived values  ─────────────────────────────────
    def shortSha  = env.GIT_COMMIT?.take(8) ?: 'unknown'
    def imageTag  = "${env.BUILD_NUMBER}-${shortSha}"
    def fullImage = "${registry}/${organization}/${imageName}"

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
            FULL_IMAGE     = "${fullImage}"
            IMAGE_TAG      = "${imageTag}"
            DOCKERFILE     = "${dockerfile}"
            DOCKER_CONTEXT = "${dockerContext}"
            BUILD_ARGS     = "${extraBuildArgs}"
        }

        stages {
            stage('Checkout') {
                steps {
                    checkout scm
                }
            }

            stage('Validate') {
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

            stage('Build Image') {
                steps {
                    script {
                        buildStrapiImage(
                            fullImage:  fullImage,
                            tag:        imageTag,
                            dockerfile: dockerfile,
                            context:    dockerContext,
                            buildArgs:  extraBuildArgs
                        )
                    }
                }
            }

            stage('Push Image') {
                steps {
                    script {
                        pushImageToQuay(
                            registry:      registry,
                            fullImage:     fullImage,
                            tag:           imageTag,
                            credentialsId: credentialsId,
                            pushLatest:    pushLatest
                        )
                    }
                }
            }

            stage('Cleanup') {
                steps {
                    script {
                        cleanupImages(fullImage, imageTag)
                    }
                }
            }
        }

        post {
            always {
                sh "podman logout ${registry} || true"
            }
            success {
                echo "Image pushed successfully: ${fullImage}:${imageTag}"
            }
            failure {
                echo "Pipeline failed for image: ${fullImage}:${imageTag}"
            }
        }
    }
}
