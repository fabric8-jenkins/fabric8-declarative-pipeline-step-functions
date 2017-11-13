#!/usr/bin/groovy

def call(Map parameters = [:]) {

  def answer = ""

  clientsNode {
      container(name: 'clients') {
        sh "bash -c 'env | gofabric8 version -b'"

        answer = sh(script: 'gofabric8 e2e-env -b', returnStdout: true).toString()
      }
  }

  echo "Found environment variables\n ${answer}"
  return answer
}

/*
import io.fabric8.Fabric8Commands
import io.fabric8.Utils

  // TODO could we detect this from an env var maybe???

  def flow = new Fabric8Commands()
  def utils = new Utils()

  def ns = parameters.get('namespace', "fabric8")

  echo "Looking for fabric8 installation in namespace ${ns}"

  def url = flow.getServiceURL("fabric8", ns, "http", true)
  if (!url) {
    throw new Exception("Could not find service fabric8 in namespace ${ns}")
  }
  def platform = utils.supportsOpenShiftS2I() ? "fabric8-openshift" : "fabric8-kubernetes"

  echo "found fabric8 at ${url} with platform ${platform}"

  return """

  export TARGET_URL="${url}"
  export TEST_PLATFORM="${platform}"
  
"""
}
*/
