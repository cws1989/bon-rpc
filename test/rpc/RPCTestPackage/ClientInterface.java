package rpc.RPCTestPackage;

import rpc.annotation.Broadcast;
import rpc.annotation.RequestTypeId;

public interface ClientInterface {

    @RequestTypeId(10)
    @Broadcast()
    void notifyClient(Integer[] broadcastList);
}
