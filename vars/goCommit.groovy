def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    pipeline {
        agent {
            kubernetes {
                yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: builder
    image: golang:alpine
    command:
    - cat
    tty: true
    securityContext:
      runAsUser: 0
      runAsUser: 0
      allowPrivilegeEscalation: false
"""
            }
        }
        stages {
            stage('Prepare') {
                steps {
                    echo "branch name: ${env.GIT_BRANCH}"
                    sh label: "print all environment variable", script: "printenv | sort"
                    sh label: "install package", script: """
                    id
                    apk add --no-cache make
                    """
                }
            }

            stage('Build') {
                steps {
                    container('builder') {
                        sh label: "golang build", script: """
                        make build
                        """
                    }
                }
            }

            stage('Test') {
                steps {
                    container('dotnet-builder') {
                        sh label: "golang test", script: """
                        make test
                        """
                    }
                }
            }
        }
    }
}