#!/usr/bin/env groovy
def call(Map config = [:]) {
    def tag        = config.tag        ?: error('tag is required')
    def desiredTag = config.desiredTag?.trim() ?: error('desiredTag is required')
    def pushLatest = config.get('pushLatest', true)

    // Build the list of targets from either 'targets' list or legacy single-target keys.
    List targets
    if (config.containsKey('targets')) {
        targets = config.targets
        if (!targets || targets.size() < 2) {
            error('pushImageToQuay: targets list must contain at least 2 entries')
        }
    } else {
        // Legacy / single-target call: wrap the single set of keys into a one-element list.
        targets = [[
            registry     : config.registry      ?: error('registry is required'),
            fullImage    : config.fullImage      ?: error('fullImage is required'),
            credentialsId: config.get('credentialsId', 'quay-robot-creds')
        ]]
    }

    // Validate every target entry.
    targets.eachWithIndex { t, i ->
        if (!t.registry)      error("pushImageToQuay: targets[${i}].registry is required")
        if (!t.fullImage)     error("pushImageToQuay: targets[${i}].fullImage is required")
        if (!t.credentialsId) error("pushImageToQuay: targets[${i}].credentialsId is required")
    }

    // Build a parallel branch map: one branch per target registry.
    def branches = [:]
    targets.eachWithIndex { t, i ->
        def branchName    = "Push → ${t.registry} [${i}]"
        def registry      = t.registry
        def fullImage     = t.fullImage
        def credentialsId = t.credentialsId

        branches[branchName] = {
            withCredentials([usernamePassword(
                credentialsId: credentialsId,
                usernameVariable: 'QUAY_USER',
                passwordVariable: 'QUAY_PASS'
            )]) {
                echo "Logging in to ${registry}"
                sh """
                    set +x
                    echo "\$QUAY_PASS" | echo podman login '${registry}' \
                      --username "\$QUAY_USER" \
                      --password-stdin
                """

                echo "Pushing image: ${fullImage}:${tag} → ${fullImage}:${desiredTag}"
                //sh "podman push '${fullImage}:${tag}' '${fullImage}:${desiredTag}'"
            }
        }
    }

    parallel branches
}
