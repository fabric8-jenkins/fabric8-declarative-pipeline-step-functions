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
package io.fabric8.support;


import io.fabric8.utils.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

/**
 */
public class Tests {
    private static final transient Logger LOG = LoggerFactory.getLogger(Tests.class);

    public static File getBasedir() {
        String basedirName = System.getProperty("basedir", ".");
        return new File(basedirName);
    }

    /**
     * Copies the source test data files into a test data area so we can test on the files and modify them
     *
     * @return the directory of test files
     */
    public static File copyPackageSources(Class<?> clazz) {
        String packagePath = getPackagePath(clazz);
        File basedir = Tests.getBasedir();
        File testDir = getTestDataDir(clazz);
        File srcDir = new File(basedir, "src/test/resources/" + packagePath);

        assertThat(srcDir).describedAs("Source test files for test " + clazz.getName()).isDirectory();

        if (testDir.exists()) {
            Files.recursiveDelete(testDir);
        }

        assertThat(testDir).describedAs("Test data dir").doesNotExist();

        testDir.mkdirs();

        try {
            Files.copy(srcDir, testDir);
        } catch (IOException e) {
            fail("Failed to copy " + srcDir + " to " + testDir + ". " + e, e);
        }

        assertThat(testDir).describedAs("Test data dir").isDirectory();
        File[] childFiles = testDir.listFiles();
        assertThat(childFiles).describedAs("Test data dir " + testDir).isNotEmpty();
        return testDir;
    }

    public static String getPackagePath(Class<?> clazz) {
        return clazz.getPackage().getName().replace('.', '/');
    }

    public static File getTestDataDir(Class<?> clazz) {
        String packagePath = getPackagePath(clazz);
        File basedir = Tests.getBasedir();
        return new File(basedir, "target/test-data/" + packagePath + "/" + clazz.getSimpleName());
    }

    /**
     * Returns a local test file, asserting that the file exists
     */
    public static File testFile(File dir, String localPath) {
        File answer = new File(dir, localPath);
        assertThat(answer).describedAs("test file").isFile();
        return answer;
    }

    /**
     * Gets a clean work dir for the given test class
     */
    public static File getCleanWorkDir(Class<?> clazz) {
        File testDataDir = getTestDataDir(clazz);
        System.out.println("Using workDir: " + testDataDir);
        String property = System.getProperty("preserve.testdir", "false");
        if (property.equals("true")) {
            LOG.info("Preserving contents of " + testDataDir + " to speed up test");
        } else {
            Files.recursiveDelete(testDataDir);
        }
        testDataDir.mkdirs();
        return testDataDir;
    }
}
