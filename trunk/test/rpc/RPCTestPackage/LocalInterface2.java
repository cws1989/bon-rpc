package rpc.RPCTestPackage;

import rpc.annotation.Blocking;
import rpc.annotation.RequestTypeId;

public interface LocalInterface2 {

    @Blocking()
    @RequestTypeId(70)
    void test();
}
