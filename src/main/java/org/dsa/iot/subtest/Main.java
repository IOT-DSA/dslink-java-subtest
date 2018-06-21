package org.dsa.iot.subtest;

import org.dsa.iot.dslink.DSLinkFactory;

/**
 * @author Aaron Hansen
 */
public class Main extends SubtestLinkHandler {

    /**
     * Command line bootstrap.
     *
     * @param args Should supply --broker host/conn
     */
    public static void main(String[] args) {
        DSLinkFactory.start(args, new Main());
    }

}


