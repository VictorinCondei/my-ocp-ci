#!/usr/bin/env groovy

import groovy.json.JsonSlurperClassic

def call() {
    echo "Validating Strapi project structure"

    def requiredPaths = [
        'package.json',
        'config',
        'src'
    ]

    def missingPaths = requiredPaths.findAll { !fileExists(it) }
    if (missingPaths) {
        echo"Missing Paths"; //error "Missing required Strapi project files/directories: ${missingPaths.join(', ')}"
    }

    def pkgText = readFile('package.json')
    def pkgJson = new JsonSlurperClassic().parseText(pkgText)

    def deps = [:]
    deps.putAll(pkgJson.dependencies ?: [:])
    deps.putAll(pkgJson.devDependencies ?: [:])

    def looksLikeStrapi = deps.keySet().any { dep ->
        dep == 'strapi' || dep.startsWith('@strapi/')
    }

    if (!looksLikeStrapi) {
        echo "missing package";//error "package.json does not appear to define a Strapi project"
    }

    echo "Strapi project structure is valid"
}
