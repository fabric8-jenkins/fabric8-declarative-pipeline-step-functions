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
import io.fabric8.kubernetes.api.environments.Environments;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.pipelines.PipelineConfiguration;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamStatus;
import io.fabric8.openshift.api.model.NamedTagEventList;
import io.fabric8.openshift.api.model.TagEvent;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftAPIGroups;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Strings;
import jenkins.model.Jenkins;
import org.apache.commons.beanutils.PropertyUtils;
import org.csanchez.jenkins.plugins.kubernetes.PodAnnotation;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils extends FunctionSupport {

    public static String defaultNamespace(KubernetesClient kubernetesClient) {
        String namespace = kubernetesClient.getNamespace();
        if (Strings.isNullOrBlank(namespace)) {
            namespace = KubernetesHelper.defaultNamespace();
        }
        if (Strings.isNullOrBlank(namespace)) {
            namespace = KubernetesHelper.defaultNamespace();
        }
        if (Strings.isNullOrBlank(namespace)) {
            namespace = "default";
        }
        return namespace;
    }


    public static KubernetesClient createKubernetesClient() {
        return new DefaultKubernetesClient();
    }

    public static String getNamespace() {
        return defaultNamespace(createKubernetesClient());
    }

    @NonCPS
    public String environmentNamespace(final String environment) {
        KubernetesClient kubernetesClient = createKubernetesClient();
        String answer = Environments.namespaceForEnvironment(kubernetesClient, environment, defaultNamespace(kubernetesClient));
        if (Strings.notEmpty(answer)) {
            return answer;
        }

        String ns = getNamespace();
        if (ns.endsWith("-jenkins")) {
            ns = ns.substring(0, ns.lastIndexOf("-jenkins"));
        }

        return ns + "-" + environment.toLowerCase();
    }

    /**
     * Loads the environments in the default user namespace
     */
    @NonCPS
    public Environments environments() {
        KubernetesClient kubernetesClient = createKubernetesClient();
        return Environments.load(kubernetesClient, defaultNamespace(kubernetesClient));
    }

    /**
     * Loads the environments from the given namespace
     */
    @NonCPS
    public Environments environments(String namespace) {
        KubernetesClient kubernetesClient = createKubernetesClient();
        return Environments.load(kubernetesClient, namespace);
    }

    /**
     * Loads the environments from the user namespace
     */
    @NonCPS
    public PipelineConfiguration pipelineConfiguration() {
        KubernetesClient kubernetesClient = createKubernetesClient();
        return PipelineConfiguration.loadPipelineConfiguration(kubernetesClient, defaultNamespace(kubernetesClient));
    }

    /**
     * Loads the environments from the given namespace
     */
    @NonCPS
    public PipelineConfiguration pipelineConfiguration(String namespace) {
        KubernetesClient kubernetesClient = createKubernetesClient();
        return PipelineConfiguration.loadPipelineConfiguration(kubernetesClient, namespace);
    }

    /**
     * Returns true if the integration tests should be disabled
     */
    @NonCPS
    public boolean isDisabledITests() {
        boolean answer = false;
        try {
            final PipelineConfiguration config = pipelineConfiguration();
            echo("Loaded PipelineConfiguration " + config);
            if (isCD()) {
                answer = config.isDisableITestsCD();
            } else if (isCI()) {
                answer = config.isDisableITestsCI();
            }
        } catch (Exception e) {
            echo("WARNING: Failed to find the flag on the PipelineConfiguration object - probably due to the jenkins plugin `kubernetes-pipeline-plugin` version: " + e);
            e.printStackTrace();
        }
        //answer = true;
        return answer;
    }

    /**
     * Returns true if we should use S2I to build docker images
     */
    @NonCPS
    public boolean isUseOpenShiftS2IForBuilds() {
        return !isUseDockerSocket();
    }

    /**
     * Returns true if the current cluster can support S2I
     */
    @NonCPS
    public boolean supportsOpenShiftS2I() {
        OpenShiftClient client = new DefaultOpenShiftClient();
        return client.supportsOpenShiftAPIGroup(OpenShiftAPIGroups.IMAGE);
    }

    /**
     * Returns true if we should mount the docker socket for docker builds
     */
    @NonCPS
    public boolean isUseDockerSocket() {
        final PipelineConfiguration config = pipelineConfiguration();
        echo("Loaded PipelineConfiguration " + config);

        Boolean flag = config.getUseDockerSocketFlag();
        if (flag != null) {
            return flag.booleanValue();
        }
        return supportsOpenShiftS2I() ? false : true;
    }

    @NonCPS
    public String getDockerRegistry() {
        String externalDockerRegistryURL = getUsersPipelineConfig("external-docker-registry-url");
        if (Strings.notEmpty(externalDockerRegistryURL)) {
            return externalDockerRegistryURL;
        }


        // fall back to the old < 4.x when the registry was in the same namespace
        String registryHost = System.getenv("FABRIC8_DOCKER_REGISTRY_SERVICE_HOST");
        String registryPort = System.getenv("FABRIC8_DOCKER_REGISTRY_SERVICE_PORT");
        if (Strings.isNullOrBlank(registryHost) || Strings.isNullOrBlank(registryPort)) {
            error("No external-docker-registry-url found in Jenkins configmap or no FABRIC8_DOCKER_REGISTRY_SERVICE_HOST FABRIC8_DOCKER_REGISTRY_SERVICE_PORT environment variables");
        }
        return registryHost + ":" + registryPort;
    }

    @NonCPS
    public String getUsersPipelineConfig(final String k) {
        // first lets check if we have the new pipelines configmap in the users home namespace
        KubernetesClient client = new DefaultKubernetesClient();
        final String ns = getUsersNamespace();
        ConfigMap r = client.configMaps().inNamespace(ns).withName("fabric8-pipelines").get();
        if (r == null) {
            error("no fabric8-pipelines configmap found in namespace " + ns);
            return null;
        }

        Map<String, String> d = r.getData();
        if (d != null) {
            echo("looking for key " + k + " in " + ns + "/fabric8-pipelines configmap");
            return d.get(k);
        }
        return null;
    }

    @NonCPS
    public String getConfigMap(String ns, final String cm, String key) {

        // first lets check if we have the new pipeliens configmap in the users home namespace
        KubernetesClient client = new DefaultKubernetesClient();

        ConfigMap r = client.configMaps().inNamespace(ns).withName(cm).get();
        if (r == null) {
            error("no " + cm + " configmap found in namespace " + ns);
            return null;
        }
        Map<String, String> data = r.getData();
        if (data != null && Strings.notEmpty(key)) {
            return data.get(key);
        }
        return null;
    }

    @NonCPS
    private Map<String, String> parseConfigMapData(final String input) {
        final Map<String, String> map = new HashMap<String, String>();
        for (String pair : input.split("\n")) {
            String[] kv = pair.split(":");
            if (kv.length > 1) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }

    @NonCPS
    public String getImageStreamSha(Object imageStreamName) {
        OpenShiftClient oc = new DefaultOpenShiftClient();
        return findTagSha(oc, (String) imageStreamName, getNamespace());
    }

    @NonCPS
    public String findTagSha(OpenShiftClient client, final String imageStreamName, String namespace) {
        Object currentImageStream = null;
        for (int i = 0; i < 15; i++) {
            if (i > 0) {
                echo("Retrying to find tag on ImageStream " + imageStreamName);
            }
            ;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                echo("interrupted " + e);
            }
            ;
            currentImageStream = client.imageStreams().withName(imageStreamName).get();
            if (currentImageStream == null) {
                continue;
            }

            ImageStreamStatus status = ((ImageStream) currentImageStream).getStatus();
            if (status == null) {
                continue;
            }

            List<NamedTagEventList> tags = status.getTags();
            if (tags == null || tags.isEmpty()) {
                continue;
            }

            // latest tag is the first
            TAG_EVENT_LIST:
            for (NamedTagEventList list : tags) {
                List<TagEvent> items = list.getItems();
                if (items == null) {
                    continue TAG_EVENT_LIST;
                }

                // latest item is the first
                for (TagEvent item : items) {
                    String image = item.getImage();
                    if (image != null && !image.equals("")) {
                        echo("Found tag on ImageStream " + imageStreamName + " tag: " + image);
                        return image;
                    }

                }

            }

            // No image found, even after several retries:
            if (currentImageStream == null) {
                error("Could not find a current ImageStream with name " + imageStreamName + " in namespace " + namespace);
                return null;
            } else {
                error("Could not find a tag in the ImageStream " + imageStreamName);
                return null;
            }
        }
        return null;
    }

    @NonCPS
    public Build addAnnotationToBuild(final String annotation, final String value) {
        io.fabric8.Fabric8Commands flow = new io.fabric8.Fabric8Commands();
        if (flow.isOpenShift()) {
            String buildName = getValidOpenShiftBuildName();
            echo("Adding annotation \'" + annotation + ": " + value + "\' to Build " + buildName);
            OpenShiftClient oClient = new DefaultOpenShiftClient();
            final String usersNamespace = getUsersNamespace();
            echo("looking for " + buildName + " in namespace " + usersNamespace);
        } else {
            echo("Not running on openshift so skip adding annotation " + annotation + ": value");
        }
        return null;
    }

    @NonCPS
    public String getUsersNamespace() {
        String usersNamespace = getNamespace();
        if (usersNamespace.endsWith("-jenkins")) {
            usersNamespace = usersNamespace.substring(0, usersNamespace.lastIndexOf("-jenkins"));
        }
        return usersNamespace;
    }

    public String getBranch() {
        final String branch = System.getenv("BRANCH_NAME");
        if (Strings.isNullOrBlank(branch)) {
            try {
                return execAndGetOutput("git", "symbolic-ref", "--short", "HEAD").trim();
            } catch (Exception err) {
                echo("Unable to get git branch and in a detached HEAD. You may need to select Pipeline additional behaviour and \'Check out to specific local branch\'");
            }
        }
        return branch;

    }

    public boolean isCI() {
        String branch = getBranch();
        return Strings.notEmpty(branch) && branch.startsWith("PR-");
    }

    public Boolean isCD() {
        String branch = getBranch();
        return Strings.notEmpty(branch) && branch.equalsIgnoreCase("master");
    }

    public Build addPipelineAnnotationToBuild(String t) {
        return addAnnotationToBuild("fabric8.io/pipeline.type", t);
    }


    public Object getLatestVersionFromTag() throws IOException {
        sh("git fetch --tags");
        sh("git config versionsort.prereleaseSuffix -RC");
        sh("git config versionsort.prereleaseSuffix -M");

        // if the repo has no tags this command will fail
        try {
            String answer = shOutput("git tag --sort version:refname | tail -1").trim();
            if (Strings.isNullOrBlank(answer)) {
                error("no release tag found");
            } else if (answer.startsWith("v")) {
                return answer.substring(1);
            }
            return answer;
        } catch (Exception err) {
            error("Failed to query tags from git: " + err);
            return null;
        }
    }

    @NonCPS
    public Boolean isValidBuildName(final String buildName) {
        io.fabric8.Fabric8Commands flow = new io.fabric8.Fabric8Commands();
        if (flow.isOpenShift()) {
            echo("Looking for matching Build " + buildName);
        }
        OpenShiftClient oClient = new DefaultOpenShiftClient();
        String usersNamespace = getUsersNamespace();
        Build build = oClient.builds().inNamespace(usersNamespace).withName(buildName).get();
        return build != null;
    }

    @NonCPS
    public String getValidOpenShiftBuildName() {
        final String buildName = getOpenShiftBuildName();
        if (isValidBuildName(buildName)) {
            return buildName;
        } else {
            error("No matching openshift build with name " + buildName + " found");
            return null;
        }
    }

/*
    public Object replacePackageVersion(final String packageLocation, String pair) {

        final Object property = DefaultGroovyMethods.getAt(pair, 0);
        final Object version = DefaultGroovyMethods.getAt(pair, 1);

        return invokeMethod("sh", new Object[]{"sed -i -r \'s/\"" + property) + "\": \"[0-9][0-9]{0,2}.[0-9][0-9]{0,2}(.[0-9][0-9]{0,2})?(.[0-9][0-9]{0,2})?(-development)?\"/\"" + property) + "\": \"" + version) + "\"/g\' " + packageLocation)});

    }

    public void replacePackageVersions(Object packageLocation, String replaceVersions) {
        for (int i = 0; i.compareTo(replaceVersions.invokeMethod("size", new Object[0])) < 0; i++) {
            replacePackageVersion(packageLocation, DefaultGroovyMethods.getAt(replaceVersions, i));
        }

    }

    public Object getExistingPR(final String project, Object pair) {
        final Object property = DefaultGroovyMethods.getAt(pair, 0);
        final Object version = DefaultGroovyMethods.getAt(pair, 1);

        Fabric8Commands flow = new Fabric8Commands();
        final Object githubToken = flow.getGitHubToken();
        final URL apiUrl = new URL("https://api.github.com/repos/" + project) + "/pulls");
        Object rs = invokeMethod("restGetURL", new Object[]{new Closure<URL>(this, this) {
            public URL doCall(Object it) {
                setProperty("authString", githubToken);
                setProperty("url", apiUrl);
                return apiUrl;
            }

            public URL doCall() {
                return doCall(null);
            }

        }});

        if (rs == null || rs.invokeMethod("isEmpty", new Object[0])) {
            return null;
        }

        for (int i = 0; i.compareTo(rs.invokeMethod("size", new Object[0])) < 0; i++) {
            final Object pr = DefaultGroovyMethods.getAt(rs, i);
            echo("checking PR " + pr.number)
        });
        if (pr.state == "open" && pr.title.invokeMethod("contains", new Object[]{"fix(version): update " + property) + " to " + version)})) {
            echo("matched"
        });
        return pr.number;
    }

}

        return null;
                }

public Object getOpenPRs(final Object project){

        List openPRs=new ArrayList();
        Fabric8Commands flow=new Fabric8Commands();
final Object githubToken=flow.getGitHubToken();
final URL apiUrl=new URL("https://api.github.com/repos/"+project)+"/pulls");
        Object rs=invokeMethod("restGetURL",new Object[]{new Closure<URL>(this,this){
public URL doCall(Object it){
        setProperty("authString",githubToken);
        setProperty("url",apiUrl);
        return apiUrl;
        }

public URL doCall(){
        return doCall(null);
        }

        }});

        if(rs==null||rs.invokeMethod("isEmpty",new Object[0])){
        return false;
        }

        for(int i=0;i.compareTo(rs.invokeMethod("size",new Object[0]))< 0;i++){
        Object pr=DefaultGroovyMethods.getAt(rs,i);

        if(pr.state=="open"){
        DefaultGroovyMethods.leftShift(openPRs,pr.number));
        }

        }

        return openPRs;
        }

public Object getDownstreamProjectOverrides(Object project,Object id,Object downstreamProject,Object botName){

        if(!downstreamProject.asBoolean()){
        invokeMethod("error",new Object[]{"no downstreamProjects provided"});
        }

        Fabric8Commands flow=new Fabric8Commands();
        Object comments=flow.getIssueComments(project,id);
        // start by looking at the most recent commments and work back
        Collections.reverse(comments);
        for(Object comment:comments){
        echo("Found PR comment "+comment.body)});
        Object text=comment.body.invokeMethod("trim",new Object[0]);
        String match="CI downstream projects";
        if(text.invokeMethod("startsWith",new Object[]{botName}).asBoolean()){
        if(text.invokeMethod("contains",new Object[]{match}).asBoolean()){
        Object result=text.invokeMethod("substring",new Object[]{text.invokeMethod("indexOf",new Object[]{"["})+1,text.invokeMethod("indexOf",new Object[]{"]"})});
        if(!result.asBoolean()){
        echo("no downstream projects found"});
        }

        ArrayList list=(ArrayList)DefaultGroovyMethods.split(result,",");
        for(Object repos:list){
        if(!DefaultGroovyMethods.invokeMethod(repos,"contains",new Object[]{"="}).asBoolean()){
        invokeMethod("error",new Object[]{"no override project found in the form organisation=foo"});
        }

final ArrayList overrides=(ArrayList)DefaultGroovyMethods.split(repos,"=");
        if(downstreamProject==DefaultGroovyMethods.invokeMethod(overrides.get(0),"trim",new Object[0])){
        "matched and returning "+DefaultGroovyMethods.invokeMethod(overrides.get(1),"trim",new Object[0]));
        return DefaultGroovyMethods.invokeMethod(overrides.get(1),"trim",new Object[0]);
        }

        }

        }

        }

        }

        }

public Object getDownstreamProjectOverrides(Object project,Object id,Object downstreamProject){
        return getDownstreamProjectOverrides(project,id,downstreamProject,"@fabric8cd");
        }

public Boolean hasPRComment(Object project,Object id,Object match){
        Fabric8Commands flow=new Fabric8Commands();
        Object comments=flow.getIssueComments(project,id);
        // start by looking at the most recent commments and work back
        Collections.reverse(comments);
        for(Object comment:comments){
        echo("Found PR comment "+comment.body)});
        Object text=comment.body.invokeMethod("trim",new Object[0]);
        if(text.invokeMethod("equalsIgnoreCase",new Object[]{match}).asBoolean()){
        return true;
        }

        }

        return false;
        }

public Object getDownstreamProjectOverrides(Object downstreamProject,Object botName){

        Fabric8Commands flow=new Fabric8Commands();

        Object id=System.getenv("CHANGE_ID;
        if(!id.asBoolean()){
        invokeMethod("error",new Object[]{"no env.CHANGE_ID / pull request id found"});
        }


        Object project=getRepoName();

        return getDownstreamProjectOverrides(project,id,downstreamProject,botName="@fabric8cd");
        }

public Object getDownstreamProjectOverrides(Object downstreamProject){
        return getDownstreamProjectOverrides(downstreamProject,"@fabric8cd");
        }
*/


    public Boolean isSkipCIDeploy(String botName) {
        String id = System.getenv("CHANGE_ID");
        if (Strings.isNullOrBlank(id)) {
            error("no env.CHANGE_ID / pull request id found");
        }


        io.fabric8.Fabric8Commands flow = new io.fabric8.Fabric8Commands();
        String project = getRepoName();

        List comments = flow.getIssueComments(project, id);
        String skipTrue = "CI skip deploy=true";
        String skipFalse = "CI skip deploy=false";

        // start by looking at the most recent commments and work back
        Collections.reverse(comments);
        for (Object comment : comments) {
            Object body = null;
            try {
                body = PropertyUtils.getProperty(comment, "body");
            } catch (Exception e) {
                error("Failed to get body property on " + comment + " due to: " + e);
            }

            if (body instanceof String) {
                String text = body.toString().trim();
                if (text.startsWith(botName)) {
                    if (text.contains(skipTrue)) {
                        return true;
                    } else if (text.contains(skipFalse)) {
                        return false;
                    }

                }
            }
        }
        return null;
    }

    public Boolean isSkipCIDeploy() {
        return isSkipCIDeploy("@fabric8cd");
    }

    public String getRepoName() {
        String jobName = System.getenv("JOB_NAME");
        int firstIdx = jobName.indexOf('/');
        int lastIdx = jobName.lastIndexOf('/');
        if (firstIdx > 0 && lastIdx > 0 && firstIdx != lastIdx) {
            // job name from the org plugin
            return jobName.substring(firstIdx + 1, lastIdx);
        } else if (lastIdx > 0) {
            // job name from the branch plugin
            return jobName.substring(0, lastIdx);
        } else {
            // normal job name
            return jobName;
        }
    }

    @NonCPS
    public String getOpenShiftBuildName() {
        try {
            Jenkins activeInstance = Jenkins.getInstance();
            WorkflowJob job = (WorkflowJob) activeInstance.getItemByFullName(System.getenv("JOB_NAME"));
            WorkflowRun run = job.getBuildByNumber(Integer.parseInt(System.getenv("BUILD_NUMBER")));
            Fabric8Commands flow = new Fabric8Commands();
            if (flow.isOpenShift()) {
                Class clazz;
                try {
                    clazz = Thread.currentThread().getContextClassLoader().loadClass("io.fabric8.jenkins.openshiftsync.BuildCause");
                } catch (ClassNotFoundException e) {
                    error("Failed to load class BuildCause", e);
                    return null;
                }
                try {
                    Object cause = run.getCause(clazz);
                    if (cause != null) {
                        return (String) PropertyUtils.getProperty(cause, "name");
                    }
                } catch (Exception e) {
                    error("Failed to get openshift BuildCause name:", e);
                }
            }
        } catch (Exception e) {
            error("Failed to get openshift build namne", e);
        }
        return null;
    }

    public boolean isKubernetesPluginVersion013() {
        boolean isNewVersion = false;

        try {
            PodAnnotation object = new PodAnnotation("dummy", "dummy");
            Package objPackage = object.getClass().getPackage();
            String version = objPackage.getImplementationVersion();
            // we could be using a custom built jar so remove any -SNAPSHOT from the version
            double v = Double.parseDouble(version.replaceAll("-SNAPSHOT", ""));

            if (v >= 0.13) {
                isNewVersion = true;
            }

        } catch (Exception err) {
            echo("caught error when checking which kubernetes-plugin version we are using; defaulting to < 0.13: " + err);
        }
        return isNewVersion;
    }
}
