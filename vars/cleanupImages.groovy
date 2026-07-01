#!/usr/bin/env groovy

def call(String fullImage, String tag) {
    echo "Cleaning up local images"
    sh """
        podman rmi ${fullImage}:${tag} || true
        podman rmi ${fullImage}:latest || true
        podman image prune -f || true
    """
}
