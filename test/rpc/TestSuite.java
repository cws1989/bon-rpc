package rpc;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    rpc.CodecTest.class,
    rpc.PacketizerTest.class,
    rpc.RPCTest.class
})
public class TestSuite {
}
