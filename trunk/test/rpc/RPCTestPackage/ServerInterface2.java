package rpc.RPCTestPackage;

import rpc.annotation.RequestTypeId;

public interface ServerInterface2 {

    @RequestTypeId(3)
    void abc();
}