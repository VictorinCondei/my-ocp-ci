# ANAF OCP Frontend CI

Shared Jenkins Pipeline Library used by ANAF OCP Frontend.

## Jenkins Prerequisites

The following Jenkins plugins are required.

| Plugin | Purpose |
|----------|----------|
| Git | Source code checkout from Git repositories |
| Git Client | Git operations implementation used by Jenkins |
| Credentials | Central credentials storage |
| Credentials Binding | Inject credentials into pipeline steps using `withCredentials(...)` |
| SSH Credentials | SSH key support for Git repositories |
| Pipeline | Core Jenkins Pipeline support |
| Pipeline: Groovy | Groovy execution for Jenkinsfiles and shared libraries |
| Pipeline: Shared Groovy Libraries | Support for Jenkins Shared Libraries (`@Library`) |
| Pipeline Utility Steps | Utility steps such as `readProperties(...)` |

## Jenkins Global Configuration


## Jenkins Credentials


## Jenkins Shared Library


## Usage

Example Jenkinsfile:

```groovy
@Library('anaf-ocp-frontend-ci') _

pipelineStrapi(
    registry:      'quay.apps.ocp1.cpd.fiscnet.ro',
    organization:  'portal',
    imageName:     'anaf-ocp-service-registre',
    credentialsId: 'quay-robot-creds',
    branch:        'main',
    nodeLabel:     'jenkins-node',
    dockerfile:    'Dockerfile',
    context:       '.',
    pushLatest:    true,
    buildArgs:     'NODE_ENV=production DATABASE_CLIENT=postgres'
)
```
