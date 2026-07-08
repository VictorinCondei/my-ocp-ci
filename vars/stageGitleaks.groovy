//----plugin Warning
def call(Map config = [:]) {
    String gitleaksImage = config.get('gitleaksImage', 'ghcr.io/gitleaks/gitleaks:v8.21.2')
    String reportDir     = config.get('reportDir',     'security-reports')
    String reportFile    = "${reportDir}/gitleaks-report.sarif"
    boolean failOnLeaks  = config.get('failOnSecrets', true)
    String configArg     = config.containsKey('configFile')
                            ? "--config=${config.configFile}"
                            : ''

    stage('Security — Gitleaks') {

        if (!fileExists(reportDir)) {
            sh "mkdir -p ${reportDir}"
        }

        int exitCode = sh(
            returnStatus: true,
            script: """
                echo ${WORKSPACE}
                podman run --rm \\
                    -v "\${WORKSPACE}:/repo:ro" \\
                    -v "\${PWD}:/repo-analyzer:z" \\
                    -w /repo \\
                    ${gitleaksImage} \\
                    detect \\
                    --source /repo \\
                    ${configArg} \\
                    --report-format sarif \\
                    --report-path /repo-analyzer/${reportFile} \\
                    --exit-code 1 \\
                    --log-level info
            """
        )

        archiveArtifacts(
            artifacts: "${reportDir}/gitleaks-report.sarif",
            allowEmptyArchive: true
        )

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