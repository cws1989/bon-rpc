package rpc.RPCTestPackage;

import rpc.annotation.NoRespond;
import rpc.annotation.RequestTypeId;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public interface ConditionConflictTest_Interface6 {

    @RequestTypeId(1)
    @NoRespond()
    Integer test();
}
