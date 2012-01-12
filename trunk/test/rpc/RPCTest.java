package rpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import rpc.RPCTestPackage.LocalInterface;
import rpc.RPCTestPackage.RemoteInterface;
import rpc.RPCTestPackage.RemoteInterface2;
import rpc.transport.RemoteOutput;

public class RPCTest {

    public RPCTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void test() throws Exception, Throwable {
//        TestRunnable tr1 = new TestRunnable() {
//
//            @Override
//            public void runTest() throws Throwable {
//                this.delay(1000);
//                assertTrue(false);
//            }
//        }, tr2 = new TestRunnable() {
//
//            @Override
//            public void runTest() throws Throwable {
//                assertTrue(false);
//            }
//        }, tr3 = new TestRunnable() {
//
//            @Override
//            public void runTest() throws Throwable {
//                assertTrue(false);
//            }
//        };
//
//        //pass that instance to the MTTR
//        TestRunnable[] trs = {tr1, tr2, tr3};
//        MultiThreadedTestRunner mttr = new MultiThreadedTestRunner(trs);
//
//        //kickstarts the MTTR & fires off threads
//        mttr.runTestRunnables();

        RPCRegistry rpcRegistry = new RPCRegistry();

        rpcRegistry.registerLocal(LocalInterface.class);
        rpcRegistry.registerRemote(RemoteInterface.class);
        rpcRegistry.registerRemote(RemoteInterface2.class);
        final RPC localRPC = rpcRegistry.getRPC();

        rpcRegistry.clear();
        rpcRegistry.registerRemote(LocalInterface.class);
        rpcRegistry.registerLocal(RemoteInterface.class);
        rpcRegistry.registerLocal(RemoteInterface2.class);
        final RPC remoteRPC = rpcRegistry.getRPC();
        remoteRPC.setUserObject(10);

        RemoteOutput localToRemote = new RemoteOutput() {

            @Override
            public void write(final byte[] b) throws IOException {
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            remoteRPC.feed(b, 0, b.length);
                        } catch (IOException ex) {
                            Logger.getLogger(RPCTest.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }).start();
            }
        };
        RemoteOutput remoteToLocal = new RemoteOutput() {

            @Override
            public void write(final byte[] b) throws IOException {
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            localRPC.feed(b, 0, b.length);
                        } catch (IOException ex) {
                            Logger.getLogger(RPCTest.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }).start();
            }
        };
        localRPC.setRemoteOutput(localToRemote);
        remoteRPC.setRemoteOutput(remoteToLocal);

        localRPC.bind(LocalInterface.class, new LocalInterface() {

            @Override
            public void notifyClient(Integer[] broadcastList) {
                System.out.println("broadcast received");
            }
        });
        remoteRPC.bind(RemoteInterface.class, new RemoteInterface() {

            @Override
            public Double ljkihy(int userObject, Map<Integer, List<String>> test) {
                System.out.println("1, userObject: " + userObject);
                List<String> list = test.get(0);
                System.out.println(list.get(0));
                System.out.println(list.get(1));
                System.out.println(list.get(2));
                return (double) 1F;
            }

            @Override
            public Double get(int x) {
                System.out.println("2");
                return null;
            }

            @Override
            public Double eval(double x) {
                System.out.println("3");
                return x;
            }

            @Override
            public void eval() {
                System.out.println("void void");
            }
        });
        remoteRPC.bind(RemoteInterface2.class, new RemoteInterface2() {

            @Override
            public void abc() {
                System.out.println("4");
            }
        });

        remoteRPC.getRemote(LocalInterface.class).notifyClient(new Integer[]{10});

        RemoteInterface serverIntl = localRPC.getRemote(RemoteInterface.class);
        System.out.println("reply: " + serverIntl.eval(111F));
        System.out.println("reply: " + serverIntl.eval(112F));
        List<String> list = new ArrayList<String>();
        list.add("rpc test");
        list.add("rpc");
        list.add("test");
        Map<Integer, List<String>> map = new HashMap<Integer, List<String>>();
        map.put(0, list);
        serverIntl.ljkihy(0, map);
        serverIntl.get(1);
        serverIntl.eval();
        System.out.println("reply: " + serverIntl.eval(112F));
        RemoteInterface2 serverIntl2 = localRPC.getRemote(RemoteInterface2.class);
        serverIntl2.abc();

        assertTrue(true);
    }
}
