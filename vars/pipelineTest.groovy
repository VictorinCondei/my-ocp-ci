#!/usr/bin/env groovy

def call(Map config = [:]) {

    // -----------------------------------------------------------------------
    // Resolve configuration with defaults
    // -----------------------------------------------------------------------
    def gitUrl            = config.get('gitUrl',           'https://github.com/VictorinCondei/ocp-frontend-ci.git')
    def gitCredentialsId  = config.get('gitCredentialsId', 'github-Victorin')
    def gitBranch         = config.get('branch',           'main')
    def nodeLabel         = config.get('nodeLabel',        'jenkins-node')
    def imageName         = config.imageName   ?: error('pipelineTest: imageName is required')
    def desiredTag        = config.desiredTag?.trim() ?: error('pipelineTest: desiredTag is required')
    def dockerfile        = config.get('dockerfile', 'Dockerfile')
    def dockerContext     = config.get('context',    '.')
    def extraBuildArgs    = config.get('buildArgs',  '')
    def pushLatest        = config.get('pushLatest', true)

    List quayTargets = config.quayTargets
    if (!quayTargets || quayTargets.size() < 2) {
        error('pipelineTest: quayTargets must contain at least 2 entries')
    }

    // Validate every quayTargets entry up-front so failures are immediate.
    quayTargets.eachWithIndex { t, i ->
        if (!t.registry)      error("pipelineTest: quayTargets[${i}].registry is required")
        if (!t.organization)  error("pipelineTest: quayTargets[${i}].organization is required")
        if (!t.credentialsId) error("pipelineTest: quayTargets[${i}].credentialsId is required")
    }

    // -----------------------------------------------------------------------
    // Pipeline definition
    // -----------------------------------------------------------------------
    pipeline {
        agent { label nodeLabel }

        options {
            timestamps()
            disableConcurrentBuilds()
            buildDiscarder(logRotator(numToKeepStr: '10'))
        }

        environment {
            IMAGE_NAME     = "${imageName}"
            DOCKERFILE     = "${dockerfile}"
            DOCKER_CONTEXT = "${dockerContext}"
            BUILD_ARGS     = "${extraBuildArgs}"
        }

        stages {

            // -----------------------------------------------------------------
            stage('Checkout') {
            // -----------------------------------------------------------------
                steps {
                    deleteDir()
                    echo "Cloning ${gitUrl} @ ${gitBranch}"
                    git(
                        branch       : gitBranch,
                        credentialsId: gitCredentialsId,
                        url          : gitUrl
                    )
                    script {
                        env.IMAGE_TAG = resolveImageTag()
                        echo "Image tag: ${env.IMAGE_TAG}"
                    }
                }
            }

            // -----------------------------------------------------------------
            stage('Validate') {
            // -----------------------------------------------------------------
                steps {
                    script {
                        sh "echo Validate"
                        //validateProject()
                    }
                }
            }

            // -----------------------------------------------------------------
            // Build once against the primary (first) registry.
            // The same local image is re-tagged for every additional registry
            // in the Push Image stage to avoid rebuilding multiple times.
            // -----------------------------------------------------------------
            stage('Build Image') {
            // -----------------------------------------------------------------
                steps {
                    script {
                        def primary   = quayTargets[0]
                        def fullImage = "${primary.registry}/${primary.organization}/${imageName}"

                        //buildPortalImage(
                        //    fullImage : fullImage,
                        //    tag       : env.IMAGE_TAG,
                        //    dockerfile: dockerfile,
                        //    context   : dockerContext,
                        //    buildArgs : extraBuildArgs
                        //)
                    }
                }
            }

            // -----------------------------------------------------------------
            stage('Push Image') {
            // -----------------------------------------------------------------
                steps {
                    script {
                        def primary          = quayTargets[0]
                        def primaryFullImage = "${primary.registry}/${primary.organization}/${imageName}"

                        // Re-tag for every registry beyond the first.
                        quayTargets.eachWithIndex { t, i ->
                            if (i > 0) {
                                def destFullImage = "${t.registry}/${t.organization}/${imageName}"
                                sh "podman tag '${primaryFullImage}:${env.IMAGE_TAG}' '${destFullImage}:${env.IMAGE_TAG}'"
                                echo "Tagged → ${destFullImage}:${env.IMAGE_TAG}"
                            }
                        }

                        // Build targets list for pushImageToQuay.
                        def pushTargets = quayTargets.collect { t ->
                            [
                                registry     : t.registry,
                                fullImage    : "${t.registry}/${t.organization}/${imageName}",
                                credentialsId: t.credentialsId
                            ]
                        }

                        // Push to all registries in parallel.
                        pushImageToMultipleQuay(
                            tag       : env.IMAGE_TAG,
                            desiredTag: desiredTag,
                            pushLatest: pushLatest,
                            targets   : pushTargets
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            stage('Cleanup') {
            // -----------------------------------------------------------------
                steps {
                    script {
                        quayTargets.each { t ->
                            def fullImage = "${t.registry}/${t.organization}/${imageName}"
                        //    cleanupImages(fullImage, env.IMAGE_TAG, pushLatest)
                        }
                    }
                }
            }

        } // end stages

        post {
            always {
                script {
                    quayTargets.each { t ->
                        sh "echo podman logout '${t.registry}' || true"
                    }
                }
            }
            success {
                script {
                    def pushed = quayTargets.collect { t ->
                        "  ${t.registry}/${t.organization}/${imageName}:${env.IMAGE_TAG}"
                    }.join('\n')
                    echo "Image pushed successfully to:\n${pushed}"
                }
            }
            failure {
                echo "Pipeline failed — check stage logs above for details."
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Private helper — resolves the image tag for the current build.
// Prefers the GIT_COMMIT env var set by Jenkins SCM plugins; falls back to
// a shell call so the function works even when GIT_COMMIT is not populated.
// ---------------------------------------------------------------------------
private String resolveImageTag() {
    def shortSha = env.GIT_COMMIT?.take(8)
    if (!shortSha) {
        shortSha = sh(
            script      : 'git rev-parse --short=8 HEAD',
            returnStdout: true
        ).trim()
    }
    return "${env.BUILD_NUMBER}-${shortSha}"
}
