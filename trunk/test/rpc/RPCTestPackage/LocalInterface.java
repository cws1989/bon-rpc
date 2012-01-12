package rpc.RPCTestPackage;

import rpc.annotation.Blocking;
import rpc.annotation.Broadcast;
import rpc.annotation.RequestTypeId;

public interface LocalInterface {

    @RequestTypeId(10)
    @Broadcast()
    void notifyClient(Integer[] broadcastList);
}
