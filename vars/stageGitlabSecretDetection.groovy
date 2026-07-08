def call(Map config = [:]) {

    // ── defaults ────────────────────────────────────────────────────────────
    String analyzerImage = config.get(
        'analyzerImage',
        'registry.gitlab.com/security-products/secrets:5'
    )
    String reportDir  = config.get('reportDir', 'security-reports')
    String reportFile = "${reportDir}/gl-secret-detection-report.json"
    boolean failOnLeaks = config.get('failOnSecrets', true)

    stage('Security — GitLab Secret Detection') {

        sh "[ -e ${reportDir} ] || mkdir -p ${reportDir}"

        int exitCode = sh(
            returnStatus: true,
            script: """
                podman run --rm \\
                    -e CI_PROJECT_DIR=/repo \\
                    -e SECURE_LOG_LEVEL=info \\
                    -v "\${WORKSPACE}:/repo" \\
                    ${analyzerImage}
                # Move report to the configured output dir
                if [ -f "\${WORKSPACE}/gl-secret-detection-report.json" ]; then
                    mv "\${WORKSPACE}/gl-secret-detection-report.json" \\
                       "\${WORKSPACE}/${reportFile}"
                fi
            """
        )

        archiveArtifacts(
            artifacts: "${reportFile}",
            allowEmptyArchive: true
        )

        int vulnCount = 0
        if (fileExists(reportFile)) {
            def report = readJSON file: reportFile
            vulnCount = report?.vulnerabilities?.size() ?: 0
            echo "GitLab Secret Detection found ${vulnCount} vulnerability(ies)."
        }

        if (vulnCount > 0 && failOnLeaks) {
            error("GitLab Secret Detection found ${vulnCount} secret(s). Build failed.")
        }

        if (vulnCount > 0 && !failOnLeaks) {
            unstable("GitLab Secret Detection found ${vulnCount} secret(s) — build UNSTABLE.")
        }
    }
}