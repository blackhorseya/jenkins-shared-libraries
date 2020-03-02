def printInfo() {
    sh label: "print dotnet info", script: "dotnet --info"
}

def build(Map configs) {
    def nugetSource = ""
    def useCache = ""

    if (configs.nuget != null) {
        nugetSource = "--source ${configs.nuget}"
    }

    if (!configs.useCache) {
        useCache = "--no-cache"
    }

    sh label: "dotnet core build", script: """
    dotnet build -v normal --nologo ${nugetSource} ${useCache} -c Release -o ./publish
    """
}

def test(Map configs) {
    def coverage = ""
    def report = ""
    def output = ""

    if (configs.genCoverage) {
        coverage = "/p:CollectCoverage=true /p:CoverletOutputFormat=opencover /p:CoverletOutput=${PWD}/coverage.xml"
    }

    if (configs.genReport) {
        report = "--logger trx -r ./report.trx"
    }

    if (configs.output) {
        output = "-o ${configs.output} --no-build --no-restore"
    }
    
    sh label: "dotnet core test", script: """
    dotnet test ${coverage} ${report} ${output}
    """
}

def scanner(Map configs) {
    sh label: "sonar-scanner", script: """
    sonar-scanner \
    -Dsonar.projectKey=${configs.projectKey} \
    -Dsonar.projectVersion=${configs.version} \
    -Dsonar.sources=. \
    -Dsonar.host.url=${configs.hostUrl} \
    -Dsonar.login=${configs.token}
    """
}
