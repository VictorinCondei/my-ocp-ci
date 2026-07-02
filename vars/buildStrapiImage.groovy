#!/usr/bin/env groovy

def call(Map config = [:]) {
    def fullImage  = config.fullImage ?: error('fullImage is required')
    def tag        = config.tag ?: error('tag is required')
    def dockerfile = config.get('dockerfile', 'Dockerfile')
    def context    = config.get('context', '.')
    def buildArgs  = config.get('buildArgs', '')

    if (dockerfile == 'Dockerfile' && !fileExists('Dockerfile')) {
        echo "No Dockerfile found in repository, using shared library Containerfile template"
        def template = libraryResource('container/strapi/Containerfile.template')
        writeFile file: 'Containerfile', text: template
        dockerfile = 'Containerfile'
    }

    echo "Building Strapi image: ${fullImage}:${tag}"

    def buildArgStr = ''
    if (buildArgs?.trim()) {
        buildArgs.trim().split(/\s+/).each { arg ->
            buildArgStr += " --build-arg ${arg}"
        }
    }

    sh """
        set -eu
        podman build \
          -f '${dockerfile}' \
          -t '${fullImage}:${tag}' \
          ${buildArgStr} \
          '${context}'
    """

    echo "Build completed: ${fullImage}:${tag}"
}
