# Sandboxed

This project is currently sandboxed due to limitations in writing Java Step functions.

For now we recommend the [fabric8-pipelines-plugin](https://github.com/fabric8-jenkins/fabric8-pipelines-plugin) instead.

## Overview 
This library re-implements the [fabric8-pipeline-library](https://github.com/fabric8io/fabric8-pipeline-library) in Java POJOs using the [declarative-step-functions-api](https://github.com/fabric8-jenkins/declarative-step-functions-api).

To consume this library inside scripted or declarative pipelines then use the [fabric8-declarative-pipeline-step-functions-plugin](https://github.com/fabric8-jenkins/fabric8-declarative-pipeline-step-functions-plugin) which exports the steps so that they can be used in the Jenkins Pipeline Syntax UI or the Blue Ocean Pipeline Editor.


## How it looks


![Pipeline Syntax](https://issues.jenkins-ci.org/secure/attachment/40422/pipline-syntax-snippet-generator.png)