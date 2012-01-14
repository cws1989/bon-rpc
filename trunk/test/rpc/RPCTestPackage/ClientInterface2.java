package rpc.RPCTestPackage;

import rpc.annotation.Blocking;
import rpc.annotation.RequestTypeId;

public interface ClientInterface2 {

    @Blocking()
    @RequestTypeId(70)
    void test();
}
