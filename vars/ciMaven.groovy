def writeSettings(Map config) {
    ciConfig.requireProperties(
            config,
            'artifact.repository.credentials.id',
            'artifact.public.repository.url',
            'artifact.release.repository.url',
            'artifact.snapshot.repository.url'
    )

    withCredentials([usernamePassword(
            credentialsId: config['artifact.repository.credentials.id'],
            usernameVariable: 'ARTIFACT_REPOSITORY_USERNAME',
            passwordVariable: 'ARTIFACT_REPOSITORY_PASSWORD'
    )]) {

        def settings = libraryResource('maven/settings.xml.template')
                .replace('${ARTIFACT_REPOSITORY_USERNAME}', env.ARTIFACT_REPOSITORY_USERNAME)
                .replace('${ARTIFACT_REPOSITORY_PASSWORD}', env.ARTIFACT_REPOSITORY_PASSWORD)
                .replace('${ARTIFACT_REPOSITORY_PUBLIC_URL}', config['artifact.public.repository.url'])

        writeFile file: 'settings-ci.xml', text: settings
    }
}