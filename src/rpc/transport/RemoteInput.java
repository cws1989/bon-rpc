package rpc.transport;

import java.io.IOException;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public interface RemoteInput {

    void feed(byte[] b) throws IOException;
}
