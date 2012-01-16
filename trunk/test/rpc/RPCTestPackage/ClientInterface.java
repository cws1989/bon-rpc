package rpc.RPCTestPackage;

import java.io.IOException;
import rpc.annotation.Blocking;
import rpc.annotation.Broadcast;
import rpc.annotation.RequestTypeId;

public interface ClientInterface {

    @RequestTypeId(1)
    @Blocking()
    void test() throws IOException;

    @RequestTypeId(16383)
    @Broadcast()
    void notifyClient(Integer[] broadcastList);
}
