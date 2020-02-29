def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    pipeline {
        environment {
            FULL_VERSION = "${config.Version}.${BUILD_ID}"
        }
        agent {
            kubernetes {
                yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: dotnet-builder
    image: blackhorseya/dotnet-builder:3.1-alpine
    command: ['cat']
    tty: true
  - name: docker
    image: docker:latest
    command: ['cat']
    tty: true
    volumeMounts:
    - name: dockersock
      mountPath: /var/run/docker.sock
  - name: helm
    image: alpine/helm:3.0.1
    command: ['cat']
    tty: true
  volumes:
  - name: dockersock
    hostPath:
      path: /var/run/docker.sock
"""
            }
        }
        stages {
            stage('Prepare') {
                steps {
                    echo """
Perform ${env.JOB_NAME} for
Repo: ${env.GIT_URL}
Branch: ${env.GIT_BRANCH}
Application: ${config.AppName}:${config.Version}
"""
                    sh '''
                    echo ### Print env ###
                    printenv | sort
                    '''
                }
            }

            stage('Build') {
                steps {
                    container('dotnet-sdk') {
                        sh '''
                        echo ### dotnet build ###
                        dotnet build -c Release -o ./publish
                        '''
                    }
                }
            }

            stage('Test') {
                steps {
                    container('dotnet-sdk') {
                        sh '''
                        echo ### dotnet test ###
                        dotnet test
                        '''
                    }
                }
            }
        }
    }
}