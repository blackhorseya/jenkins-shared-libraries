def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    pipeline {
        environment {
            // application settings
            APP_NAME = "${config.AppName}"
            VERSION = "${config.Version}"
            FULL_VERSION = "${VERSION}.${BUILD_ID}"
            IMAGE_NAME = "${DOCKERHUB_USR}/${APP_NAME}"

            // docker credentials
            DOCKERHUB = credentials('docker-hub-credential')

            // sonarqube settings
            SONARQUBE_HOST_URL = "https://sonar.blackhorseya.com"
            SONARQUBE_TOKEN = credentials('sonarqube-token')

            // kubernetes settings
            KUBE_NS = "${config.KubeNamespace}"
            KUBE_CONFIG_FILE = credentials('kube-config')
        }
        agent {
            kubernetes {
                yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: builder
    image: golang:alpine
    command: ['cat']
    tty: true
  - name: sonar-scanner
    image: sonarsource/sonar-scanner-cli
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
    image: alpine/helm:3.1.0
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
Application: ${APP_NAME}:${FULL_VERSION}
"""

                    sh label: "print all environment variable", script: "printenv | sort"
                    
                    container('builder') {
                        sh label: "print builder version", script: "go version"
                        sh label: "install package", script: """
                        apk add --no-cache make
                        go get -u github.com/jstemmer/go-junit-report
                        go get -u github.com/axw/gocov/...
                        go get -u github.com/AlekSi/gocov-xml
                        go get -u gopkg.in/alecthomas/gometalinter.v1
                        gometalinter.v1 --install
                        go mod download
                        """
                    }
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
                    container('builder') {
                        sh label: "dotnet test with code coverage and test report", script: """
                        go test -v ./... -coverprofile=cover.out | go-junit-report > test.xml
                        gocov convert cover.out | gocov-xml > coverage.xml
                        """
                    }
                }
            }

            stage('Static Code Analysis') {
                steps {
                    container('sonar-scanner') {
                        sh label: "style check", script: """
                        gometalinter.v1 --checkstyle > report.xml
                        """
                        sh label: "sonar-scanner", script: """
                        sonar-scanner \
                        -Dsonar.projectKey=nester \
                        -Dsonar.projectVersion=${FULL_VERSION} \
                        -Dsonar.sources=. \
                        -Dsonar.host.url=${SONARQUBE_HOST_URL} \
                        -Dsonar.login=${SONARQUBE_TOKEN}
                        """
                    }
                }
            }
        }

        post {
            always {
                script {
                    def (x, repo) = "${GIT_URL}".split(':')
                    def prefixIcon = currentBuild.currentResult == 'SUCCESS' ? ':white_check_mark:' : ':x:'
                    def blocks = [
                        [
                        "type": "section",
                        "text": [
                            "type": "mrkdwn",
                            "text": "${prefixIcon} *<${BUILD_URL}|${JOB_NAME} #${FULL_VERSION}>*"
                        ]
                        ],
                        [
                        "type": "divider"
                        ],
                        [
                        "type": "section",
                        "fields": [
                            [
                            "type": "mrkdwn",
                            "text": "*:star: Build Status:*\n${currentBuild.currentResult}"
                            ],
                            [
                            "type": "mrkdwn",
                            "text": "*:star: Elapsed:*\n${currentBuild.durationString}"
                            ],
                            [
                            "type": "mrkdwn",
                            "text": "*:star: Job:*\n<${JOB_URL}|${JOB_NAME}>"
                            ],
                            [
                            "type": "mrkdwn",
                            "text": "*:star: Project:*\n<https://github.com/${repo}|Github>"
                            ],
                            [
                            "type": "mrkdwn",
                            "text": "*:star: Build Image:*\n<https://hub.docker.com/r/${DOCKERHUB_USR}/${APP_NAME}/tags|Docker hub>"
                            ]
                        ]
                        ]
                    ]
                    slackSend(blocks: blocks)
                }
            }
        }
    }
}