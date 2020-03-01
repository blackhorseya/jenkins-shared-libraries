# jenkins-shared-libraries

The project is jenkins shared libraries

## Features

- dotnet
  - commit build
  - daily build
- react
  - commit build
  - daily build

## How to use

### dotnetCommit

At your project's commit build jenkinsfile, `commit.Jenkinsfile`

```groovy
#!/usr/bin/env groovy

// required, import library
@Library("jenkins-shared-libraries") _

// call dotnet commit method
dotnetCommit {[]}
```

### dotnetDaily

At your project's daily build jenkinsfile, `daily.Jenkinsfile`

```groovy
#!/usr/bin/env groovy

// required, import library
@Library("jenkins-shared-libraries") _

// call dotnet daily method
// input parameters, required
// AppName, Version, KubeNamespace
dotnetDaily {
  [
    AppName = "learn-dotnet",
    Version = "1.0.0",
    KubeNamespace = "default"
  ]
}
```

### reactCommit

At your project's commit build jenkinsfile, `commit.Jenkinsfile`

```groovy
#!/usr/bin/env groovy

// required, import library
@Library("jenkins-shared-libraries") _

// call dotnet commit method
reactCommit {[]}
```

### reactDaily

At your project's daily build jenkinsfile, `daily.Jenkinsfile`

```groovy
#!/usr/bin/env groovy

// required, import library
@Library("jenkins-shared-libraries") _

// call dotnet daily method
// input parameters, required
// AppName, Version, KubeNamespace
// but AppName and Version not use
reactDaily {
  [
    AppName = "learn-react",
    Version = "1.0.0",
    KubeNamespace = "default"
  ]
}
```
