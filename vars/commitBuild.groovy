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
  - name: dotnet-sdk
    image: blackhorseya/dotnet-builder:3.1-alpine
    command:
    - cat
    tty: true
"""
            }
        }
        stages {
            stage ('Prepare') {
                echo config.HW
                echo config.P1
                echo config.Custom
            }
        }
    }
}