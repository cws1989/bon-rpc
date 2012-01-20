package rpc.RPCTestPackage;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import rpc.annotation.Blocking;
import rpc.annotation.RequestTypeId;
import rpc.annotation.Sequential;
import rpc.annotation.UserObject;

public interface ServerInterface {

    @RequestTypeId(1)
    @Blocking()
    void test() throws IOException;

    @RequestTypeId(2)
    @Blocking()
    @Sequential(1)
    void testSequential() throws IOException;

    @Blocking()
    @UserObject()
    @RequestTypeId(4)
    Double ljkihy(Integer userObject, Map<Integer, List<String>> test);

    @Blocking()
    @RequestTypeId(5)
    Double get(int x);

    @Blocking()
    @RequestTypeId(6)
    Double eval(double x);

    @Blocking()
    @RequestTypeId(7)
    List<Object> eval();
}