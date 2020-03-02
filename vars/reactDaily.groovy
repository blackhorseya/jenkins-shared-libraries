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
            DOCKER_REGISTRY_URL = "https://registry.hub.docker.com/"
            DOCKER_REGISTRY_ID = "docker-hub-credential"
            DOCKER_REGISTRY_CRED = credentials("${DOCKER_REGISTRY_ID}")

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
  - name: node
    image: node:alpine
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
                    sh label: "print all environment variable", script: "printenv | sort"

                    container('node') {
                        script {
                            APP_NAME = sh(
                                    script: 'yarn -s get-name',
                                    returnStdout: true
                            ).trim()
                            VERSION = sh(
                                    script: 'yarn -s get-version',
                                    returnStdout: true
                            ).trim()
                        }
                    }
                    
                    container('docker') {
                        sh label: "print docker info and version", script: """
                        docker info
                        docker version
                        """
                    }

                    container('helm') {
                        sh label: "print helm version", script: "helm version"
                        sh label: "copy kube config to /root/.kube/", script: """
                        mkdir -p /root/.kube/ && cp ${KUBE_CONFIG_FILE} /root/.kube/config
                        """
                    }

                    echo """
Perform ${env.JOB_NAME} for
Repo: ${env.GIT_URL}
Branch: ${env.GIT_BRANCH}
Application: ${APP_NAME}:${FULL_VERSION}
"""
                }
            }

            stage('Build') {
                steps {
                    container('node') {
                        sh label: "yarn install and build", script: """
                        yarn install
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

            stage('Build and push docker image') {
                steps {
                    container('docker') {
                        script {
                            docker.withRegistry("${DOCKER_REGISTRY_URL}", "${DOCKER_REGISTRY_ID}") {
                                def image = docker.build("${IMAGE_NAME}:${FULL_VERSION}", "--network host .")
                                image.push()
                                image.push('latest')
                            }
                        }
                    }
                }
            }

            stage('Deploy') {
                steps {
                    container('helm') {
                        sh label: "print all release", script: """
                        helm --namespace=${KUBE_NS} list
                        """
                        sh label: "deploy to ${KUBE_NS} with ${IMAGE_NAME}:${FULL_VERSION}", script: """
                        helm --namespace=${KUBE_NS} upgrade --install dev-${APP_NAME} deploy/helm \
                        -f deploy/config/dev/values.yaml \
                        --set image.tag=${FULL_VERSION}
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