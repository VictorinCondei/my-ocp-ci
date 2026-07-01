#!/usr/bin/env groovy

def call() {
    echo "Validating Strapi project structure"

    def requiredFiles = [
        'package.json',
        'config',
        'src'
    ]

    def missingFiles = []

    requiredFiles.each { f ->
        if (!fileExists(f)) {
            missingFiles.add(f)
        }
    }

    if (missingFiles) {
        error "Missing required Strapi project files/dirs: ${missingFiles.join(', ')}"
    }

    // validate package.json contains strapi
    def pkgJson = readFile('package.json')
    if (!pkgJson.contains('@strapi/strapi') && !pkgJson.contains('strapi')) {
        error "package.json does not appear to be a Strapi project"
    }

    echo "Strapi project structure is valid"
}
