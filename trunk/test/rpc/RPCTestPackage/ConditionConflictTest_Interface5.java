package rpc.RPCTestPackage;

import rpc.annotation.Broadcast;
import rpc.annotation.RequestTypeId;
import rpc.annotation.UserObject;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public interface ConditionConflictTest_Interface5 {

    @RequestTypeId(1)
    @Broadcast()
    void test(Integer broadcastList);
}
