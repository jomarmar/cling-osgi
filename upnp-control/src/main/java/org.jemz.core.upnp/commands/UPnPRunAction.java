package org.jemz.core.upnp.commands;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
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
@Command(scope = "upnp", name = "run", description="Run action on UPnPDevice")
@Service
@Component(
        immediate = true
)
public class UPnPRunAction implements Action {

    private static IUPnPControl upnpControl;
    @Argument(index = 0, name = "deviceUDN", description = "Device UDN.", required = true, multiValued = false)
    private String deviceUDN;

    @Argument(index = 1, name = "serviceID", description = "Service ID.", required = true, multiValued = false)
    private String serviceId;

    @Argument(index = 2, name = "actionName", description = "Action Name.", required = true, multiValued = false)
    private String actionName;

    @Argument(index = 3, name = "parameters", description = "Parameter values.", required = false, multiValued = true)
    private String[] params;



    @Activate
    public void startup() {

    }

    @Deactivate
    public void shutdown() {

    }


    @Override
    public Object execute() throws Exception {

        System.out.println("Running action: " + actionName + "@"+deviceUDN+"["+serviceId+"]");
        System.out.println("\tParams: ");
        for(String p : params ) {
            System.out.println("\t\t" + p);
        }


        for(UPnPDevice dev : upnpControl.getDeviceList()) {
            String devUDN = (String) dev.getDescriptions(null).get(UPnPDevice.UDN);
            if(!deviceUDN.startsWith("uuid:")) {
                deviceUDN = "uuid:"+deviceUDN;
            }

            if(devUDN.equals(deviceUDN)) {

                for(UPnPService s : dev.getServices()) {
                    if(s.getId().equals(serviceId)) {

                        UPnPAction action = s.getAction(actionName);
                        if(action == null) {
                            System.out.println("ERROR: Action " + actionName + " not found for deviceId: " + deviceUDN);
                            return null;
                        } else {
                            String[] args = action.getInputArgumentNames();
                            for(int i=0;i<args.length;i++) {
                                UPnPStateVariable stateVar = action.getStateVariable(args[i]);
                                stateVar.getJavaDataType();
                            }
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
