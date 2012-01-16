package rpc.RPCTestPackage;

import rpc.annotation.RequestTypeId;
import rpc.annotation.UserObject;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public interface ConditionConflictTest_Interface8 {

    @RequestTypeId(1)
    @UserObject()
    void test(Integer[] broadcastList);
}
