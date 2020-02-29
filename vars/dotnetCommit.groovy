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
  - name: dotnet-builder
    image: blackhorseya/dotnet-builder:3.1-alpine
    command:
    - cat
    tty: true
"""
            }
        }
        stages {
            stage('Prepare') {
                steps {
                    echo "branch name: ${env.GIT_BRANCH}"
                    sh label: "print all environment variable", script: "printenv | sort"
                }
            }

            stage('Build') {
                steps {
                    container('dotnet-builder') {
                        sh label: "dotnet build", script: """
                        dotnet build -c Release -o ./publish
                        """
                    }
                }
            }

            stage('Test') {
                steps {
                    container('dotnet-builder') {
                        sh label: "dotnet test", script: """
                        dotnet test
                        """
                    }
                }
            }
        }
    }
}