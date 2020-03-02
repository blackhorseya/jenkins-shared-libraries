import java.util.regex.Pattern

def getChanges() {
    def ret = ""
    def changeLogSets = currentBuild.changeSets
    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {
            def entry = entries[j]
            ret += "${i+1}.${j+1} ${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}\n${entry.msg}\n"
        }
    }

    return ret
}

def getAuthorEmail() {
    return sh(script: 'git show -s --pretty=%an', returnStdout: true)
}

def gitAddTag(String credentialId, String env, String version) {
    if (credentialId == null) {
        error("missing credentialId from parameters")
    }

    if (env == null) {
        error("missing env from parameters")
    }

    if (version == null) {
        error("missing version from parameters")
    }

    def postfix = ""
    switch(env) {
        case "dev":
            postfix = "alpha"
            break
        case "stg":
            postfix = "beta"
            break
    }

    sshagent(["${credentialId}"]) {
        sh label: "git tag new v${VERSION}-alpha", script: """
        git tag --delete v${VERSION}-alpha | exit 0 && git push --delete origin v${VERSION}-alpha | exit 0
        git tag v${VERSION}-alpha && git push --tags
        """
    }
}

def getTargetEnv(String branch) {
    if (branch == null) {
        error("missing [branch] from parameters")
    }

    switch(branch) {
        case ~/.*\/master/:
            return "prod"
        case ~/.*\/release\/.*/:
            return "stg"
        case ~/.*\/develop/:
            return "dev"
        case ~/.*\/feature\/.*/:
            return "dev"
    }
}
