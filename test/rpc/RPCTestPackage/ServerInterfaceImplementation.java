package rpc.RPCTestPackage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class ServerInterfaceImplementation implements ServerInterface {

    @Override
    public void test() {
    }

    @Override
    public Double ljkihy(Integer userObject, Map<Integer, List<String>> test) {
        return null;
    }

    @Override
    public Double get(int x) {
        return null;
    }

    @Override
    public Double eval(double x) {
        return x;
    }

    @Override
    public List<Object> eval() {
        List<Object> returnList = new ArrayList<Object>();
        returnList.add(1);
        returnList.add("eval");
        return returnList;
    }
}
