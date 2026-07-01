#!/usr/bin/env groovy

def call(Map config = [:]) {
    def fullImage  = config.fullImage
    def tag        = config.tag
    def dockerfile = config.get('dockerfile', 'Dockerfile')
    def context    = config.get('context',    '.')
    def buildArgs  = config.get('buildArgs',  '')

    // use embedded Containerfile.template if no Dockerfile found in repo
    if (dockerfile == 'Dockerfile' && !fileExists('Dockerfile')) {
        echo "No Dockerfile found in repo, using shared library Containerfile.template"
        def template = libraryResource('container/strapi/Containerfile.template')
        writeFile file: 'Containerfile', text: template
        dockerfile = 'Containerfile'
    }

    echo "Building Strapi image: ${fullImage}:${tag}"

    def buildArgStr = ''
    if (buildArgs) {
        buildArgs.split(' ').each { arg ->
            buildArgStr += " --build-arg ${arg}"
        }
    }

    sh """
        podman build \
          -f ${dockerfile} \
          -t ${fullImage}:${tag} \
          ${buildArgStr} \
          ${context}
    """

    echo "Build completed: ${fullImage}:${tag}"
}
