#!/usr/bin/env groovy

def call(Map config = [:]) {
    def registry      = config.registry ?: error('registry is required')
    def fullImage     = config.fullImage ?: error('fullImage is required')
    def tag           = config.tag ?: error('tag is required')
    def credentialsId = config.get('credentialsId', 'quay-robot-creds')
    def pushLatest    = config.get('pushLatest', true)

    withCredentials([usernamePassword(
        credentialsId: credentialsId,
        usernameVariable: 'QUAY_USER',
        passwordVariable: 'QUAY_PASS'
    )]) {
        echo "Logging in to ${registry}"
        sh """
            set +x
            echo "\$QUAY_PASS" | podman login '${registry}' \
              --username "\$QUAY_USER" \
              --password-stdin
        """

        echo "Pushing image: ${fullImage}:${tag}"
        sh "podman push '${fullImage}:${tag}'"

        if (pushLatest) {
            echo "Tagging and pushing latest"
            sh """
                set -eu
                podman tag '${fullImage}:${tag}' '${fullImage}:latest'
                podman push '${fullImage}:latest'
            """
        }
    }
}
