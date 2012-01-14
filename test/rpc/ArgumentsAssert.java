package rpc;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class ArgumentsAssert {

    private static final Logger LOG = Logger.getLogger(ArgumentsAssert.class.getName());
    protected static final Map<Object, AssertionDataContainer> assertionDataMap = Collections.synchronizedMap(new HashMap<Object, AssertionDataContainer>());

    protected ArgumentsAssert() {
    }

    public static boolean register(Object key, Object[][] assertionData) {
        synchronized (assertionDataMap) {
            if (assertionDataMap.get(key) != null) {
                return false;
            }
            assertionDataMap.put(key, new AssertionDataContainer(assertionData));
        }
        return true;
    }

    public static boolean assertMatch(Object key, Object... assertionData) {
        boolean matchResult = false;

        do {
            AssertionDataContainer _assertionDataContainer = null;
            if ((_assertionDataContainer = assertionDataMap.get(key)) == null) {
                break;
            }
            synchronized (_assertionDataContainer) {
                if (_assertionDataContainer.assertionData.length <= _assertionDataContainer.count) {
                    break;
                }

                Object[] _assertionData = _assertionDataContainer.assertionData[_assertionDataContainer.count];
                if (_assertionData.length != assertionData.length) {
                    break;
                }
                for (int i = 0, iEnd = _assertionData.length; i < iEnd; i++) {
                    if (!assertEquals(_assertionData[i], assertionData[i])) {
                        break;
                    }
                }
                _assertionDataContainer.count++;
            }
            matchResult = true;
        } while (false);

        if (!matchResult) {
            LOG.log(Level.SEVERE, "assert Match failed", new Exception());
        }

        return true;
    }

    public static boolean assertEquals(Object o1, Object o2) {
        if ((o1 == null && o2 != null) || (o1 != null && o2 == null)) {
            return false;
        }
        if (!o1.getClass().equals(o2.getClass())) {
            return false;
        }
        if (o1.getClass().isArray()) {
            Object[] o1Array = (Object[]) o1;
            Object[] o2Array = (Object[]) o2;

            if (o1Array.length != o2Array.length) {
                return false;
            }

            for (int i = 0, iEnd = o1Array.length; i < iEnd; i++) {
                if (!assertEquals(o1Array[i], o2Array[i])) {
                    return false;
                }
            }
        } else if (o1 instanceof List) {
            List<Object> o1List = (List<Object>) o1;
            List<Object> o2List = (List<Object>) o2;

            if (o1List.size() != o2List.size()) {
                return false;
            }

            if (!assertEquals(o1List.toArray(), o2List.toArray())) {
                return false;
            }
        } else if (o1 instanceof Map) {
            Map<Object, Object> o1Map = (Map<Object, Object>) o1;
            Map<Object, Object> o2Map = (Map<Object, Object>) o2;

            if (o1Map.size() != o2Map.size()) {
                return false;
            }

            for (Object o1MapObj : o1Map.keySet()) {
                if (!assertEquals(o1Map.remove(o1MapObj), o2Map.remove(o1MapObj))) {
                    return false;
                }
            }

            if (!o1Map.isEmpty()) {
                return false;
            }
        } else {
            if (!o1.equals(o2)) {
                return false;
            }
        }
        return true;
    }

    public static void finish() {
        synchronized (assertionDataMap) {
            for (Object key : assertionDataMap.keySet()) {
                AssertionDataContainer assertionDataContainer = assertionDataMap.get(key);
                if (assertionDataContainer.assertionData.length == assertionDataContainer.count) {
                    continue;
                }
                LOG.log(Level.SEVERE, String.format("arguments assertion not consumed, key: %1$s, expected count: %2$d, current count: %3$d",
                        key.toString(), assertionDataContainer.assertionData.length, assertionDataContainer.count));
            }
        }
    }

    protected static class AssertionDataContainer {

        protected Object[][] assertionData;
        protected int count;

        protected AssertionDataContainer(Object[][] assertionData) {
            this.assertionData = assertionData;
            count = 0;
        }
    }
}
