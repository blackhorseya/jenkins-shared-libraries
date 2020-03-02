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
        coverage = "/p:CollectCoverage=true /p:CoverletOutputFormat=opencover /p:CoverletOutput=${WORKSPACE}/reports/coverage.xml"
    }

    if (configs.genReport) {
        report = "--logger trx -r reports"
    }

    if (configs.output) {
        output = "-o ${configs.output} --no-build --no-restore"
    }
    
    sh label: "dotnet core test", script: """
    dotnet test ${coverage} ${report} ${output}
    """
}

def scannerBegin(Map configs) {
    sh label: "sonar-scanner begin", script: """
    dotnet sonarscanner begin /k:\"${configs.projectKey}\" \
    /v:${configs.version} \
    /d:sonar.host.url=${configs.hostUrl} \
    /d:sonar.login=${configs.token} \
    /d:sonar.exclusions=**/*.js,**/*.ts,**/*.css,bin/**/*,obj/**/*,wwwroot/**/*,ClientApp/**/* \
    /d:sonar.cs.opencover.reportsPaths=reports/coverage.xml \
    /d:sonar.coverage.exclusions=**/Entities/**/*,test/**/* \
    /d:sonar.cs.vstest.reportsPaths=reports/report.trx
    """
}

def scannerEnd(Map configs) {
    sh label: "sonarscanner end", script: """
    dotnet sonarscanner end /d:sonar.login=${configs.token}
    """
}
