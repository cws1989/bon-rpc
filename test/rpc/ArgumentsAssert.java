package rpc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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

    public static void clear() {
        assertionDataMap.clear();
    }

    public static boolean assertAnyMatch(Object key, Object... assertionData) {
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

                int matchIndex = -1;
                for (int i = _assertionDataContainer.count, iEnd = _assertionDataContainer.assertionData.length; i < iEnd; i++) {
                    Object[] _assertionData = _assertionDataContainer.assertionData[i];
                    int matchCount = 0;
                    for (int j = 0, jEnd = _assertionData.length; j < jEnd; j++) {
                        if (assertEquals(_assertionData[j], assertionData[j])) {
                            matchCount++;
                        } else {
                            break;
                        }
                    }
                    if (matchCount == _assertionData.length) {
                        matchIndex = i;
                        matchResult = true;
                        break;
                    }
                }

                if (matchIndex != -1) {
                    Object[][] newAssertionData = new Object[_assertionDataContainer.assertionData.length - 1][];
                    System.arraycopy(_assertionDataContainer.assertionData, 0, newAssertionData, 0, matchIndex);
                    if (matchIndex != _assertionDataContainer.assertionData.length - 1) {
                        System.arraycopy(_assertionDataContainer.assertionData, matchIndex + 1, newAssertionData, matchIndex, _assertionDataContainer.assertionData.length - matchIndex - 1);
                    }
                }
            }
        } while (false);

        if (!matchResult) {
            LOG.log(Level.SEVERE, "assert any match failed", new Exception());
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
            LOG.log(Level.SEVERE, "assert match failed", new Exception());
        }

        return true;
    }

    public static boolean assertEquals(Object o1, Object o2) {
        if ((o1 == null && o2 != null) || (o1 != null && o2 == null)) {
            return false;
        } else if (o1 == null && o2 == null) {
            return true;
        }
        if (!o1.getClass().equals(o2.getClass())) {
            return false;
        }
        if (o1.getClass().isArray()) {
            if (o1 instanceof byte[]) {
                byte[] o1Array = (byte[]) o1;
                byte[] o2Array = (byte[]) o2;

                if (o1Array.length != o2Array.length) {
                    return false;
                }

                for (int i = 0, iEnd = o1Array.length; i < iEnd; i++) {
                    if (!assertEquals(o1Array[i], o2Array[i])) {
                        return false;
                    }
                }
            } else {
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

            Iterator<Object> iterator = o1Map.keySet().iterator();
            while (iterator.hasNext()) {
                Object o1MapObj = iterator.next();
                if (!assertEquals(o1Map.get(o1MapObj), o2Map.remove(o1MapObj))) {
                    return false;
                }
                iterator.remove();
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
