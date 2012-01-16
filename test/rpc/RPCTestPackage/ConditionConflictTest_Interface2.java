package rpc.RPCTestPackage;

import rpc.annotation.Blocking;
import rpc.annotation.NoRespond;
import rpc.annotation.RequestTypeId;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public interface ConditionConflictTest_Interface2 {

    @RequestTypeId(1)
    @NoRespond()
    @Blocking()
    void test();
}
