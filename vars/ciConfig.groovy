def read() {

    def config = readProperties(
        text: libraryResource("config/${env.CI_CONFIG_FILE}")
    )

    config['artifact.public.repository.url'] =
        env.NEXUS_PUBLIC_URL ?: config['artifact.public.repository.url']

    config['artifact.release.repository.url'] =
        env.NEXUS_RELEASE_URL ?: config['artifact.release.repository.url']

    config['artifact.snapshot.repository.url'] =
        env.NEXUS_SNAPSHOT_URL ?: config['artifact.snapshot.repository.url']

    config['artifact.repository.credentials.id'] =
        env.NEXUS_CREDENTIALS_ID ?: config['artifact.repository.credentials.id']

    return config
}