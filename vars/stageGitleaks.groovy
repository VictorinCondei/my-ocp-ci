def call(Map config = [:]) {

    // ── defaults ────────────────────────────────────────────────────────────
    String gitleaksImage = config.get('gitleaksImage', 'ghcr.io/gitleaks/gitleaks:v8.21.2')
    String reportDir     = config.get('reportDir',     'security-reports')
    String reportFile    = "${reportDir}/gitleaks-report.sarif"
    boolean failOnLeaks  = config.get('failOnSecrets', true)
    String configArg     = config.containsKey('configFile')
                            ? "--config=${config.configFile}"
                            : ''

    stage('Security — Gitleaks') {

        // Create output dir inside the workspace
        sh "mkdir -p ${reportDir}"

        // Run Gitleaks inside a container that mounts the workspace.
        // --no-git prevents Gitleaks from trying to traverse .git while
        //   the workspace is already a detached checkout on many CI agents.
        // Remove --no-git if you want full history scanning.
        int exitCode = sh(
            returnStatus: true,
            script: """
                podman run --rm \\
                    -v "\${WORKSPACE}:/repo:ro" \\
                    -w /repo \\
                    ${gitleaksImage} \\
                    detect \\
                    --source /repo \\
                    ${configArg} \\
                    --report-format sarif \\
                    --report-path /repo/${reportFile} \\
                    --exit-code 1 \\
                    --log-level info
            """
        )

        // Archive the SARIF report regardless of outcome
        archiveArtifacts(
            artifacts: "${reportDir}/gitleaks-report.sarif",
            allowEmptyArchive: true
        )

        // Optionally publish with the Warnings-NG / SARIF plugin
        if (fileExists(reportFile)) {
            recordIssues(
                tools: [sarif(pattern: reportFile, id: 'gitleaks', name: 'Gitleaks')],
                qualityGates: [[threshold: 1, type: 'TOTAL', unstable: !failOnLeaks]]
            )
        }

        if (exitCode != 0 && failOnLeaks) {
            error("Gitleaks detected secrets in the repository. Build failed.")
        }

        if (exitCode != 0 && !failOnLeaks) {
            unstable("Gitleaks detected secrets — build marked UNSTABLE.")
        }
    }
}
