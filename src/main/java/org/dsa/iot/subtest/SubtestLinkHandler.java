package org.dsa.iot.subtest;

import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.DSLinkHandler;
import org.dsa.iot.dslink.node.Node;

/**
 * @author Aaron Hansen
 */
public class SubtestLinkHandler extends DSLinkHandler {

    ///////////////////////////////////////////////////////////////////////////
    // Instance Fields
    ///////////////////////////////////////////////////////////////////////////

    private Subtest subtest;
    private boolean requesterConnected;
    private DSLink requesterLink;
    private boolean responderConnected;
    private DSLink responderLink;
    private Node superRoot;

    ///////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isRequester() {
        return true;
    }

    @Override
    public boolean isResponder() {
        return true;
    }

    @Override
    public void onRequesterConnected(DSLink link) {
        requesterLink = link;
        requesterConnected = true;
        if (responderConnected) {
            subtest.start();
        }
    }

    @Override
    public void onRequesterDisconnected(DSLink link) {
        requesterLink = null;
    }

    @Override
    public void onResponderConnected(DSLink link) {
        responderLink = link;
        Node superRoot = responderLink.getNodeManager().getSuperRoot();
        if (superRoot != this.superRoot) {
            if (subtest != null) {
                subtest.stop();
                subtest = null;
            }
            this.superRoot = superRoot;
            Node serviceNode = superRoot.createChild("main", false)
                                        .setSerializable(true)
                                        .build();
            if (subtest == null) {
                subtest = new Subtest();
            }
            subtest.init(this, serviceNode);
        }
        responderConnected = true;
        if (requesterConnected) {
            subtest.start();
        }
    }

    @Override
    public void onResponderDisconnected(DSLink link) {
        responderLink = null;
    }

    @Override
    public void stop() {
        if (subtest != null) {
            subtest.stop();
        }
        super.stop();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Package / Private Methods
    ///////////////////////////////////////////////////////////////////////////

    DSLink getRequesterLink() {
        return requesterLink;
    }

    DSLink getResponderLink() {
        return responderLink;
    }

}
