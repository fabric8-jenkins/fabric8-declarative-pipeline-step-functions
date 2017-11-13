#!/usr/bin/groovy

def call(Map parameters = [:]) {

  def beforeTest = parameters.get('beforeTest', "")
  def afterTest = parameters.get('afterTest', "")

  fabric8EETestNode(parameters) {
    def screenshotsStash = "screenshots"
    try {
      container(name: 'test') {
        try {
          sh """
                ${beforeTest}

                /test/ee_tests/entrypoint.sh

                ${afterTest}
             """
        } catch (e) {
          echo "FAILED: ${e}"
        } finally {

          echo "stashing logs and screenshots"
          sh "mkdir -p screenshots"
          sh "cp /test/ee_tests/*.log ."
          sh "cp -r /test/ee_tests/target/screenshots/* screenshots"

          stash name: screenshotsStash, includes: "screenshots/*"
          stash name: "log", includes: "*.log"
        }
      }
    } catch (e) {
      echo "FAILED: ${e}"
    } finally {

      echo "unstashing"
      unstash screenshotsStash
      unstash "log"

      echo "now lets archive: ${screenshotsStash}"
      try {
        archiveArtifacts artifacts: 'screenshots/*'
      } catch (e) {
        echo "could not find: screenshots* ${e}"
      }
      try {
        archiveArtifacts artifacts: '*.log'
      } catch (e) {
        echo "could not find: logs ${e}"
      }
    }
  }
}
