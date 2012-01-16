package rpc.RPCTestPackage;

import rpc.annotation.Blocking;
import rpc.annotation.Broadcast;
import rpc.annotation.RequestTypeId;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public interface ConditionConflictTest_Interface1 {

    @RequestTypeId(1)
    @Broadcast()
    @Blocking()
    void test();
}
