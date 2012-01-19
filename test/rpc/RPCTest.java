package rpc;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
import rpc.exception.ClassRegisteredException;
import rpc.exception.ConditionConflictException;

public class RPCTest {

    private static final Logger LOG = Logger.getLogger(RPCTest.class.getName());
    //
    protected RPCRegistry serverRPCRegistry;
    protected RPCRegistry clientRPCRegistry;
    //
    protected Simulator serverToClientSimulator;
    protected Simulator clientToServerSimulator;
    //
    protected RPC<Integer> serverRPC;
    protected RPC<Integer> clientRPC;
    //
    protected ServerInterface serverInterface;
    protected ServerInterface2 serverInterface2;
    protected ClientInterface clientInterface;
    protected ClientInterface2 clientInterface2;

    public RPCTest() {
    }

    protected static String getClassName() {
        return new Object() {
        }.getClass().getEnclosingClass().getName();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("***** " + getClassName() + " *****");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("******************************\r\n");
    }

    @Before
    public void setUp() throws Throwable {
        serverRPCRegistry = new RPCRegistry();
        clientRPCRegistry = new RPCRegistry();

        // register and bind class
        serverRPCRegistry.registerLocal(ServerInterface.class);
        serverRPCRegistry.registerLocal(ServerInterface2.class);
        serverRPCRegistry.registerRemote(ClientInterface.class);
        serverRPCRegistry.registerRemote(ClientInterface2.class);
        serverRPC = serverRPCRegistry.getRPC(Integer.class);
        serverRPC.bind(ServerInterface.class, new ServerInterfaceImplementation());
        serverRPC.bind(ServerInterface2.class, new ServerInterface2Implementation());
        serverRPC.setUserObject(10);

        clientRPCRegistry.registerRemote(ServerInterface.class);
        clientRPCRegistry.registerRemote(ServerInterface2.class);
        clientRPCRegistry.registerLocal(ClientInterface.class);
        clientRPCRegistry.registerLocal(ClientInterface2.class);
        clientRPC = clientRPCRegistry.getRPC(Integer.class);
        clientRPC.bind(ClientInterface.class, new ClientInterfaceImplementation());
        clientRPC.bind(ClientInterface2.class, new ClientInterface2Implementation());

        // direct the output to correct RPC
        serverToClientSimulator = new Simulator(serverRPC);
        clientToServerSimulator = new Simulator(clientRPC);
        serverToClientSimulator.setRemoteRPC(clientToServerSimulator);
        clientToServerSimulator.setRemoteRPC(serverToClientSimulator);
        serverRPC.setRemoteOutput(serverToClientSimulator);
        clientRPC.setRemoteOutput(clientToServerSimulator);

        // get the remote object
        serverInterface = clientRPC.getRemote(ServerInterface.class);
        serverInterface2 = clientRPC.getRemote(ServerInterface2.class);
        clientInterface = serverRPC.getRemote(ClientInterface.class);
        clientInterface2 = serverRPC.getRemote(ClientInterface2.class);
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
    public void conditionConflictTest() throws Throwable {
        System.out.println("+++++ conditionConflictTest +++++");

        RPCRegistry registry = null;
        AtomicBoolean exceptionCaught = new AtomicBoolean(false);

        exceptionCaught.set(false);
        registry = null;
        try {
            registry = new RPCRegistry();
            registry.registerLocal(ClientInterface.class);
            registry.registerLocal(ClientInterface.class);
        } catch (ClassRegisteredException ex) {
            exceptionCaught.set(true);
        } finally {
            if (registry != null) {
                registry.stop();
            }
        }
        assertTrue(exceptionCaught.get());

        for (int i = 1; i <= 14; i++) {
            Class<?> testInterface = Class.forName(String.format("rpc.RPCTestPackage.ConditionConflictTest_Interface%1$d", i));
            exceptionCaught.set(false);
            registry = null;
            try {
                registry = new RPCRegistry();
                registry.registerLocal(testInterface);
            } catch (ConditionConflictException ex) {
                exceptionCaught.set(true);
            } finally {
                if (registry != null) {
                    registry.stop();
                }
            }
            assertTrue(exceptionCaught.get());
        }
    }

    @Test
    public void registerClassTest() throws Throwable {
        System.out.println("+++++ registerClassTest +++++");

        AtomicBoolean exceptionCaught = new AtomicBoolean(false);

        RPCRegistry _serverRPCRegistry = null;
        RPCRegistry _clientRPCRegistry = null;
        Simulator _serverToClientSimulator = null;
        Simulator _clientToServerSimulator = null;
        try {
            _serverRPCRegistry = new RPCRegistry();
            _clientRPCRegistry = new RPCRegistry();

            _serverRPCRegistry.registerLocal(ServerInterface.class);
            _serverRPCRegistry.registerRemote(ClientInterface.class);
            RPC<Integer> serverRPC = _serverRPCRegistry.getRPC(Integer.class);

            _clientRPCRegistry.registerRemote(ServerInterface.class);
            RPC<Integer> clientRPC = _clientRPCRegistry.getRPC(Integer.class);


            // direct the output to correct RPC
            _serverToClientSimulator = new Simulator(serverRPC);
            _clientToServerSimulator = new Simulator(clientRPC);
            _serverToClientSimulator.setRemoteRPC(_clientToServerSimulator);
            _clientToServerSimulator.setRemoteRPC(_serverToClientSimulator);
            serverRPC.setRemoteOutput(_serverToClientSimulator);
            clientRPC.setRemoteOutput(_clientToServerSimulator);


            // get the remote object
            ServerInterface serverInterface = clientRPC.getRemote(ServerInterface.class);
            ClientInterface clientInterface = serverRPC.getRemote(ClientInterface.class);


            assertNotNull(clientInterface);
            assertTrue(clientInterface instanceof ClientInterface);

            assertNull(serverRPC.getRemote(ServerInterface.class));
            assertNull(serverRPC.getRemote(ClientInterface2.class));


            TestSuite.suppressErrorOutput();

            exceptionCaught.set(false);
            try {
                clientInterface.test();
            } catch (IOException ex) {
                // REMOTE_CONNECTION_METHOD_NOT_REGISTERED
                exceptionCaught.set(true);
            }
            assertTrue(exceptionCaught.get());

            exceptionCaught.set(false);
            try {
                serverInterface.test();
            } catch (IOException ex) {
                // REMOTE_CONNECTION_METHOD_INSTANCE_NOT_REGISTERED
                exceptionCaught.set(true);
            }
            assertTrue(exceptionCaught.get());

            TestSuite.restoreErrorOutput();
        } finally {
            if (_serverToClientSimulator != null) {
                _serverToClientSimulator.stop();
            }
            if (_clientToServerSimulator != null) {
                _clientToServerSimulator.stop();
            }
            if (_serverRPCRegistry != null) {
                _serverRPCRegistry.stop();
            }
            if (_clientRPCRegistry != null) {
                _clientRPCRegistry.stop();
            }
        }
    }

    @Test
    public void pressureTest() throws Throwable {
        System.out.println("+++++ pressureTest +++++");

//- regular send respondedId and remove requests from requestList (and for sequential also)
//- annotations

        Thread thread1 = new Thread(new Runnable() {

            @Override
            public void run() {
                for (int i = 0; i < 100000; i++) {
                    List<Object> list = serverInterface.eval();
                    assertEquals(1, list.get(0));
                    assertEquals("eval", list.get(1));
                }
            }
        });
        thread1.start();
        Thread thread2 = new Thread(new Runnable() {

            @Override
            public void run() {
                for (int i = 100000, iEnd = i + 100000; i < iEnd; i++) {
                    assertEquals(i, serverInterface.eval(i), 0.0F);
                }
            }
        });
        thread2.start();
        Thread thread3 = new Thread(new Runnable() {

            @Override
            public void run() {
                for (int i = 200000, iEnd = i + 100000; i < iEnd; i++) {
                    assertEquals(i, serverInterface.eval(i), 0.0F);
                }
            }
        });
        thread3.start();
        Thread thread4 = new Thread(new Runnable() {

            @Override
            public void run() {
                for (int i = 300000, iEnd = i + 100000; i < iEnd; i++) {
                    assertEquals(i, serverInterface.eval(i), 0.0F);
                }
            }
        });
        thread4.start();
        Thread thread5 = new Thread(new Runnable() {

            @Override
            public void run() {
                for (int i = 400000, iEnd = i + 100000; i < iEnd; i++) {
                    assertEquals(i, serverInterface.eval(i), 0.0F);
                }
            }
        });
        thread5.start();
        for (int i = 0; i < 100000; i++) {
            List<Object> list = serverInterface.eval();
            assertEquals(1, list.get(0));
            assertEquals("eval", list.get(1));
        }
        thread1.join();
        thread2.join();
        thread3.join();
        thread4.join();
        thread5.join();
    }

    @Test
    public void errorCorrectingTest() throws Throwable {
        System.out.println("+++++ errorCorrectingTest +++++");

        Map<Integer, List<String>> _resultMap = new HashMap<Integer, List<String>>();
        _resultMap.put(0, Arrays.asList(new String[]{"rpc test", "rpc", "test"}));
        ArgumentsAssert.register("ljkihy", new Object[][]{new Object[]{10, _resultMap}, new Object[]{10, _resultMap}});

        ServerInterface serverInterfaceImplementation = new ServerInterfaceImplementation() {

            @Override
            public Double ljkihy(Integer userObject, Map<Integer, List<String>> test) {
                ArgumentsAssert.assertAnyMatch("ljkihy", userObject, test);
                return (double) 1.0F;
            }
        };

        serverRPC.bind(ServerInterface.class, serverInterfaceImplementation);

        serverToClientSimulator.addReceiveError(0, Simulator.ErrorMode.CONTENT, 5);
        clientToServerSimulator.addReceiveError(0, Simulator.ErrorMode.CONTENT, 5);
        serverToClientSimulator.addReceiveError(1, Simulator.ErrorMode.DISCARD, 0);
        clientToServerSimulator.addReceiveError(1, Simulator.ErrorMode.DISCARD, 0);
        serverToClientSimulator.addReceiveError(2, Simulator.ErrorMode.HEAD, 5);
        clientToServerSimulator.addReceiveError(2, Simulator.ErrorMode.HEAD, 5);
        serverToClientSimulator.addReceiveError(3, Simulator.ErrorMode.TAIL, 5);
        clientToServerSimulator.addReceiveError(3, Simulator.ErrorMode.TAIL, 5);


        clientInterface.notifyClient(new Integer[]{10});

        Map<Integer, List<String>> map = new HashMap<Integer, List<String>>();
        map.put(0, Arrays.asList(new String[]{"rpc test", "rpc", "test"}));
        assertEquals(1.0F, serverInterface.ljkihy(0, map), 0F);
        assertEquals(1.0F, serverInterface.ljkihy(0, map), 0F);


        assertTrue(ArgumentsAssert.finish());
    }

    @Test
    public void reuseRequestId_heartBeat() throws Throwable {
        System.out.println("+++++ reuseRequestId_heartBeat +++++");

        // reuse requestId
        clientRPC.requestIdSet.id = 1073741822;
        serverInterface.test();
        assertEquals(1073741823, clientRPC.requestIdSet.id);
        serverInterface.test();
        assertEquals(1, clientRPC.requestIdSet.id);
        for (int i = 0; i < 122; i++) {
            assertEquals(i, serverInterface.eval(i), 0.0F);
        }
        assertEquals(123, clientRPC.requestIdSet.id);

        assertEquals(2, clientRPC.requestList.size());

        // heart beat
        serverRPCRegistry.heartBeatSendInterval = 1000;
        serverRPCRegistry.workerInterval = 1000;
        Thread.sleep(2500); // may set to 2600 which is slightly larger than the default workerInterval (2500)
        assertTrue((System.currentTimeMillis() - clientRPC.lastPacketReceiveTime) < 2000);
    }
}
