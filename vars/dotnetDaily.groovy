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
                    
                    container('dotnet-builder') {
                        sh label: "print dotnet info", script: "dotnet --info"
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
                }
            }

            stage('Build') {
                steps {
                    container('dotnet-builder') {
                        sh label: "sonarscanner begin", script: """
                        dotnet sonarscanner begin /k:\"${APP_NAME}\" \
                        /v:${FULL_VERSION} \
                        /d:sonar.host.url=${SONARQUBE_HOST_URL} \
                        /d:sonar.login=${SONARQUBE_TOKEN} \
                        /d:sonar.exclusions=**/*.js,**/*.ts,**/*.css,bin/**/*,obj/**/*,wwwroot/**/*,ClientApp/**/* \
                        /d:sonar.cs.opencover.reportsPaths=${PWD}/coverage/coverage.opencover.xml \
                        /d:sonar.coverage.exclusions=**/Entities/**/*,test/**/* \
                        /d:sonar.cs.vstest.reportsPaths=${PWD}/TestResults/report.trx
                        """
                        sh label: "dotnet build", script: """
                        dotnet build -c Release -o ./publish
                        """
                    }
                }
            }

            stage('Test') {
                steps {
                    container('dotnet-builder') {
                        sh label: "dotnet test with code coverage and test report", script: """
                        dotnet test /p:CollectCoverage=true \
                        /p:CoverletOutputFormat=opencover \
                        /p:CoverletOutput=${PWD}/coverage/ \
                        --logger trx \
                        -r ./TestResults/report.trx \
                        -o ./publish \
                        --no-build --no-restore
                        """
                    }
                }
            }

            stage('Static Code Analysis') {
                steps {
                    container('dotnet-builder') {
                        sh label: "sonarscanner end", script: """
                        dotnet sonarscanner end /d:sonar.login=${SONARQUBE_TOKEN}
                        """
                    }
                }
            }

            stage('Build and push docker image') {
                steps {
                    container('docker') {
                        sh label: "docker build image", script: """
                        docker build -t ${IMAGE_NAME}:latest -f Dockerfile --network host .
                        """
                        sh label: "docker login to dockerhub", script: """
                        docker login --username ${DOCKERHUB_USR} --password ${DOCKERHUB_PSW}
                        """
                        sh label: "docker tag and push image ${FULL_VERSION}", script: """
                        docker tag ${IMAGE_NAME}:latest ${IMAGE_NAME}:${FULL_VERSION}
                        docker push ${IMAGE_NAME}:latest
                        docker push ${IMAGE_NAME}:${FULL_VERSION}
                        """
                        sh label: "print all ${IMAGE_NAME}", script: """
                        docker images ${IMAGE_NAME}"""
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