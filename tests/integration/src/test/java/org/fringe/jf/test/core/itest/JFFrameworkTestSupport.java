/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fringe.jf.test.core.itest;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.features.BootFinished;
import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeaturesService;
import org.junit.Assert;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;
import java.io.*;
import java.net.URL;
import java.security.Principal;
import java.security.PrivilegedExceptionAction;
import java.util.*;
import java.util.concurrent.*;


public class JFFrameworkTestSupport {
    public static final String RMI_SERVER_PORT = "44445";
    public static final String HTTP_PORT = "9081";
    public static final String RMI_REG_PORT = "1100";

    static final Long COMMAND_TIMEOUT = 30000L;
    static final Long SERVICE_TIMEOUT = 30000L;

    private static Logger LOG = LoggerFactory.getLogger(JFFrameworkTestSupport.class);

//    @Rule
//    public KarafTestWatcher baseTestWatcher = new KarafTestWatcher();

    ExecutorService executor = Executors.newCachedThreadPool();

    @Inject
    protected BundleContext bundleContext;

    @Inject
    protected FeaturesService featureService;

    /**
     * To make sure the tests run only when the boot features are fully installed
     */
    @Inject
    BootFinished bootFinished;

    @ProbeBuilder
    public TestProbeBuilder probeConfiguration(TestProbeBuilder probe) {
        probe.setHeader(Constants.DYNAMICIMPORT_PACKAGE, "*,org.apache.felix.service.*;status=provisional");
        return probe;
    }

    public File getConfigFile(String path) {
        URL res = this.getClass().getResource(path);
        if (res == null) {
            throw new RuntimeException("Config resource " + path + " not found");
        }
        return new File(res.getFile());
    }



    /**
     * Executes a shell command and returns output as a String.
     * Commands have a default timeout of 10 seconds.
     *
     * @param command The command to execute
     * @param principals The principals (e.g. RolePrincipal objects) to run the command under
     * @return
     */
    protected String executeCommand(final String command, Principal... principals) {
        return executeCommand(command, COMMAND_TIMEOUT, false, principals);
    }

    /**
     * Executes a shell command and returns output as a String.
     * Commands have a default timeout of 10 seconds.
     *
     * @param command    The command to execute.
     * @param timeout    The amount of time in millis to wait for the command to execute.
     * @param silent     Specifies if the command should be displayed in the screen.
     * @param principals The principals (e.g. RolePrincipal objects) to run the command under
     * @return
     */
    protected String executeCommand(final String command, final Long timeout, final Boolean silent, final Principal... principals) {
        waitForCommandService(command);

        String response;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArrayOutputStream);
        final CommandProcessor commandProcessor = getOsgiService(CommandProcessor.class);
        final CommandSession commandSession = commandProcessor.createSession(System.in, printStream, System.err);

        final Callable<String> commandCallable = new Callable<String>() {
            @Override
            public String call() throws Exception {
                try {
                    if (!silent) {
                        System.err.println(command);
                    }
                    commandSession.execute(command);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                printStream.flush();
                return byteArrayOutputStream.toString();
            }
        };

        FutureTask<String> commandFuture;
        if (principals.length == 0) {
            commandFuture = new FutureTask<String>(commandCallable);
        } else {
            // If principals are defined, run the command callable via Subject.doAs()
            commandFuture = new FutureTask<String>(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    Subject subject = new Subject();
                    subject.getPrincipals().addAll(Arrays.asList(principals));
                    return Subject.doAs(subject, new PrivilegedExceptionAction<String>() {
                        @Override
                        public String run() throws Exception {
                            return commandCallable.call();
                        }
                    });
                }
            });
        }

        try {
            executor.submit(commandFuture);
            response = commandFuture.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            e.printStackTrace(System.err);
            response = "SHELL COMMAND TIMED OUT: ";
        } catch (ExecutionException e) {
            Throwable cause = e.getCause().getCause();
            throw new RuntimeException(cause.getMessage(), cause);
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return response;
    }


    protected <T> T getOsgiService(Class<T> type, long timeout) {
        return getOsgiService(type, null, timeout);
    }

    protected <T> T getOsgiService(Class<T> type) {
        return getOsgiService(type, null, SERVICE_TIMEOUT);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected <T> T getOsgiService(Class<T> type, String filter, long timeout) {
        ServiceTracker tracker = null;
        try {
            String flt;
            if (filter != null) {
                if (filter.startsWith("(")) {
                    flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")" + filter + ")";
                } else {
                    flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")(" + filter + "))";
                }
            } else {
                flt = "(" + Constants.OBJECTCLASS + "=" + type.getName() + ")";
            }
            Filter osgiFilter = FrameworkUtil.createFilter(flt);
            tracker = new ServiceTracker(bundleContext, osgiFilter, null);
            tracker.open(true);
            // Note that the tracker is not closed to keep the reference
            // This is buggy, as the service reference may change i think
            Object svc = type.cast(tracker.waitForService(timeout));
            if (svc == null) {
                Dictionary dic = bundleContext.getBundle().getHeaders();
                System.err.println("Test bundle headers: " + explode(dic));

                for (ServiceReference ref : asCollection(bundleContext.getAllServiceReferences(null, null))) {
                    System.err.println("ServiceReference: " + ref);
                }

                for (ServiceReference ref : asCollection(bundleContext.getAllServiceReferences(null, flt))) {
                    System.err.println("Filtered ServiceReference: " + ref);
                }

                throw new RuntimeException("Gave up waiting for service " + flt);
            }
            return type.cast(svc);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Invalid filter", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void waitForCommandService(String command) {
        // the commands are represented by services. Due to the asynchronous nature of services they may not be
        // immediately available. This code waits the services to be available, in their secured form. It
        // means that the code waits for the command service to appear with the roles defined.

        if (command == null || command.length() == 0) {
            return;
        }

        int spaceIdx = command.indexOf(' ');
        if (spaceIdx > 0) {
            command = command.substring(0, spaceIdx);
        }
        int colonIndx = command.indexOf(':');

        try {
            if (colonIndx > 0) {
                String scope = command.substring(0, colonIndx);
                String function = command.substring(colonIndx + 1);
                waitForService("(&(osgi.command.scope=" + scope + ")(osgi.command.function=" + function + "))", SERVICE_TIMEOUT);
            } else {
                waitForService("(osgi.command.function=" + command + ")", SERVICE_TIMEOUT);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void waitForService(String filter, long timeout) throws InvalidSyntaxException, InterruptedException {
        ServiceTracker<Object, Object> st = new ServiceTracker<Object, Object>(bundleContext, bundleContext.createFilter(filter), null);
        try {
            st.open();
            st.waitForService(timeout);
        } finally {
            st.close();
        }
    }

    /*
    * Explode the dictionary into a ,-delimited list of key=value pairs
    */
    @SuppressWarnings("rawtypes")
    private static String explode(Dictionary dictionary) {
        Enumeration keys = dictionary.keys();
        StringBuffer result = new StringBuffer();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            result.append(String.format("%s=%s", key, dictionary.get(key)));
            if (keys.hasMoreElements()) {
                result.append(", ");
            }
        }
        return result.toString();
    }

    /**
     * Provides an iterable collection of references, even if the original array is null
     */
    @SuppressWarnings("rawtypes")
    private static Collection<ServiceReference> asCollection(ServiceReference[] references) {
        return references != null ? Arrays.asList(references) : Collections.<ServiceReference>emptyList();
    }

    public JMXConnector getJMXConnector() throws Exception {
        return getJMXConnector("karaf", "karaf");
    }

    public JMXConnector getJMXConnector(String userName, String passWord) throws Exception {
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + RMI_REG_PORT+ "/karaf-root");
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        String[] credentials = new String[]{ userName, passWord };
        env.put("jmx.remote.credentials", credentials);
        JMXConnector connector = JMXConnectorFactory.connect(url, env);
        return connector;
    }

    public void assertFeatureInstalled(String featureName) {
        Feature[] features = new Feature[0];
        try {
            features = featureService.listInstalledFeatures();

            for (Feature feature : features) {
                if (featureName.equals(feature.getName())) {
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Assert.fail("Feature " + featureName + " should be installed but is not");
    }

    public void assertFeaturesInstalled(String... expectedFeatures) {
        Set<String> expectedFeaturesSet = new HashSet<String>(Arrays.asList(expectedFeatures));
        Feature[] features = new Feature[0];
        try {
            features = featureService.listInstalledFeatures();

            Set<String> installedFeatures = new HashSet<String>();
            for (Feature feature : features) {
                installedFeatures.add(feature.getName());
            }
            String msg = "Expecting the following features to be installed : " + expectedFeaturesSet + " but found " + installedFeatures;
            Assert.assertTrue(msg, installedFeatures.containsAll(expectedFeaturesSet));
        } catch (Exception e) {
            e.printStackTrace();
        }
        //Assert.fail("Feature " + featureName + " should be installed but is not");

    }

    public void assertContains(String expectedPart, String actual) {
        Assert.assertTrue("Should contain '" + expectedPart + "' but was : " + actual, actual.contains(expectedPart));
    }

    public void assertContainsNot(String expectedPart, String actual) {
        Assert.assertFalse("Should not contain '" + expectedPart + "' but was : " + actual, actual.contains(expectedPart));
    }

    protected void assertBundleInstalled(String name) {
        Assert.assertNotNull("Bundle " + name + " should be installed", findBundleByName(name));
    }

    protected void assertBundleNotInstalled(String name) {
        Assert.assertNull("Bundle " + name + " should not be installed", findBundleByName(name));
    }

    protected Bundle findBundleByName(String symbolicName) {
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getSymbolicName().equals(symbolicName)) {
                return bundle;
            }
        }
        return null;
    }

    protected void installAndAssertFeature(String feature, EnumSet<FeaturesService.Option> options) throws Exception {
        featureService.installFeature(feature, options);
        assertFeatureInstalled(feature);
    }

    protected void installAssertAndUninstallFeature(String... feature) throws Exception {
        Set<Feature> featuresBefore = new HashSet<Feature>(Arrays.asList(featureService.listInstalledFeatures()));
        try {
            for (String curFeature : feature) {
                featureService.installFeature(curFeature);
                assertFeatureInstalled(curFeature);
            }
        } finally {
            uninstallNewFeatures(featuresBefore);
        }
    }

    /**
     * The feature service does not uninstall feature dependencies when uninstalling a single feature.
     * So we need to make sure we uninstall all features that were newly installed.
     *
     * @param featuresBefore
     * @throws Exception
     */
    protected void uninstallNewFeatures(Set<Feature> featuresBefore)
            throws Exception {
        Feature[] features = featureService.listInstalledFeatures();
        for (Feature curFeature : features) {
            if (!featuresBefore.contains(curFeature)) {
                try {
                    System.out.println("Uninstalling " + curFeature.getName());
                    featureService.uninstallFeature(curFeature.getName(), curFeature.getVersion());
                } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        }
    }

    protected void close(Closeable closeAble) {
        if (closeAble != null) {
            try {
                closeAble.close();
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }
}
