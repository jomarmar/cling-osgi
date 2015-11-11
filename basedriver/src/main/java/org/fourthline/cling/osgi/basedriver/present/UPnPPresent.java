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

package org.fourthline.cling.osgi.basedriver.present;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.ValidationException;
import org.fourthline.cling.model.action.ActionExecutor;
import org.fourthline.cling.model.meta.*;
import org.fourthline.cling.model.state.StateVariableAccessor;
import org.fourthline.cling.model.types.*;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.upnp.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Logger;

/**
 * 111.2.1 UPnP Base Driver
 * <p>
 * The functionality of the UPnP service is implemented in a UPnP base driver.
 * This is a bundle that implements the UPnP protocols and handles the interaction
 * with bundles that use the UPnP devices. A UPnP base driver bundle
 * must provide the following functions:
 * </p>
 * <ul>
 * <li>Discover UPnP devices on the network and map each discovered device
 * into an OSGi registered UPnP Device service.</li>
 * <li>Present UPnP marked services that are registered with the OSGi
 * Framework on one or more networks to be used by other computers.
 * </li>
 * </ul>
 * <p>
 * UPnPPresent tracks UPnPDevice services registered for export. When a service
 * is registered/unregistered  UPnPPresent will add/remove it with Cling.
 * </p>
 * <p>
 * When a service changes a state variable that sends events UPnPPresent will
 * send that change to external listeners.
 * </p>
 *
 * @author Bruce Green
 */
@Component (
        immediate = true
)
public class UPnPPresent {

    final private static Logger log = Logger.getLogger(UPnPPresent.class.getName());

    private UpnpService upnpService;
    private Map<UPnPDevice, LocalDevice> registrations = new Hashtable<UPnPDevice, LocalDevice>();

    public UPnPPresent( UpnpService upnpService) {
        this.upnpService = upnpService;
    }

    private Map<Action<LocalService<DataAdapter>>, ActionExecutor> createActionExecutors(UPnPAction[] actions) {
        Map<Action<LocalService<DataAdapter>>, ActionExecutor> executors = new HashMap();

        if (actions != null) {
            for (UPnPAction action : actions) {
                List<ActionArgument<LocalService<DataAdapter>>> list = new ArrayList();

                String[] names;

                names = action.getInputArgumentNames();
                if (names != null) {
                    for (String name : names) {
                        UPnPStateVariable variable = action.getStateVariable(name);
                        ActionArgument<LocalService<DataAdapter>> argument =
                                new ActionArgument<LocalService<DataAdapter>>(
                                        name,
                                        variable.getName(),
                                        ActionArgument.Direction.IN,
                                        false
                                );
                        list.add(argument);
                    }
                }

                names = action.getOutputArgumentNames();
                if (names != null) {
                    for (String name : names) {
                        UPnPStateVariable variable = action.getStateVariable(name);
                        ActionArgument<LocalService<DataAdapter>> argument =
                                new ActionArgument<LocalService<DataAdapter>>(
                                        name,
                                        variable.getName(),
                                        ActionArgument.Direction.OUT,
                                        false
                                );
                        list.add(argument);
                    }
                }

                Action<LocalService<DataAdapter>> local = new Action<LocalService<DataAdapter>>(
                        action.getName(),
                        list.toArray(new ActionArgument[list.size()])
                );

                executors.put(local, new UPnPActionExecutor(action));
            }
        }

        return executors;
    }

    private Map<StateVariable<LocalService<DataAdapter>>, StateVariableAccessor> createStateVariableAccessors(UPnPStateVariable[] variables) {
        Map<StateVariable<LocalService<DataAdapter>>, StateVariableAccessor> map = new HashMap();

        if (variables != null) {
            for (UPnPStateVariable variable : variables) {

                Datatype<?> dataType = Datatype.Builtin.getByDescriptorName(variable.getUPnPDataType()).getDatatype();
                StateVariable<LocalService<DataAdapter>> local = new StateVariable<LocalService<DataAdapter>>(
                        variable.getName(),
                        new StateVariableTypeDetails(dataType),
                        new StateVariableEventDetails(variable.sendsEvents())
                );
                if (variable instanceof UPnPLocalStateVariable) {
                    map.put(local, new UPnPLocalStateVariableAccessor((UPnPLocalStateVariable) variable));
                } else {
                    map.put(local, new UPnPStateVariableAccessor(variable));
                }
            }
        }

        return map;

    }

    private Set<Class<?>> createStringConvertibleTypes() {
        Set<Class<?>> set = new HashSet<Class<?>>();

        set.add(Boolean.class);
        set.add(Byte.class);
        set.add(Integer.class);
        set.add(Long.class);
        set.add(Float.class);
        set.add(Double.class);
        set.add(Character.class);
        set.add(String.class);
        set.add(Date.class);

        return set;
    }

    private LocalService<DataAdapter>[] createServices(UPnPService[] services) throws InvalidValueException, ValidationException {
        List<LocalService<DataAdapter>> list = null;

        if (services != null) {
            list = new ArrayList<LocalService<DataAdapter>>();
            for (UPnPService service : services) {
                UPnPLocalServiceImpl<DataAdapter> local =
                        new UPnPLocalServiceImpl<DataAdapter>(
                                ServiceType.valueOf(service.getType()),
                                ServiceId.valueOf(service.getId()),
                                (Map) createActionExecutors(service.getActions()),
                                (Map) createStateVariableAccessors(service.getStateVariables()),
                                (Set) createStringConvertibleTypes(),
                                false
                        );

                //local.setManager(new UPnPServiceManager<DataAdapter>(local));
                local.setManager(new DefaultServiceManager<DataAdapter>(local, DataAdapter.class));

                list.add(local);
            }
        }

        return (list != null) ? list.toArray(new LocalService[list.size()]) : null;
    }

    private Icon[] createIcons(UPnPIcon[] icons) throws IOException, URISyntaxException {
        List<Icon> list = null;

        if (icons != null) {
            list = new ArrayList<Icon>();
            for (UPnPIcon icon : icons) {
                InputStream in = icon.getInputStream();
                if (in != null) {
                    Icon local =
                            new Icon(icon.getMimeType(),
                                    icon.getWidth(),
                                    icon.getHeight(),
                                    icon.getDepth(),
                                    UUID.randomUUID().toString(),
                                    in
                            );
                    list.add(local);
                }
            }
        }

        return (list != null) ? list.toArray(new Icon[list.size()]) : null;
    }


    private String getSafeString(Object object) {
        return (object != null) ? object.toString() : null;
    }

    private URI getSafeURI(Object object) {
        return (object != null) ? URI.create(object.toString()) : null;
    }

    private LocalDevice createDevice(UPnPDevice in) throws ValidationException, IOException, URISyntaxException {
        Dictionary<?, ?> descriptions = in.getDescriptions(null);
        DeviceIdentity identity =
                new DeviceIdentity(
                        new UDN(getSafeString(descriptions.get(UPnPDevice.UDN)))
                );

        DeviceType type =
                DeviceType.valueOf(getSafeString(descriptions.get(UPnPDevice.TYPE)));

        DeviceDetails details =
                new DeviceDetails(
                        getSafeString(descriptions.get(UPnPDevice.FRIENDLY_NAME)),
                        new ManufacturerDetails(
                                getSafeString(descriptions.get(UPnPDevice.MANUFACTURER)),
                                getSafeURI(descriptions.get(UPnPDevice.MANUFACTURER_URL))
                        ),
                        new ModelDetails(
                                getSafeString(descriptions.get(UPnPDevice.MODEL_NAME)),
                                getSafeString(descriptions.get(UPnPDevice.MODEL_DESCRIPTION)),
                                getSafeString(descriptions.get(UPnPDevice.MODEL_NUMBER)),
                                getSafeURI(descriptions.get(UPnPDevice.MODEL_URL))
                        ),
                        getSafeString(descriptions.get(UPnPDevice.SERIAL_NUMBER)),
                        getSafeString(descriptions.get(UPnPDevice.UPC)),
                        getSafeURI(descriptions.get(UPnPDevice.PRESENTATION_URL))
                );

        Icon[] icons = createIcons(in.getIcons(null));

        LocalService<DataAdapter>[] services = createServices(in.getServices());

        return new LocalDevice(identity, type, details, icons, services);
    }


    @Reference(name = "upnpDevice", service = UPnPDevice.class,
            cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.STATIC,
            target = "("+UPnPDevice.UPNP_EXPORT+"=*)"
    )
    public void bindUPnPDevice(UPnPDevice dev) {
        log.finer(dev.toString());

        try {
            LocalDevice local = createDevice(dev);
            if (local != null) {
                upnpService.getRegistry().addDevice(local);
                registrations.put(dev, local);
            }

        } catch (ValidationException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public void unbindUPnPDevice(UPnPDevice dev) {
        log.entering(this.getClass().getName(), "removedService", new Object[]{ dev});

        LocalDevice local = registrations.get(dev);
        if (local != null) {
            upnpService.getRegistry().removeDevice(local);
            registrations.remove(dev);
        }
    }
}
