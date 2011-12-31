package rpc.RPCTestPackage;

import java.util.List;
import java.util.Map;
import rpc.annotation.Blocking;
import rpc.annotation.RequestTypeId;

public interface RemoteInterface {

    @Blocking(true)
    @RequestTypeId(1)
    void abc(Map<List<Object>, List<Object>> abc, double absc, int abcc, long dss);

    List<Object> get();

    @Blocking(true)
    @RequestTypeId(2)
    Double eval(double x);
}
