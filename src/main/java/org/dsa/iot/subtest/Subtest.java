package org.dsa.iot.subtest;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.Writable;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.SubscriptionValue;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.SubData;
import org.dsa.iot.dslink.util.TimeUtils;
import org.dsa.iot.dslink.util.handler.Handler;

/**
 * @author Aaron Hansen
 */
public class Subtest {

    ///////////////////////////////////////////////////////////////////////////
    // Class Fields
    ///////////////////////////////////////////////////////////////////////////

    private static final String DEBUG = "debug";
    private static final String INTERVAL = "interval-millis";
    private static final String LOST = "lost";
    private static final String OUT_OF_ORDER = "out-of-order";
    private static final String PATH = "test-path";
    private static final String QOS = "qos";
    private static final String RUNNING = "running";
    private static final String SIZE = "size";
    private static final String SKIPPED = "skipped";
    private static final String START = "start";
    private static final String START_TS = "startTime";
    private static final String VALUE = "value";

    ///////////////////////////////////////////////////////////////////////////
    // Instance Fields
    ///////////////////////////////////////////////////////////////////////////

    private SubtestLinkHandler linkHandler;
    private Node mainNode;
    private Node valueNode;
    private boolean running = false;

    ///////////////////////////////////////////////////////////////////////////
    // Package / Private Methods
    ///////////////////////////////////////////////////////////////////////////

    void init(SubtestLinkHandler linkHandler, Node node) {
        this.linkHandler = linkHandler;
        this.mainNode = node;
        initActions();
        initData();
    }

    void initActions() {
        Action action = new Action(Permission.WRITE, new Handler<ActionResult>() {
            @Override
            public void handle(ActionResult event) {
                startTest(event);
            }
        });
        action.addParameter(new Parameter(SIZE, ValueType.NUMBER, new Value(10000)));
        action.addParameter(new Parameter(INTERVAL, ValueType.NUMBER, new Value(0)));
        action.addParameter(new Parameter(QOS, ValueType.NUMBER, new Value(1)));
        mainNode.createChild(START, false)
                .setSerializable(false)
                .setAction(action)
                .build();
    }

    void initData() {
        initProperty(DEBUG, null, new Value(false))
                .createFakeBuilder()
                .setSerializable(true)
                .setWritable(Writable.WRITE);
        initProperty(RUNNING, null, new Value(false));
        initProperty(PATH, null, new Value(""));
        initProperty(SIZE, null, new Value(0));
        initProperty(INTERVAL, null, new Value(0));
        initProperty(QOS, null, new Value(1));
        initProperty(START_TS, null, new Value(""));
        initProperty(SKIPPED, null, new Value(0));
        initProperty(OUT_OF_ORDER, null, new Value(0));
        initProperty(LOST, null, new Value(0));
        Node valueParent = mainNode
                .createChild("do-not-view", false)
                .setSerializable(false)
                .build();
        valueNode = initProperty(valueParent, VALUE, null, new Value(-1));
    }

    Node initProperty(String name, ValueType type, Value value) {
        return initProperty(mainNode, name, type, value);
    }

    Node initProperty(Node parent, String name, ValueType type, Value value) {
        Node child = parent.getChild(name, false);
        if (child == null) {
            child = parent.createChild(name, false).setSerializable(false).build();
            if (type != null) {
                child.setValueType(type);
            } else {
                child.setValueType(value.getType());
            }
            child.setWritable(Writable.NEVER);
        }
        child.setValue(value);
        child.setHasChildren(false);
        return child;
    }

    void start() {
        System.out.println("Subtest ready.");
    }

    void startTest(ActionResult result) {
        synchronized (this) {
            if (running) {
                throw new IllegalStateException("Already running");
            }
            running = true;
        }
        try {
            int size = result.getParameter(SIZE).getNumber().intValue();
            mainNode.getChild(SIZE, false).setValue(new Value(size));
            int interval = result.getParameter(INTERVAL).getNumber().intValue();
            mainNode.getChild(INTERVAL, false).setValue(new Value(interval));
            int qos = result.getParameter(QOS).getNumber().intValue();
            mainNode.getChild(QOS, false).setValue(new Value(qos));
            new Subscriber(size, interval, qos).start();
        } catch (Exception x) {
            running = false;
            throw new RuntimeException(x);
        }
    }

    void stop() {
    }

    private class Publisher extends Thread {

        private int interval;
        private int size;

        Publisher(int size, int interval) {
            this.interval = interval;
            this.size = size;
        }

        public void run() {
            try {
                for (int i = 0; i < size; i++) {
                    valueNode.setValue(new Value(i));
                    if (interval > 0) {
                        Thread.sleep(interval);
                    }
                }
            } catch (Exception x) {
                x.printStackTrace();
            }
        }
    }

    private class Subscriber extends Thread implements Handler<SubscriptionValue> {

        private boolean debug;
        private int interval;
        private long lastTs = -1;
        private int lastValue = -1;
        private int outOrder = 0;
        private int qos;
        private int size;
        private int skipped = 0;

        Subscriber(int size, int interval, int qos) {
            this.interval = interval;
            this.size = size;
            this.qos = qos;
        }

        public synchronized void handle(SubscriptionValue value) {
            int val = value.getValue().getNumber().intValue();
            if (debug) {
                System.out.println(val);
            }
            lastTs = System.currentTimeMillis();
            if (val >= 0) {
                if (val > lastValue) {
                    if (val != (lastValue + 1)) {
                        skipped += val - (lastValue + 1);
                        System.out.println("-Skip: " + val);
                    }
                    lastValue = val;
                } else {
                    outOrder++;
                    System.out.println("-Out of order: " + val);
                }
            }
            notify();
        }

        public void run() {
            String path = null;
            long time = 0;
            try {
                debug = mainNode.getChild(DEBUG, false).getValue().getBool();
                valueNode.setValue(new Value(-1));
                mainNode.getChild(RUNNING, false).setValue(new Value(true));
                //time
                time = System.currentTimeMillis();
                String ts = TimeUtils.encode(time, false).toString();
                mainNode.getChild(START_TS, false).setValue(new Value(ts));
                System.out.println("\n***Start at " + ts);
                //path
                path = linkHandler.getResponderLink().getPath() + valueNode.getPath();
                mainNode.getChild(PATH, false).setValue(new Value(path));
                setStats();
                linkHandler.getRequesterLink().getRequester()
                           .subscribe(new SubData(path, qos), this);
                Thread.sleep(1000); //wait for subscription
                time = System.currentTimeMillis();
                lastTs = time;
                new Publisher(size, interval).start();
                waitForValue(size - 1, Math.max(10000, interval * 10));
                setStats();
                printStats();
            } catch (Exception x) {
                x.printStackTrace();
            } finally {
                linkHandler.getRequesterLink().getRequester().unsubscribe(path, null);
                mainNode.getChild(RUNNING, false).setValue(new Value(false));
                running = false;
                valueNode.setValue(new Value(-1));
            }
            System.out.println("***Finished in " + (System.currentTimeMillis() - time) + "ms");
        }

        void printStats() {
            System.out.println("Skipped: " + skipped);
            System.out.println("Out Of Order: " + outOrder);
            System.out.println("Total Lost: " + (skipped - outOrder));
        }

        void setStats() {
            mainNode.getChild(LOST, false).setValue(new Value(skipped - outOrder));
            mainNode.getChild(SKIPPED, false).setValue(new Value(skipped));
            mainNode.getChild(OUT_OF_ORDER, false).setValue(new Value(outOrder));
        }

        synchronized void waitForValue(int value, int timeout) {
            while (lastValue != value) {
                try {
                    wait(1000);
                } catch (Exception x) {
                    x.printStackTrace();
                }
                if ((System.currentTimeMillis() - lastTs) > timeout) {
                    throw new IllegalStateException("Test timed out");
                }
            }
        }
    }

}
