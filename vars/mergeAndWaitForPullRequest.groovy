#!/usr/bin/groovy
import io.fabric8.pipeline.steps.WaitUntilPullRequestMerged

def call(body) {
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def flow = new io.fabric8.Fabric8Commands()
  echo "adding merge comment to pr ${config.pullRequestId} for project ${config.project}"
  flow.addMergeCommentToPullRequest(config.pullRequestId, config.project)
  WaitUntilPullRequestMerged{
    name = config.project
    prId = config.pullRequestId
  }

}
