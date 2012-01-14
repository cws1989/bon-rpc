package rpc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import rpc.RPCTestPackage.ServerInterface;
import rpc.RPCTestPackage.ServerInterface2;
import rpc.RPCTestPackage.ServerInterface2Implementation;
import rpc.RPCTestPackage.ServerInterfaceImplementation;
import rpc.RPCTestPackage.ClientInterface;
import rpc.RPCTestPackage.ClientInterface2;
import rpc.RPCTestPackage.ClientInterface2Implementation;
import rpc.RPCTestPackage.ClientInterfaceImplementation;

public class RPCTest {

    private static final Logger LOG = Logger.getLogger(RPCTest.class.getName());
    protected RPCRegistry serverRPCRegistry;
    protected RPCRegistry clientRPCRegistry;
    protected Simulator serverToClientSimulator;
    protected Simulator clientToServerSimulator;

    public RPCTest() {
    }

    protected static String getClassName() {
        return new Object() {
        }.getClass().getEnclosingClass().getName();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("***** " + getClassName() + " *****");
        Map<Integer, List<String>> map = new HashMap<Integer, List<String>>();
        map.put(0, Arrays.asList(new String[]{"rpc test", "rpc", "test"}));
        ArgumentsAssert.register("ljkihy", new Object[][]{new Object[]{10, map}, new Object[]{10, map}});
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        ArgumentsAssert.finish();
        System.out.println("******************************\r\n");
    }

    @Before
    public void setUp() {
        serverRPCRegistry = new RPCRegistry();
        clientRPCRegistry = new RPCRegistry();
        serverToClientSimulator = null;
        clientToServerSimulator = null;
    }

    @After
    public void tearDown() {
        if (serverToClientSimulator != null) {
            serverToClientSimulator.stop();
        }
        if (clientToServerSimulator != null) {
            clientToServerSimulator.stop();
        }
        if (serverRPCRegistry != null) {
            serverRPCRegistry.stop();
        }
        if (clientRPCRegistry != null) {
            clientRPCRegistry.stop();
        }
    }

    @Test
    public void test() throws Throwable {
        ServerInterface serverInterfaceImplementation = new ServerInterfaceImplementation() {

            @Override
            public Double ljkihy(int userObject, Map<Integer, List<String>> test) {
                ArgumentsAssert.assertMatch("ljkihy", userObject, test);
                return (double) 1.0F;
            }
        };

        serverRPCRegistry.registerLocal(ServerInterface.class);
        serverRPCRegistry.registerLocal(ServerInterface2.class);
        serverRPCRegistry.registerRemote(ClientInterface.class);
        serverRPCRegistry.registerRemote(ClientInterface2.class);
        RPC serverRPC = serverRPCRegistry.getRPC();
        serverRPC.bind(ServerInterface.class, serverInterfaceImplementation);
        serverRPC.bind(ServerInterface2.class, new ServerInterface2Implementation());
        serverRPC.setUserObject(10);

        clientRPCRegistry.registerRemote(ServerInterface.class);
        clientRPCRegistry.registerRemote(ServerInterface2.class);
        clientRPCRegistry.registerLocal(ClientInterface.class);
        clientRPCRegistry.registerLocal(ClientInterface2.class);
        RPC clientRPC = clientRPCRegistry.getRPC();
        clientRPC.bind(ClientInterface.class, new ClientInterfaceImplementation());
        clientRPC.bind(ClientInterface2.class, new ClientInterface2Implementation());


        // direct the output to correct RPC
        serverToClientSimulator = new Simulator(serverRPC, clientRPC);
        clientToServerSimulator = new Simulator(clientRPC, serverRPC);
        serverRPC.setRemoteOutput(serverToClientSimulator);
        clientRPC.setRemoteOutput(clientToServerSimulator);


        // get the remote object
        ServerInterface serverInterface = clientRPC.getRemote(ServerInterface.class);
        ServerInterface2 serverInterface2 = clientRPC.getRemote(ServerInterface2.class);
        ClientInterface clientInterface = serverRPC.getRemote(ClientInterface.class);
        ClientInterface2 clientInterface2 = serverRPC.getRemote(ClientInterface2.class);


        // remote procedure call start
        clientInterface.notifyClient(new Integer[]{10});

        Map<Integer, List<String>> map = new HashMap<Integer, List<String>>();
        map.put(0, Arrays.asList(new String[]{"r1pc test", "rpc", "test"}));
        serverInterface.ljkihy(0, map);
        serverInterface.ljkihy(0, map);
        serverInterface.get(1);
        serverInterface.eval();
        serverInterface2.abc();

        assertTrue(true);
    }
}
