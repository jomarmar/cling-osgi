package org.fourthline.cling.osgi.basedriver;

import org.fourthline.cling.controlpoint.SubscriptionCallback;
import org.fourthline.cling.model.gena.CancelReason;
import org.fourthline.cling.model.gena.GENASubscription;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.Service;

/**
 * Created by jmartinez on 12/11/15.
 */
public class TestSubscriptionCallback extends SubscriptionCallback {

    public TestSubscriptionCallback(Service service) {
        super(service);
    }

    @Override
    protected void failed(GENASubscription subscription, UpnpResponse responseStatus, Exception exception, String defaultMsg) {
        System.out.println("FAILED: " + exception.toString());
    }

    @Override
    protected void established(GENASubscription subscription) {
        System.out.println("ESTABLISHED");
    }

    @Override
    protected void ended(GENASubscription subscription, CancelReason reason, UpnpResponse responseStatus) {
        System.out.println("ENDED");
    }

    @Override
    protected void eventReceived(GENASubscription subscription) {
        System.out.println("EVENT: " + subscription.toString());
    }

    @Override
    protected void eventsMissed(GENASubscription subscription, int numberOfMissedEvents) {
        System.out.println("EVENTMISSED: " + numberOfMissedEvents);
    }
}
