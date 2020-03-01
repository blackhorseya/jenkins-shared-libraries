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

            CGO_ENABLED = '0'
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
  - name: sls
    image: node:alpine
    command: ['cat']
    tty: true
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
                        go mod download
                        """
                    }

                    container('sls') {
                        sh label: "install package", script: """
                        yarn global add serverless
                        sls version
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
                        sh label: "golang test with code coverage and test report", script: """
                        go test -v ./... -coverprofile=cover.out -json > test.out
                        """
                    }
                }
            }

            stage('Static Code Analysis') {
                steps {
                    container('sonar-scanner') {
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

            stage('Deploy') {
                steps {
                    container('sls') {
                        sh label: "deploy to ${KUBE_NS}", script: """
                        make deploy
                        """
                    }
                    sshagent(['github-ssh']) {
                        sh label: "git tag new v${VERSION}-alpha", script: """
                        git tag --delete v${VERSION}-alpha | exit 0 && git push --delete origin v${VERSION}-alpha | exit 0
                        git tag v${VERSION}-alpha && git push --tags
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