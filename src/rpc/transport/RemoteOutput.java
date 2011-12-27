package rpc.transport;

import java.io.IOException;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public interface RemoteOutput {

    void write(byte b[]) throws IOException;

    void write(byte b[], int off, int len) throws IOException;
}
