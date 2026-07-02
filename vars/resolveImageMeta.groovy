def resolveImageMeta() {
    def shortSha  = env.GIT_COMMIT?.take(8) ?: sh(script: "git rev-parse --short=8 HEAD", returnStdout: true).trim()
    [
        fullImage: "${registry}/${organization}/${imageName}",
        imageTag : "${env.BUILD_NUMBER}-${shortSha}"
    ]
}
