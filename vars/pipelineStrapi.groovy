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
                        resolveImageMeta()
                        echo "Checked out ${gitUrl} branch ${gitBranch}"
                        echo "Resolved image: ${env.FULL_IMAGE}:${env.IMAGE_TAG}"
                    }
                }
            }

            stage('Validate') {
                steps {
                    script {
                        echo"Validate";
                    }
                }
            }

            stage('Build Image') {
                steps {
                    script {
                        buildStrapiImage(
                            fullImage: env.FULL_IMAGE,
                            tag: env.IMAGE_TAG,
                            dockerfile: env.DOCKERFILE,
                            context: env.DOCKER_CONTEXT,
                            buildArgs: env.BUILD_ARGS
                        )
                    }
                }
            }

            stage('Push Image') {
                steps {
                    script {
                        pushImageToQuay(
                            registry: env.REGISTRY,
                            fullImage: env.FULL_IMAGE,
                            tag: env.IMAGE_TAG,
                            credentialsId: credentialsId,
                            pushLatest: pushLatest
                        )
                    }
                }
            }

            stage('Cleanup') {
                steps {
                    script {
                        cleanupImages(env.FULL_IMAGE, env.IMAGE_TAG, pushLatest)
                    }
                }
            }
        }

        post {
            always {
                sh "podman logout ${env.REGISTRY} || true"
            }
            success {
                echo "Image pushed successfully: ${env.FULL_IMAGE}:${env.IMAGE_TAG}"
            }
            failure {
                echo "Pipeline failed for image: ${env.FULL_IMAGE}:${env.IMAGE_TAG}"
            }
        }
    }
}
