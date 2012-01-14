package rpc.RPCTestPackage;

import java.util.List;
import java.util.Map;
import rpc.annotation.Blocking;
import rpc.annotation.RequestTypeId;
import rpc.annotation.UserObject;

public interface ServerInterface {

    @Blocking()
    @UserObject()
    @RequestTypeId(4)
    Double ljkihy(int userObject, Map<Integer, List<String>> test);

    @Blocking()
    @RequestTypeId(5)
    Double get(int x);

    @Blocking()
    @RequestTypeId(2)
    Double eval(double x);

    @Blocking()
    @RequestTypeId(1)
    void eval();
}