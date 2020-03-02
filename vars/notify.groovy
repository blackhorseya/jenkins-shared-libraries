def sendSlack() {
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
            "text": "*:star: Build Image:*\n<https://hub.docker.com/r/${DOCKER_REGISTRY_CRED_USR}/${APP_NAME}/tags|Docker hub>"
            ]
        ]
        ]
    ]
    slackSend(blocks: blocks)
}