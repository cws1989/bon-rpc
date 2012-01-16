package rpc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
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

    protected static PrintStream errorStream;

    public synchronized static void suppressErrorOutput() {
        if (errorStream == null) {
            errorStream = System.err;
            System.setErr(new PrintStream(new OutputStream() {

                @Override
                public void write(int b) throws IOException {
                }
            }));
        }
    }

    public synchronized static void restoreErrorOutput() {
        if (errorStream != null) {
            System.setErr(errorStream);
            errorStream = null;
        }
    }
}
