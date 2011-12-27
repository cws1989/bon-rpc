package rpc.RPCTestPackage;

import rpc.annotation.RequestTypeId;

public interface RemoteInterface2 {

    @RequestTypeId(3)
    void abc();
}
