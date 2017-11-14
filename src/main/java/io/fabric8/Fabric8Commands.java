/**
 * Copyright (C) Original Authors 2017
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8;

import com.cloudbees.groovy.cps.NonCPS;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.pipeline.steps.helpers.DomUtils;
import io.fabric8.pipeline.steps.helpers.FailedBuildException;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Strings;
import io.fabric8.utils.XmlUtils;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.w3c.dom.Document;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.fabric8.Utils.createKubernetesClient;
import static io.fabric8.Utils.defaultNamespace;
import static java.lang.Integer.parseInt;

public class Fabric8Commands extends FunctionSupport {

    public static boolean hasGitHubEnvVars() {
        String user = System.getenv(EnvironmentVariableNames.GITHUB_USER);
        String password = System.getenv(EnvironmentVariableNames.GITHUB_PASSWORD);
        String githubToken = System.getenv(EnvironmentVariableNames.GITHUB_TOKEN);

        return Strings.notEmpty(githubToken) || (Strings.notEmpty(user) && Strings.notEmpty(password));
    }

    public static GitHub createGitHub(String githubToken) {
        String user = System.getenv(EnvironmentVariableNames.GITHUB_USER);
        String password = System.getenv(EnvironmentVariableNames.GITHUB_PASSWORD);
        final GitHubBuilder ghb = new GitHubBuilder();
        if (Strings.isNullOrBlank(githubToken)) {
            githubToken = System.getenv(EnvironmentVariableNames.GITHUB_TOKEN);
        }
        if (Strings.notEmpty(githubToken)) {
            if (Strings.notEmpty(user)) {
                ghb.withOAuthToken(user, githubToken);
            } else {
                ghb.withOAuthToken(githubToken);
            }
        }
        if (Strings.isNotBlank(user) && Strings.isNotBlank(password)) {
            ghb.withPassword(user, password);
        }
        try {
            return ghb.build();
        } catch (Exception e) {
            throw new FailedBuildException("Could not connect to github", e);
        }
    }


    public String swizzleImageName(Object text, final Object match, final Object replace) {
        return Pattern.compile("image: " + match + ":(.*)").matcher((CharSequence) text).replaceFirst("image: " + replace);
    }

    public String getReleaseVersionFromMavenMetadata(String url) {
        try {
            String result = shOutput("curl -L " + url + " | grep \'<latest\' | cut -f2 -d\'>\'|cut -f1 -d\'<\'");
            return result.trim();
        } catch (Exception e) {
            error("Failed to find release version from maven central " + url + " due to: " + e);
            return null;
        }
    }

    public String updatePackageJSONVersion(final String f, final Object p, final Object v) {
        try {
            return shOutput("sed -i -r \'s/\"" + p + "\": \"[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2)?(.[0-9][0-9]{0,2)?(-development)?\"/\"" + p + "\": \"" + v + "\"/g\' " + f).trim();
        } catch (Exception e) {
            error("Failed to get package json version", e);
            return null;
        }
    }

    public Object updateDockerfileEnvVar(final String f, final Object p, final Object v) {
        try {
            return shOutput("sed -i -r \'s/ENV " + p + ".*/ENV " + p + " " + v + "/g\' " + f);
        } catch (Exception e) {
            error("Failed to get dockerfile env var", e);
            return null;
        }
    }

    public String getProjectVersion() {
        Document doc;
        try {
            doc = XmlUtils.parseDoc(createFile("pom.xml"));
        } catch (Exception e) {
            error("Failed to parse pom.xml", e);
            return null;
        }
        return DomUtils.firstElementText(getLogger(), doc, "version", "pom.xml");
    }

    public String getReleaseVersion(final String artifact) {
        return DomUtils.parseXmlForURLAndReturnFirstElementText(getLogger(), "https://oss.sonatype.org/content/repositories/releases/" + artifact + "/maven-metadata.xml", "latest");
    }

    public String getMavenCentralVersion(final String artifact) {
        return DomUtils.parseXmlForURLAndReturnFirstElementText(getLogger(), "http://central.maven.org/maven2/" + artifact + "/maven-metadata.xml", "latest");
    }

    public String getVersion(String repo, String artifact) {
        repo = Strings.stripSuffix(repo, "/");
        artifact = Strings.stripSuffix(artifact, "/");

        String url = repo + "/" + artifact + "/maven-metadata.xml";
        return DomUtils.parseXmlForURLAndReturnFirstElementText(getLogger(), url, "latest");
    }

    public boolean isArtifactAvailableInRepo(String repo, String groupId, String artifactId, String version, String ext) {
        repo = Strings.stripSuffix(repo, "/");
        groupId = Strings.stripSuffix(groupId, "/").replace('.', '/');
        artifactId = Strings.stripSuffix(artifactId, "/");
        version = Strings.stripSuffix(version, "/");

        URL url = null;
        HttpURLConnection connection = null;
        try {
            String urlText = repo + "/" + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "." + ext;
            url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setDoInput(true);

            connection.connect();
            try (InputStream inputStream = connection.getInputStream();) {
                try (Reader ignored = new InputStreamReader(inputStream, "UTF-8")) {
                    echo("File is available at: " + url.toString());
                    return true;
                }
            }
        } catch (FileNotFoundException e1) {
            echo("File not yet available: " + url.toString());
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public boolean isFileAvailableInRepo(String repo, String path, String version, final String artifact) {
        repo = Strings.stripSuffix(repo, "/");
        path = Strings.stripSuffix(path, "/");
        version = Strings.stripSuffix(version, "/");


        URL url = null;
        HttpURLConnection connection = null;
        try {
            url = new URL(repo + "/" + path + "/" + version + "/" + artifact);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setDoInput(true);

            connection.connect();
            try (InputStream inputStream = connection.getInputStream();) {
                try (Reader ignored = new InputStreamReader(inputStream, "UTF-8")) {
                    echo("File is available at: " + url.toString());
                    return true;
                }
            }
        } catch (FileNotFoundException e1) {
            echo("File not yet available: " + url.toString());
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public List<String> getRepoIds() {
        // we could have multiple staging repos created, we need to write the names of all the generated files to a well known
        // filename so we can use the workflow readFile (wildcards wont works and new File wont with slaves as groovy is executed on the master jenkins
        // We write the names of the files that contain the repo ids used for staging.  Each staging repo id is read from each file and returned as a list

        List<String> answer = new ArrayList<>();
        try {
            shOutput("find target/nexus-staging/staging/  -maxdepth 1 -name \"*.properties\" > target/nexus-staging/staging/repos.txt").trim();
            String repos = readFile("target/nexus-staging/staging/repos.txt");
            String[] lines = repos.split("\n");
            for (String line : lines) {
                String text = readFile(line);
                Matcher matcher = Pattern.compile("stagingRepository.id=(.+)").matcher(text);
                if (matcher.matches()) {
                    answer.add(matcher.group(1));
                }
            }
            return answer;
        } catch (Exception e) {
            error("Failed to find repoIds", e);
            return Collections.EMPTY_LIST;
        }
    }

    public String getDockerHubImageTags(final String image) {
        try {
            return IOHelpers.readFully(new URL("https://registry.hub.docker.com/v1/repositories/" + image + "/tags"));
        } catch (Exception err) {
            return "NO_IMAGE_FOUND";
        }
    }

    public String searchAndReplaceMavenVersionPropertyNoCommit(final String property, final String newVersion) throws IOException {
        // example matches <fabric8.version>2.3</fabric8.version> <fabric8.version>2.3.12</fabric8.version> <fabric8.version>2.3.12.5</fabric8.version>
        return shOutput("find -type f -name \'pom.xml\' | xargs sed -i -r \'s/" + property + "[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2)?(.[0-9][0-9]{0,2)?</" + property + newVersion + "</g\'");
    }

    public String searchAndReplaceMavenVersionProperty(final String property, final String newVersion) throws IOException {
        // example matches <fabric8.version>2.3</fabric8.version> <fabric8.version>2.3.12</fabric8.version> <fabric8.version>2.3.12.5</fabric8.version>
        sh("find -type f -name \'pom.xml\' | xargs sed -i -r \'s/" + property + "[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2)?(.[0-9][0-9]{0,2)?</" + property + newVersion + "</g\'");
        return shOutput("git commit -a -m \'Bump " + property + " version\'").trim();
    }

    public String searchAndReplaceMavenSnapshotProfileVersionProperty(final String property, final String newVersion) throws IOException {
        // example matches <fabric8.version>2.3-SNAPSHOT</fabric8.version> <fabric8.version>2.3.12-SNAPSHOT</fabric8.version> <fabric8.version>2.3.12.5-SNAPSHOT</fabric8.version>
        sh("find -type f -name \'pom.xml\' | xargs sed -i -r \'s/" + property + "[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2)?(.[0-9][0-9]{0,2)?-SNAPSHOT</" + property + newVersion + "-SNAPSHOT</g\'");
        return shOutput("git commit -a -m \'Bump " + property + " development profile SNAPSHOT version\'");
    }

    public String setupWorkspaceForRelease(String project, Boolean useGitTagForNextVersion, String mvnExtraArgs, String currentVersion) throws IOException {
        sh("git config user.email fabric8-admin@googlegroups.com");
        sh("git config user.name fabric8-release");

        sh("chmod 600 /root/.ssh-git/ssh-key");
        sh("chmod 600 /root/.ssh-git/ssh-key.pub");
        sh("chmod 700 /root/.ssh-git");
        sh("chmod 600 /home/jenkins/.gnupg/pubring.gpg");
        sh("chmod 600 /home/jenkins/.gnupg/secring.gpg");
        sh("chmod 600 /home/jenkins/.gnupg/trustdb.gpg");
        sh("chmod 700 /home/jenkins/.gnupg");

        sh("git tag -d $(git tag)");
        sh("git fetch --tags");

        if (useGitTagForNextVersion) {
            final String newVersion = getNewVersionFromTag(currentVersion);
            echo("New release version " + newVersion);
            sh("mvn -B -U versions:set -DnewVersion=" + newVersion + " " + mvnExtraArgs);
            sh("git commit -a -m \'release " + newVersion + "\'");
            pushTag(newVersion);
        } else {
            sh("mvn -B build-helper:parse-version versions:set -DnewVersion=${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.nextIncrementalVersion} " + mvnExtraArgs);
        }

        final String releaseVersion = getProjectVersion();

        // delete any previous branches of this release
        try {
            return shOutput("git checkout -b release-v" + releaseVersion);
        } catch (Exception err) {
            sh("git branch -D release-v" + releaseVersion);
            return shOutput("git checkout -b release-v" + releaseVersion);
        }
    }

    public Object setupWorkspaceForRelease(String project, Boolean useGitTagForNextVersion, String mvnExtraArgs) throws IOException {
        return setupWorkspaceForRelease(project, useGitTagForNextVersion, mvnExtraArgs, "");
    }

    public Object setupWorkspaceForRelease(String project, Boolean useGitTagForNextVersion) throws IOException {
        return setupWorkspaceForRelease(project, useGitTagForNextVersion, "", "");
    }

    public String getNewVersionFromTag(String pomVersion) throws IOException {
        //return shOutput("semver-release-version --folder " + getCurrentDir().getPath()).trim();
        return shOutput("semver-release-number");
/*        final String version = "1.0.0";

        // Set known prerelease prefixes, needed for the proper sort order
        // in the next command
        execBashAndGetOutput("git config versionsort.prereleaseSuffix -RC");
        execBashAndGetOutput("git config versionsort.prereleaseSuffix -M");

        // if the repo has no tags this command will fail
        execBashAndGetOutput("git tag --sort version:refname | tail -1 > version.tmp");

        String tag = readFile("version.tmp").trim();
        if (Strings.isNullOrBlank(tag)) {
            echo("no existing tag found using version " + version);
            return version;
        }

        echo("Testing to see if version " + tag + " is semver compatible");

        Pattern semVerRegex = Pattern.compile("(?i)\\bv?(?<major>0|[1-9]\\d*)(?:\\.(?<minor>0|[1-9]\\d*)(?:\\.(?<patch>0|[1-9]\\d*))?)?(?:-(?<prerelease>[\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?(?:\\+(?<build>[\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?\\b");
        Pattern pomRegex = Pattern.compile("(?i)\\bv?(?<major>0|[1-9]\\d*)(?:\\.(?<minor>0|[1-9]\\d*)(?:\\.(?<patch>0|[1-9]\\d*))?)?(?:-(?<prerelease>[\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?(?:\\+(?<build>[\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?\\b");

        Matcher semver = semVerRegex.matcher(tag);
        if (semver.matches()) {
            echo("Version " + tag + " is semver compatible");

            int majorVersion = parseInt(semver.group("major"));
            int minorVersion = parseInt(semver.group("minor"));
            int patchVersion = parseInt(semver.group("patch")) + 1;

            echo("Testing to see if current POM version " + pomVersion + " is semver compatible");

            Matcher pomSemver = pomRegex.matcher(pomVersion.trim());
            if (pomSemver.matches()) {
                echo("Current POM version " + pomVersion + " is semver compatible");

                int pomMajorVersion = parseInt(pomSemver.group("major"));
                int pomMinorVersion = parseInt(pomSemver.group("minor"));
                int pomPatchVersion = parseInt(pomSemver.group("patch")) + 1;


                if (pomMajorVersion > majorVersion ||
                        (pomMajorVersion == majorVersion &&
                                (pomMinorVersion > minorVersion) || (pomMinorVersion == minorVersion && pomPatchVersion > patchVersion)
                        )
                        ) {
                    majorVersion = pomMajorVersion;
                    minorVersion = pomMinorVersion;
                    patchVersion = pomPatchVersion;
                }
            }


            final String newVersion = "" + majorVersion + "." + minorVersion + "." + patchVersion;
            echo("New version is " + newVersion);
            return newVersion;
        } else {
            echo("Version is not semver compatible");

            // strip the v prefix from the tag so we can use in a maven version number
            String previousReleaseVersion = Strings.stripPrefix(tag, "v");
            echo("Previous version found " + previousReleaseVersion);

            // if there's an int as the version then turn it into a major.minor.micro version
            if (NumberHelpers.isInteger(previousReleaseVersion)) {
                return previousReleaseVersion + ".0.1";
            } else {
                int idx = previousReleaseVersion.lastIndexOf('.');
                if (idx <= 0) {
                    error("found invalid latest tag [" + previousReleaseVersion + "] set to major.minor.micro to calculate next release version");
                    return null;
                }

                String text = previousReleaseVersion.substring(idx + 1);
                int nextVersionNumber = parseInt(text) + 1;
                return previousReleaseVersion.substring(0, idx + 1) + nextVersionNumber;
            }
        }*/
    }

    public String getNewVersionFromTag() throws IOException {
        return getNewVersionFromTag(null);
    }

    public List<String> stageSonartypeRepo() {
        try {
            sh("mvn clean -B");
            sh("mvn -V -B -e -U install org.sonatype.plugins:nexus-staging-maven-plugin:1.6.7:deploy -P release -P openshift -DnexusUrl=https://oss.sonatype.org -DserverId=oss-sonatype-staging -Ddocker.push.registry=" + System.getenv("FABRIC8_DOCKER_REGISTRY_SERVICE_HOST") + ":" + System.getenv("FABRIC8_DOCKER_REGISTRY_SERVICE_PORT"));

            // lets not archive artifacts until we if we just use nexus or a content repo
            //step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])

        } catch (Exception err) {
            Map<String, Object> map = new LinkedHashMap<>(3);
            map.put("room", "release");
            map.put("message", "Release failed when building and deploying to Nexus " + err);
            map.put("failOnError", false);
            callStep("hubotSend", map);

            throw new FailedBuildException("ERROR Release failed when building and deploying to Nexus " + err, err);
        }
        // the sonartype staging repo id gets written to a file in the workspace
        return getRepoIds();
    }

    public Object releaseSonartypeRepo(final String repoId) {
        try {
            // release the sonartype staging repo
            return shOutput("mvn -B org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-release -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=" + repoId + " -Ddescription=\"Next release is ready\" -DstagingProgressTimeoutMinutes=60");

        } catch (Exception err) {
            try {
                sh("mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=" + repoId + " -Ddescription=\"Error during release: " + err + "\" -DstagingProgressTimeoutMinutes=60");
            } catch (Exception e) {
                error("Failed to drop the staging repository " + e, e);
            }
            throw new FailedBuildException("ERROR releasing sonartype repo " + repoId + ": " + err, err);
        }
    }

    public Object dropStagingRepo(final String repoId) {
        echo("Not a release so dropping staging repo " + repoId);
        try {
            return shOutput("mvn org.sonatype.plugins:nexus-staging-maven-plugin:1.6.5:rc-drop -DserverId=oss-sonatype-staging -DnexusUrl=https://oss.sonatype.org -DstagingRepositoryId=" + repoId + " -Ddescription=\"Dry run\" -DstagingProgressTimeoutMinutes=60");
        } catch (Exception e) {
            error("Failed to drop staging repository " + repoId + ". " + e, e);
        }
        return null;
    }

    public Object helm() {
        final Object pluginVersion = getReleaseVersion("io/fabric8/fabric8-maven-plugin");
        try {
            sh("mvn -B io.fabric8:fabric8-maven-plugin:" + pluginVersion + ":helm");
            sh("mvn -B io.fabric8:fabric8-maven-plugin:" + pluginVersion + ":helm-push");
            return null;
        } catch (Exception err) {
            throw new FailedBuildException("ERROR with helm push " + err, err);
        }
    }

    public Object pushTag(final String releaseVersion) throws IOException {
        sh("git tag -fa v" + releaseVersion + " -m \'Release version " + releaseVersion + "\'");
        sh("git push origin v" + releaseVersion);
        return null;
    }

    public String updateGithub() throws IOException {
        final String releaseVersion = getProjectVersion();
        sh("git push origin release-v" + releaseVersion);
        return null;
    }

    public Object updateNextDevelopmentVersion(String releaseVersion, String mvnExtraArgs) throws IOException {
        // update poms back to snapshot again
        sh("mvn -B build-helper:parse-version versions:set -DnewVersion=${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.nextIncrementalVersion}-SNAPSHOT " + mvnExtraArgs);
        final Object snapshotVersion = getProjectVersion();
        sh("git commit -a -m \'[CD] prepare for next development iteration " + snapshotVersion + "\'");
        sh("git push origin release-v" + releaseVersion);
        return null;
    }

    public Object updateNextDevelopmentVersion(String releaseVersion) throws IOException {
        return updateNextDevelopmentVersion(releaseVersion, "");
    }

    public Boolean hasChangedSinceLastRelease() throws IOException {
        sh("git log --name-status HEAD^..HEAD -1 --grep=\"prepare for next development iteration\" --author='fusesource-ci' >> gitlog.tmp");
        File file = createFile("gitlog.tmp");
        String myfile = IOHelpers.readFully(file).trim();
        file.delete();
        //sh "rm gitlog.tmp"
        // if the file size is 0 it means the CI user was not the last commit so project has changed
        return myfile.length() == 0;
    }

    public Object getOldVersion() {
        return null;
/*
TODO
        Matcher matcher=Pattern.compile("<h1>Documentation for version (.+)</h1>").matcher(invokeMethod("readFile",new Object[]{"website/src/docs/index.page"));
        return DefaultGroovyMethods.asBoolean(matcher)?DefaultGroovyMethods.getAt(DefaultGroovyMethods.getAt(matcher,0),1):null;
*/
    }


    public Object updateDocsAndSite(final String newVersion) throws IOException {
// get previous version
        final Object oldVersion = getOldVersion();

        if (oldVersion == null) {
            echo("No previous version found");
            return null;

        }


        // use perl so that we we can easily turn off regex in the SED query as using dots in version numbers returns unwanted results otherwise
        sh("find . -name \'*.md\' ! -name Changes.md ! -path \'*/docs/jube/**.*\' | xargs perl -p -i -e \'s/\\Q" + oldVersion + "/" + newVersion + "/g\'");
        sh("find . -path \'*/website/src/**.*\' | xargs perl -p -i -e \'s/\\Q" + oldVersion + "/" + newVersion + "/g\'");

        sh("git commit -a -m \'[CD] Update docs following " + newVersion + " release\'");
        return null;

    }

    public Object runSystemTests() throws IOException {
        sh("cd systests && mvn clean && mvn integration-test verify");
        return null;
    }

    @NonCPS
    public boolean isOpenShift() {
        return new DefaultOpenShiftClient().isAdaptable(OpenShiftClient.class);
    }

    public List<GHIssueComment> getIssueComments(String project, String id, String githubToken) {
        int issueNumber = parseInt(id);
        if (issueNumber <= 0) {
            throw new FailedBuildException("GitHub issue " + id + " is not a valid issue number");
        }
        return getIssueComments(project, issueNumber, githubToken);
    }

    public List<GHIssueComment> getIssueComments(String project, int issueNumber, String githubToken) {
        GitHub gitHub = createGitHub(githubToken);
        GHRepository repository = null;
        try {
            repository = gitHub.getRepository(project);
        } catch (Exception e) {
            throw new FailedBuildException("Could not find repository " + project, e);
        }
        GHIssue issue = null;
        try {
            issue = repository.getIssue(issueNumber);
        } catch (Exception e) {
            throw new FailedBuildException("Could not find issue #" + issueNumber + " on repository " + project, e);
        }
        try {
            return issue.getComments();
        } catch (Exception e) {
            throw new FailedBuildException("Could not load comments for issue #" + issueNumber + " on repository " + project, e);
        }
    }

    public List<GHIssueComment> getIssueComments(String project, String id) {
        return getIssueComments(project, id, null);
    }

    public List<GHIssueComment> getIssueComments(String project, int id) {
        return getIssueComments(project, id, null);
    }


    @NonCPS
    public Boolean isSingleNode() {
        KubernetesClient kubernetes = createKubernetesClient();
        try {
            if (kubernetes.nodes().list().getItems().size() == 1) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            error("Failed to query nodes - probably due to security restrictions", e);
            return false;
        }

    }

    @NonCPS
    public Boolean hasService(String name) {
        KubernetesClient kubernetes = createKubernetesClient();
        try {
            Service service = kubernetes.services().withName(name).get();
            if (service != null) {
                return service.getMetadata() != null;
            }
        } catch (Exception e) {
            error("Failed to find service " + name, e);
        }
        return false;
    }

    @NonCPS
    public String getServiceURL(String serviceName, String namespace, String protocol, boolean external) {
        KubernetesClient kubernetes = createKubernetesClient();
        if (namespace == null) {
            namespace = defaultNamespace(kubernetes);
        }
        return KubernetesHelper.getServiceURL(kubernetes, serviceName, namespace, protocol, external);
    }

    @NonCPS
    public String getServiceURL(String serviceName, String namespace, String protocol) {
        return getServiceURL(serviceName, namespace, protocol, true);
    }

    @NonCPS
    public String getServiceURL(String serviceName, String namespace) {
        return getServiceURL(serviceName, namespace, "http", true);
    }

    @NonCPS
    public String getServiceURL(String serviceName) {
        return getServiceURL(serviceName, null, "http", true);
    }


}

        /*
public Object createPullRequest(final String message,final String project,final String branch){
final Object githubToken=getGitHubToken();
final URL apiUrl=new URL("https://api.github.com/repos/"+project+"/pulls");
        echo("creating PR for "+apiUrl));
        try{
        HttpURLConnection connection=(HttpURLConnection)apiUrl.openConnection();
        if(DefaultGroovyMethods.invokeMethod(githubToken,"length",new Object[0])>0){
        connection.setRequestProperty("Authorization","Bearer "+githubToken));
        }

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.connect();

final String body="\n    {\n      " title": "" + message + "",\n      " head": "" + branch + "
        ",\n      " base": " master"\n    }\n    ";
        echo("sending body: "+body)+"\n");

        OutputStreamWriter writer=new OutputStreamWriter(connection.getOutputStream());
        writer.write(body);
        writer.flush();

// execute the POST request
final Object rs=new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(),"UTF-8"));

        connection.disconnect();

        echo("Received PR id:  "+rs.number));
        return rs.number+"";

        }catch(Exception err){
        return invokeMethod("error",new Object[]{"ERROR  "+err));
        }

        }

public void closePR(final Object project,final Object id,final Object newVersion,final Object newPRID){
final Object githubToken=getGitHubToken();
final URL apiUrl=new URL("https://api.github.com/repos/"+project)+"/pulls/"+id));
        echo("deleting PR for "+apiUrl));

final HttpURLConnection connection=(HttpURLConnection)apiUrl.openConnection();
        if(DefaultGroovyMethods.invokeMethod(githubToken,"length",new Object[0])>0){
        connection.setRequestProperty("Authorization","Bearer "+githubToken));
        }

        connection.setRequestProperty("X-HTTP-Method-Override","PATCH");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.connect();

final String body="\n    {\n      " state": " closed",\n      " body": " Superseded by new version
        " + java.lang.newVersion) + " #" + java.lang.newPRID) + ""\n    }\n    ";
        echo("sending body: "+body)+"\n");

        OutputStreamWriter writer=new OutputStreamWriter(connection.getOutputStream());
        writer.write(body);
        writer.flush();

// execute the PATCH     request
final Object rs=new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(),"UTF-8"));

        int code=connection.getResponseCode();

        if(code!=200){
        invokeMethod("error",new Object[]{project)+" PR "+id)+" not merged.  "+connection.getResponseMessage());

        }else{
        echo(project)+" PR "+id)+" "+rs.message));
        }

        connection.disconnect();
        }

public Object waitUntilSuccessStatus(final Object project,final Object ref){

final Object githubToken=getGitHubToken();

final URL apiUrl=new URL("https://api.github.com/repos/"+project)+"/commits/"+ref)+"/status");
        return invokeMethod("waitUntil",new Object[]{new Closure<Boolean>(this,this){
public Boolean doCall(Object it){
        HttpURLConnection connection=(HttpURLConnection)apiUrl.openConnection();
        if(githubToken!=null&&DefaultGroovyMethods.invokeMethod(githubToken,"length",new Object[0])>0){
        connection.setRequestProperty("Authorization","Bearer "+githubToken));
        }


        connection.setRequestMethod("GET");
        connection.setDoOutput(true);
        connection.connect();

        Object rs;
        Object code;

        try{
        rs=new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(),"UTF-8"));

        code=connection.getResponseCode();
        }catch(Exception err){
        echo("CI checks have not passed yet so waiting before merging");
        }finally{
        connection.disconnect();
        }


        if(rs==null){
        echo("Error getting commit status, are CI builds enabled for this PR?");
        return false;
        }

        if(rs!=null&&rs.state=="success"){
        return true;
        }else{
        echo("Commit status is "+rs.state)+".  Waiting to merge");
        return false;
        }

        }

public Boolean doCall(){
        return doCall(null);
        }

        });
        }


public Object getGithubBranch(final Object project,final Object id,final Object githubToken){

        URL apiUrl=new URL("https://api.github.com/repos/"+project)+"/pulls/"+id));
        HttpURLConnection connection=(HttpURLConnection)apiUrl.openConnection();
        if(githubToken!=null&&githubToken.invokeMethod("length",new Object[0])>0){
        connection.setRequestProperty("Authorization","Bearer "+githubToken));
        }


        connection.setRequestMethod("GET");
        connection.setDoOutput(true);
        connection.connect();
        try{
        Object rs=new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(),"UTF-8"));
        setProperty("branch",rs.head.ref);
        echo(this.getBinding().getProperty("branch")));
        return this.getBinding().getProperty("branch");
        }catch(Exception err){
        return echo("Error while fetching the github branch");
        }finally{
        if(DefaultGroovyMethods.asBoolean(connection)){
        connection.disconnect();
        }

        }

        }

        /*
public Object mergePR(final Object project,final Object id){
final Object githubToken=getGitHubToken();
        Object branch=getGithubBranch(project,id,githubToken);
        waitUntilSuccessStatus(project,branch);

        URL apiUrl=new URL("https://api.github.com/repos/"+project)+"/pulls/"+id)+"/merge");

        HttpURLConnection connection=(HttpURLConnection)apiUrl.openConnection();
        if(DefaultGroovyMethods.invokeMethod(githubToken,"length",new Object[0])>0){
        connection.setRequestProperty("Authorization","Bearer "+githubToken));
        }

        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.connect();

// execute the request
final Reference<Object> rs;
        try{
        rs.set(new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(),"UTF-8")));

final int code=connection.getResponseCode();

        if(code!=200){
        if(code==405){
        return invokeMethod("error",new Object[]{project)+" PR "+id)+" not merged.  "+rs.get().message));
        }else{
        return invokeMethod("error",new Object[]{project)+" PR "+id)+" not merged.  GitHub API Response code: "+code));
        }

        }else{
        return echo(project)+" PR "+id)+" "+rs.get().message));
        }

        }catch(Exception err){
        // if merge failed try to squash and merge
        connection=null;
        rs.set(null);
        return squashAndMerge(project,id);
        }finally{
        if(DefaultGroovyMethods.asBoolean(connection)){
        connection.disconnect();
        connection=null;
        }

        rs.set(null);
        }

        }

public Object squashAndMerge(final Object project,final Object id){
final Object githubToken=getGitHubToken();
        URL apiUrl=new URL("https://api.github.com/repos/"+project)+"/pulls/"+id)+"/merge");

        HttpURLConnection connection=(HttpURLConnection)apiUrl.openConnection();
        if(DefaultGroovyMethods.invokeMethod(githubToken,"length",new Object[0])>0){
        connection.setRequestProperty("Authorization","Bearer "+githubToken));
        }

        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.connect();
        String body="{\"merge_method\":\"squash\"}";

final Reference<Object> rs;
        try{
        OutputStreamWriter writer=new OutputStreamWriter(connection.getOutputStream());
        writer.write(body);
        writer.flush();

        rs.set(new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(),"UTF-8")));
final int code=connection.getResponseCode();

        if(code!=200){
        if(code==405){
        return invokeMethod("error",new Object[]{project)+" PR "+id)+" not merged.  "+rs.get().message));
        }else{
        return invokeMethod("error",new Object[]{project)+" PR "+id)+" not merged.  GitHub API Response code: "+code));
        }

        }else{
        return echo(project)+" PR "+id)+" "+rs.get().message));
        }

        }finally{
        connection.disconnect();
        connection=null;
        rs.set(null);
        }

        }

public void addCommentToPullRequest(final String comment,final Object pr,final String project){
final Object githubToken=getGitHubToken();
final URL apiUrl=new URL("https://api.github.com/repos/"+project)+"/issues/"+pr)+"/comments");
        echo("adding "+comment)+" to "+apiUrl));
        try{
        HttpURLConnection connection=(HttpURLConnection)apiUrl.openConnection();
        if(DefaultGroovyMethods.invokeMethod(githubToken,"length",new Object[0])>0){
        connection.setRequestProperty("Authorization","Bearer "+githubToken));
        }

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.connect();

        String body="{\"body\":\""+comment)+"\"}";

        OutputStreamWriter writer=new OutputStreamWriter(connection.getOutputStream());
        writer.write(body);
        writer.flush();

        // execute the POST request
        new InputStreamReader(connection.getInputStream());

        connection.disconnect();
        }catch(Exception err){
        invokeMethod("error",new Object[]{"ERROR  "+err));
        }

        }

public void addMergeCommentToPullRequest(final String pr,final String project){
final Object githubToken=getGitHubToken();
final URL apiUrl=new URL("https://api.github.com/repos/"+project+"/issues/"+pr+"/comments");
        echo("merge PR using comment sent to "+apiUrl));
        try{
        HttpURLConnection connection=(HttpURLConnection)apiUrl.openConnection();
        if(DefaultGroovyMethods.invokeMethod(githubToken,"length",new Object[0])>0){
        connection.setRequestProperty("Authorization","Bearer "+githubToken));
        }

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.connect();

        String body="{\"body\":\"[merge]\"}";

        OutputStreamWriter writer=new OutputStreamWriter(connection.getOutputStream());
        writer.write(body);
        writer.flush();

        // execute the POST request
        new InputStreamReader(connection.getInputStream());

        connection.disconnect();
        }catch(Exception err){
        invokeMethod("error",new Object[]{"ERROR  "+err));
        }

        }


public Object getGitHubProject(){
final Reference<Object> url=new Reference<Object>(getScmPushUrl());
        if(!DefaultGroovyMethods.invokeMethod(url.get(),"contains",new Object[]{"github.com").asBoolean()){
        invokeMethod("error",new Object[]{url)+" is not a GitHub URL");
        }


        if(DefaultGroovyMethods.invokeMethod(url.get(),"contains",new Object[]{"https://github.com/").asBoolean()){
        url.set(DefaultGroovyMethods.invokeMethod(url.get(),"replaceAll",new Object[]{"https://github.com/",""));

        }else if(DefaultGroovyMethods.invokeMethod(url.get(),"contains",new Object[]{"git@github.com:").asBoolean()){
        url.set(DefaultGroovyMethods.invokeMethod(url.get(),"replaceAll",new Object[]{"git@github.com:",""));
        }


        if(DefaultGroovyMethods.invokeMethod(url.get(),"contains",new Object[]{".git").asBoolean()){
        url.set(DefaultGroovyMethods.invokeMethod(url.get(),"replaceAll",new Object[]{".git",""));
        }

        return DefaultGroovyMethods.invokeMethod(url.get(),"trim",new Object[0]);
        }

public Boolean isAuthorCollaborator(String githubToken,String project){
final Reference<String> object1=new Reference<String>(githubToken);
final Reference<String> object=new Reference<String>(project);


        if(!object1.get().asBoolean()){

        object1.set(getGitHubToken());

        if(!DefaultGroovyMethods.asBoolean(object1)){
        echo("No GitHub api key found so trying annonynous GitHub api call");
        }

        }

        if(!object.get().asBoolean()){
        object.set(getGitHubProject());
        }


final Object changeAuthor=System.getenv("CHANGE_AUTHOR;
        if(!changeAuthor.asBoolean()){
        invokeMethod("error",new Object[]{"No commit author found.  Is this a pull request pipeline?");
        }

        echo("Checking if user "+changeAuthor)+" is a collaborator on "+object));

        URL apiUrl=new URL("https://api.github.com/repos/"+object)+"/collaborators/"+changeAuthor));

        HttpURLConnection connection=(HttpURLConnection)apiUrl.openConnection();
        if(object1.get()!=null&&DefaultGroovyMethods.invokeMethod(object1.get(),"length",new Object[0])>0){
        connection.setRequestProperty("Authorization","Bearer "+object1));
        }

        connection.setRequestMethod("GET");
        connection.setDoOutput(true);

        try{
        connection.connect();
        new InputStreamReader(connection.getInputStream(),"UTF-8");
        return true;
        }catch(FileNotFoundException e1){
        return false;
        }finally{
        connection.disconnect();
        }


        return invokeMethod("error",new Object[]{"Error checking if user "+changeAuthor)+" is a collaborator on "+object)+".  GitHub API Response code: "+this.getBinding().getProperty("code")));

        }

public Object getUrlAsString(String urlString){

final URL url=new URL(urlString);
        Object scan;
        Object response;
        echo("getting string from URL: "+url));
        try{
        scan=new Scanner(url.openStream(),"UTF-8");
        response=((Scanner)scan).useDelimiter("\\A").next();
        }finally{
        ((Scanner)scan).close();
        }

        return response;
        }

public void drop(final String pr,final String project){
final Object githubToken=getGitHubToken();
final Reference<URL> apiUrl=new Reference<URL>(new URL("https://api.github.com/repos/"+project+"/pulls/"+pr));
        Object branch;
        HttpURLConnection connection;
        OutputStreamWriter writer;
        echo("closing PR "+apiUrl));

        try{
        connection=((HttpURLConnection)(apiUrl.get().openConnection()));
        if(DefaultGroovyMethods.invokeMethod(githubToken,"length",new Object[0])>0){
        connection.setRequestProperty("Authorization","Bearer "+githubToken));
        }

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.connect();

        String body="\n    {\n      " body": " release aborted",\n      " state": " closed"\n    }\n    ";

        writer=new OutputStreamWriter(connection.getOutputStream());
        writer.write(body);
        writer.flush();

        // execute the POST request
        Object rs=new JsonSlurper().parse(new InputStreamReader(connection.getInputStream(),"UTF-8"));

        connection.disconnect();

        setProperty("branchName",rs.head.ref);

        }catch(Exception err){
        invokeMethod("error",new Object[]{"ERROR  "+err));
        }


        try{
        apiUrl.set(new URL("https://api.github.com/repos/"+project+"/git/refs/heads/"+this.getBinding().getProperty("branchName"))));
        connection=((HttpURLConnection)(apiUrl.get().openConnection()));
        if(DefaultGroovyMethods.invokeMethod(githubToken,"length",new Object[0])>0){
        connection.setRequestProperty("Authorization","Bearer "+githubToken));
        }

        connection.setRequestMethod("DELETE");
        connection.setDoOutput(true);
        connection.connect();

        writer=new OutputStreamWriter(connection.getOutputStream());
        DefaultGroovyMethods.invokeMethod(writer,"write",new Object[]{this.getBinding().getProperty("body"));
        writer.flush();

        // execute the POST request
        new InputStreamReader(connection.getInputStream());

        connection.disconnect();

        }catch(Exception err){
        invokeMethod("error",new Object[]{"ERROR  "+err));
        }

        }

public Object deleteRemoteBranch(final String branchName,Object containerName){
        LinkedHashMap<String, Object> map=new LinkedHashMap<String, Object>(1);
        map.put("name",containerName);
        return invokeMethod("container",new Object[]{map,new Closure<Object>(this,this){
public Object doCall(Object it){
        execBashAndGetOutput("chmod 600 /root/.ssh-git/ssh-key");
        execBashAndGetOutput("chmod 600 /root/.ssh-git/ssh-key.pub");
        execBashAndGetOutput("chmod 700 /root/.ssh-git");
        return execBashAndGetOutput("git push origin --delete "+branchName);
        }

public Object doCall(){
        return doCall(null);
        }

        });
        }

public Object getGitHubToken(){
final String tokenPath="/home/jenkins/.apitoken/hub";
        Object githubToken=invokeMethod("readFile",new Object[]{tokenPath);
        if(!githubToken.invokeMethod("trim",new Object[0]).asBoolean()){
        invokeMethod("error",new Object[]{"No GitHub token found in "+tokenPath);
        }

        return githubToken.invokeMethod("trim",new Object[0]);
        }


*/

/*
public Boolean hasOpenShiftYaml(){
        LinkedHashMap<String, String> map=new LinkedHashMap<String, String>(1);
*/
//map.put("glob","**/openshift.yml");
/*

final Object openshiftYaml=invokeMethod("findFiles",new Object[]{map);
        try{
        if(openshiftYaml.asBoolean()){
        Object contents=invokeMethod("readFile",new Object[]{DefaultGroovyMethods.getAt(openshiftYaml,0).path);
        if(contents!=null){
        if(contents.invokeMethod("contains",new Object[]{"kind: \"ImageStream\"")||contents.invokeMethod("contains",new Object[]{"kind: ImageStream")||contents.invokeMethod("contains",new Object[]{"kind: \'ImageStream\'")){
        echo("OpenShift YAML contains an ImageStream");
        return true;
        }else{
        echo("OpenShift YAML does not contain an ImageStream so not using S2I binary mode");
        }

        }

        }else{
        echo("Warning OpenShift YAML "+openshiftYaml)+" does not exist!");
        }

        }catch(Exception e){
        invokeMethod("error",new Object[]{"Failed to load "+DefaultGroovyMethods.getAt(openshiftYaml,0))+" due to "+e));
        }

        return false;
        }

*/
/**
 * Deletes the given namespace if it exists
 *
 * @param name the name of the namespace
 * @return true if the delete was successful
 * <p>
 * Should be called after checkout scm
 * <p>
 * Should be called after checkout scm
 * <p>
 * Should be called after checkout scm
 *//*

@NonCPS
public Boolean deleteNamespace(final String name){
        KubernetesClient kubernetes=createKubernetesClient();
        try{
        Namespace namespace=((DefaultKubernetesClient)kubernetes).namespaces().withName(name).get();
        if(namespace!=null){
        echo("Deleting namespace "+name+"...");
        ((DefaultKubernetesClient)kubernetes).namespaces().withName(name).delete();
        echo("Deleted namespace "+name);

        // TODO should we wait for the namespace to really go away???
        namespace=((DefaultKubernetesClient)kubernetes).namespaces().withName(name).get();
        if(namespace!=null){
        echo("Namespace "+name+" still exists!");
        }

        return true;
        }

        return false;
        }catch(Exception e){
        // ignore errors
        return false;
        }

        }

@NonCPS
public String getCloudConfig(){
        Cloud openshiftCloudConfig= Jenkins.getInstance().getCloud("openshift");
        return DefaultGroovyMethods.asBoolean((openshiftCloudConfig))?"openshift":"kubernetes";
        }

*/
/**
 * Should be called after checkout scm
 *//*

@NonCPS
public Object getScmPushUrl(){
        LinkedHashMap<String, Serializable> map=new LinkedHashMap<String, Serializable>(2);
        map.put("returnStdout",true);
        map.put("script","git config --get remote.origin.url");
        Object url=execBashAndGetOutput(map).invokeMethod("trim",new Object[0]);

        if(!url.asBoolean()){
        invokeMethod("error",new Object[]{"no URL found for git config --get remote.origin.url ");
        }

        return url;
        }

@NonCPS
public Boolean openShiftImageStreamExists(final String name){
        if(isOpenShift()){
        try{
        LinkedHashMap<String, Serializable> map=new LinkedHashMap<String, Serializable>(2);
        map.put("returnStdout",true);
        map.put("script","oc describe is ${name} --namespace openshift");
        Object result=execBashAndGetOutput(map);
        if(result&&result.invokeMethod("contains",new Object[]{name)){
        echo("ImageStream  "+name+" is already installed globally");
        return true;
        }else{
//see if its already in our namespace
final Object namespace=this.getBinding().getProperty("kubernetes").invokeMethod("getNamespace",new Object[0]);
        LinkedHashMap<String, Serializable> map1=new LinkedHashMap<String, Serializable>(2);
        map1.put("returnStdout",true);
        map1.put("script","oc describe is ${name} --namespace ${namespace}");
        result=execBashAndGetOutput(map1);
        if(result&&result.invokeMethod("contains",new Object[]{name)){
        echo("ImageStream  "+name+" is already installed in project "+namespace));
        return true;
        }

        }

        }catch(Exception e){
        echo("Warning: "+e)+" ");
        }

        }

        return false;
        }

@NonCPS
public Boolean openShiftImageStreamInstall(final String name,String location){
        if(openShiftImageStreamExists(name)){
        echo("ImageStream "+name+" does not exist - installing ...");
        try{
        LinkedHashMap<String, Serializable> map=new LinkedHashMap<String, Serializable>(2);
        map.put("returnStdout",true);
        map.put("script","oc create -f  ${location}");
        Object result=execBashAndGetOutput(map);
final Object namespace=this.getBinding().getProperty("kubernetes").invokeMethod("getNamespace",new Object[0]);
        echo("ImageStream "+name+" now installed in project "+namespace));
        return true;
        }catch(Exception e){
        echo("Warning: "+e)+" ");
        }

        }

        return false;
        }
*/
