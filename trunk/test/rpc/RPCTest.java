package rpc;

import java.util.HashMap;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import rpc.RPCTestPackage.LocalInterface;
import rpc.RPCTestPackage.RemoteInterface;
import rpc.RPCTestPackage.RemoteInterface2;
import static org.junit.Assert.*;

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
    public void test() throws Exception {
        // not implemented yet

        RPCRegistry rpcRegistry = new RPCRegistry();
        rpcRegistry.registerLocal(LocalInterface.class);
        rpcRegistry.registerRemote(RemoteInterface.class);
        rpcRegistry.registerRemote(RemoteInterface2.class);

        RPC rpc = rpcRegistry.getRPC();
        RemoteInterface serverIntl = rpc.getRemote(RemoteInterface.class);
        serverIntl.abc(new HashMap<List<Object>, List<Object>>(), 1F, 1, 1L);

        assertTrue(true);
    }
}
