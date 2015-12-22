/*
 * Copyright (C) 2013 4th Line GmbH, Switzerland
 *
 * The contents of this file are subject to the terms of either the GNU
 * Lesser General Public License Version 2 or later ("LGPL") or the
 * Common Development and Distribution License Version 1 or later
 * ("CDDL") (collectively, the "License"). You may not use this file
 * except in compliance with the License. See LICENSE.txt for more
 * information.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.fourthline.cling.osgi.test.integration;

import org.apache.commons.codec.binary.Base64;
import org.fourthline.cling.controlpoint.ActionCallback;
import org.fourthline.cling.model.action.ActionArgumentValue;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Action;
import org.fourthline.cling.model.meta.ActionArgument;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.InvalidValueException;
import org.fourthline.cling.model.types.ServiceType;
import org.fourthline.cling.model.types.UnsignedVariableInteger;
import org.fourthline.cling.osgi.test.data.TestData;
import org.fourthline.cling.osgi.test.data.TestDataFactory;
import org.fourthline.cling.osgi.test.util.DataUtil;
import org.fringe.jf.test.core.itest.JFFrameworkTestSupport;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.*;
import org.osgi.service.upnp.UPnPAction;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPService;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.File;
import java.util.*;

import static org.junit.Assert.*;
import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;

@ExamReactorStrategy(PerClass.class)
@RunWith(PaxExam.class)
public class InitialIntegrationTest extends JFFrameworkTestSupport {

	static private final String INITIAL_TEST_DATA_ID = "initial";
	static private final String SET_TEST_DATA_ID = "set";

	@Inject
	BundleContext bundleContext = null;

    @Configuration
    public Option[] config() {
        MavenArtifactUrlReference karafUrl = maven()
                .groupId("org.apache.karaf")
                .artifactId("apache-karaf")
                .type("tar.gz")
                .versionAsInProject();

        MavenUrlReference karafStandardRepo = maven()
                .groupId("org.apache.karaf.features")
                .artifactId("standard")
                .classifier("features")
                .type("xml")
                .versionAsInProject();
        return new Option[] {
                //debugConfiguration("5005", true),
                karafDistributionConfiguration()
                        .frameworkUrl(karafUrl)
                        .unpackDirectory(new File("target/exam"))
                        .useDeployFolder(false),
                keepRuntimeFolder(),
                logLevel(LogLevelOption.LogLevel.ERROR),
                //KarafDistributionOption.features(karafStandardRepo, "scr, eventadmin, jetty/8.1.14.v20131031"),
                KarafDistributionOption.features(karafStandardRepo, "eventadmin, jetty, scr"), // jetty 9


                //mavenBundle().groupId("org.osgi").artifactId("org.osgi.core").versionAsInProject().start(),
                mavenBundle().groupId("org.osgi").artifactId("org.osgi.compendium").versionAsInProject().start(),

                mavenBundle().groupId("org.fourthline.cling.osgi").artifactId("seamless-http").versionAsInProject().start(),
                mavenBundle().groupId("org.fourthline.cling.osgi").artifactId("seamless-util").versionAsInProject().start(),
                mavenBundle().groupId("org.fourthline.cling.osgi").artifactId("seamless-xml").versionAsInProject().start(),

                mavenBundle().groupId("commons-codec").artifactId("commons-codec").versionAsInProject().start(),

                mavenBundle().groupId("org.jemz.core").artifactId("jf-upnp-cling-transport-jetty9").versionAsInProject().start(),
                //mavenBundle().groupId("org.jemz.core").artifactId("jf-upnp-cling-transport-jetty8").versionAsInProject().start(),

                mavenBundle().groupId("org.fourthline.cling.osgi").artifactId("cling-core").versionAsInProject().start(),
                mavenBundle().groupId("org.fourthline.cling.osgi").artifactId("cling-osgi-tests-common").versionAsInProject().start(),

                mavenBundle().groupId("org.fourthline.cling.osgi").artifactId("cling-osgi-basedriver").versionAsInProject().start(),
                mavenBundle().groupId("org.fourthline.cling.osgi").artifactId("cling-osgi-tests-devices-simple").versionAsInProject().start(),




        };
    }



	static private final String DEVICE_TYPE = "urn:schemas-4thline-com:device:simple-test:1";
	static private final String SERVICE_ID = "urn:4thline-com:serviceId:SimpleTest";


    private UPnPDevice findUPnPTestDevice() {
        String string = String.format("(%s=%s)", UPnPDevice.TYPE, DEVICE_TYPE);

        UPnPDevice dev = getOsgiService(UPnPDevice.class, string, 2000);

        assertNotNull(dev);

        Dictionary props = dev.getDescriptions(Locale.getDefault().toString());

        assertEquals(props.get(UPnPDevice.TYPE), DEVICE_TYPE);

        return dev;
    }



    private String bytesToString(byte[] bytes) {
        String string = new String();

        for (int i = 0; i < bytes.length; i++) {
            string += String.format("0x%x ", bytes[i]);
        }

        return string;
    }

    private byte[] toBytes(Byte[] Bytes) {
        byte[] bytes = new byte[Bytes.length];
        for (int i = 0; i < Bytes.length; i++) {
            bytes[i] = Bytes[i].byteValue();
        }

        return bytes;
    }

    private String valueToString(Object value) {
        String string;

        if (value == null) {
            string = "[null]";
        }
        else if (value instanceof byte[]) {
            string = bytesToString((byte[]) value);
        }
        else if (value instanceof Byte[]) {
            string = bytesToString(toBytes((Byte[]) value));
        }
        else {
            string = value.toString();
        }

        return string;
    }

    private boolean validate(String name, String type, Object value, Object desired) {
        boolean matches;

        System.out.printf("=========================================\n");
        System.out.printf("data type: %s\n", type);
        System.out.printf("    value: %s (%s)\n", valueToString(value), value.getClass().getName());
        System.out.printf("  desired: %s (%s)\n", valueToString(desired), desired.getClass().getName());

        if (value instanceof UnsignedVariableInteger) {
            value = Integer.valueOf(((UnsignedVariableInteger) value).getValue().intValue());
        }
        else if (value instanceof Calendar) {
            if (type.equals("time") || type.equals("time.tz")) {
                Calendar calendar = (Calendar) value;
                Date date = calendar.getTime();
                long time = date.getTime() + calendar.getTimeZone().getOffset(date.getTime());
                value = Long.valueOf(time);
            }
            else {
                value = ((Calendar) value).getTime();
            }
        }

        if (value instanceof byte[]) {
            matches = DataUtil.compareBytes((byte[]) value, (byte[]) desired);
        }
        else {
            matches = value.equals(desired);

            if (!matches) {
                matches = value.toString().equals(desired.toString());
            }
        }

        System.out.printf("  matches: %s\n", matches ? "TRUE" : "FALSE");

        return matches;
    }

	private void doSimpleDeviceSetAction(final String name, String testDataId) {
        UPnPDevice dev = findUPnPTestDevice();

//        for(UPnPService service : dev.getServices()) {
//            System.out.println("serviceID: " + service.getId());
//        }

        UPnPService service = dev.getService(SERVICE_ID);
        assertNotNull(service);

//        for(UPnPAction action : service.getActions()) {
//            System.out.println("ACTIONS: " + action.getName());
//        }

        UPnPAction action = service.getAction("GetAllVariables");
        assertNotNull(action);

        try {
            Dictionary result = action.invoke(null);
            Enumeration en = result.keys();

            TestData d = new TestDataFactory().getInstance().getTestData(INITIAL_TEST_DATA_ID);
            while(en.hasMoreElements()) {
                String key = (String)en.nextElement();
                Object object = d.getOSGiUPnPValue(key, /*type*/key);
                assertTrue(validate(key, key, result.get(key), object));

            }
        } catch (Exception e) {
            fail(e.getMessage());
        }

        UPnPAction setAction = service.getAction("SetAllVariables");

        TestData d = new TestDataFactory().getInstance().getTestData(testDataId);
        Properties props = new Properties();
        for(String argName : setAction.getInputArgumentNames()) {
//            System.out.printf("@@@ argument: %s\n", argName);
//            System.out.printf("@@@ type: %s\n", argName);

            Object object = d.getOSGiUPnPValue(argName, /*type*/argName);

//            System.out.println("OBJECT: " + object);

            props.put(argName, object);

        }

        try {
            setAction.invoke(props);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        try {
            Dictionary result = action.invoke(null);

//            System.out.println("RESULT: " + result);
            Enumeration en = result.keys();
            while(en.hasMoreElements()) {
                String key = (String) en.nextElement();
                assertTrue(validate(key, key, result.get(key), props.get(key)));
            }


        } catch (Exception e) {
            fail(e.getMessage());
        }
	}

	@Test
	public void testSimpleDeviceSetAllVariablesAction() {
		doSimpleDeviceSetAction("SetAllVariables", SET_TEST_DATA_ID);
	}
}
