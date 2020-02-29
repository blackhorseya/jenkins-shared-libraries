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
  - name: node
    image: node:alpine
    command:
    - cat
    tty: true
"""
            }
        }
        stages {
            stage('Prepare') {
                steps {
                    container('node') {
                        echo "branch name: ${env.GIT_BRANCH}"
                        sh label: "print all environment variable", script: "printenv | sort"
                        sh label: "print node version", script: "node -v"
                        sh label: "print yarn version", script: "yarn -v"
                        sh label: "install package via yarn", script: "yarn install"
                    }
                }
            }

            stage('Build') {
                steps {
                    container('node') {
                        sh label: "yarn build", script: """
                        yarn build
                        """
                    }
                }
            }

            stage('Test') {
                steps {
                    container('node') {
                        sh label: "yarn test", script: """
                        CI=true yarn test
                        """
                    }
                }
            }
        }
    }
}