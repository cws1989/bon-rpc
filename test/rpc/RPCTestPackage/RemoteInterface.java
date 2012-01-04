package rpc.RPCTestPackage;

import java.util.List;
import java.util.Map;
import rpc.annotation.Blocking;
import rpc.annotation.RequestTypeId;

public interface RemoteInterface {

    @Blocking(true)
    @RequestTypeId(4)
    Double ljkihy(Map<Integer, List<String>> test);

    @Blocking(true)
    @RequestTypeId(5)
    Double get(int x);

    @Blocking(true)
    @RequestTypeId(2)
    Double eval(double x);

    @Blocking(true)
    @RequestTypeId(1)
    void eval();
}
