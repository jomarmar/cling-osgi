package org.jemz.core.upnp.commands;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jemz.core.upnp.IUPnPControl;
import org.osgi.service.component.annotations.*;
import org.osgi.service.upnp.UPnPAction;
import org.osgi.service.upnp.UPnPDevice;
import org.osgi.service.upnp.UPnPService;
import org.osgi.service.upnp.UPnPStateVariable;

/**
 * Created by jmartinez on 12/8/15.
 */
@Command(scope = "upnp", name = "list-services", description="Lists UPnP devices")
@Service
@Component(
        immediate = true
)
public class UPnPServices implements Action {

    private static IUPnPControl upnpControl;
    @Argument(index = 0, name = "deviceUDN", description = "Device UDN.", required = true, multiValued = false)
    private String deviceUDN;


    @Option(name = "-a", description = "Shows service actions", required = false, multiValued = false)
    boolean showActions;

    @Option(name = "-v", description = "Shows service state variables", required = false, multiValued = false)
    boolean showStateVariables;



    @Activate
    public void startup() {

    }

    @Deactivate
    public void shutdown() {

    }


    @Override
    public Object execute() throws Exception {

        for(UPnPDevice dev : upnpControl.getDeviceList()) {
            String devUDN = (String) dev.getDescriptions(null).get(UPnPDevice.UDN);
            if(!deviceUDN.startsWith("uuid:")) {
                deviceUDN = "uuid:"+deviceUDN;
            }

            if(devUDN.equals(deviceUDN)) {

                for(UPnPService service : dev.getServices()) {
                    System.out.println("SERVICE: " + service.getId() + " [" + service.getVersion() + "]");
                    System.out.println("  TYPE: " + service.getType());
                    if(showActions) {
                        System.out.println("  ACTIONS: ");
                        for (UPnPAction action : service.getActions()) {
                            System.out.println("\t" + action.getName());
                        }
                    }

                    if(showStateVariables) {
                        System.out.println("  STATE VARIABLES: ");
                        for (UPnPStateVariable var : service.getStateVariables()) {
                            System.out.println("\t" + var.getName() + "[" + var.getUPnPDataType() + "] (" + var.getJavaDataType() + ")");
                        }
                    }

                }
                return null;
            }
        }

        System.out.println("ERROR: Device with UDN: " + deviceUDN + " not found.");


        return null;
    }

    @Reference( service = IUPnPControl.class,
                policy = ReferencePolicy.DYNAMIC,
                cardinality = ReferenceCardinality.MANDATORY)
    public void bindUPnPControl(IUPnPControl ctrl) {
        upnpControl = ctrl;
    }

    public void unbindUPnPControl(IUPnPControl ctrl) {
        upnpControl = null;
    }
}
