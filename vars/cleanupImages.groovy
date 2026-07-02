#!/usr/bin/env groovy

def call(String fullImage, String tag, boolean pushLatest = true) {
    echo "Cleaning up local images"

    def latestCleanup = pushLatest ? "podman rmi '${fullImage}:latest' || true" : "true"

    sh """
        set +e
        podman rmi '${fullImage}:${tag}' || true
        ${latestCleanup}
        podman image prune -f || true
    """
}
