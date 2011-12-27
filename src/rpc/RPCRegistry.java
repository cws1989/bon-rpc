package rpc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.NotFoundException;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class RPCRegistry {

    private static final Logger LOG = Logger.getLogger(RPCRegistry.class.getName());
    protected List<RPCRegistryMethod> localMethodRegistry;
    protected List<RPCRegistryMethod> remoteMethodRegistry;
    protected Map<Class<?>, Integer> registeredLocalClasses;
    protected Map<Class<?>, Integer> registeredRemoteClasses;

    public RPCRegistry() {
        localMethodRegistry = new ArrayList<RPCRegistryMethod>();
        remoteMethodRegistry = new ArrayList<RPCRegistryMethod>();
        registeredLocalClasses = new HashMap<Class<?>, Integer>();
        registeredRemoteClasses = new HashMap<Class<?>, Integer>();
    }

    public void clear() {
        localMethodRegistry.clear();
        remoteMethodRegistry.clear();
        registeredLocalClasses.clear();
        registeredRemoteClasses.clear();
    }

    public RPC getRPC() throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException {
        return new RPC(localMethodRegistry, remoteMethodRegistry, registeredLocalClasses, registeredRemoteClasses);
    }

    public void registerLocal(Class<?> objectClass) {
        registerClass(objectClass, registeredLocalClasses, localMethodRegistry);
    }

    public void registerRemote(Class<?> objectClass) {
        registerClass(objectClass, registeredRemoteClasses, remoteMethodRegistry);
    }

    protected int registerClass(Class<?> objectClass, Map<Class<?>, Integer> classMap, List<RPCRegistryMethod> methodList) {
        if (classMap.get(objectClass) != null) {
            LOG.log(Level.SEVERE, "class {0} already registered", objectClass.getName());
            return 0;
        }

        classMap.put(objectClass, classMap.size());

        Method[] methods = objectClass.getDeclaredMethods();
        for (Method method : methods) {
            methodList.add(new RPCRegistryMethod(method, null));
        }

        return methods.length;
    }

    protected static class RPCRegistryMethod {

        protected final Method method;
        protected Object instance;
        protected boolean noRequestId;
        protected boolean noRespond;

        protected RPCRegistryMethod(Method method, Object instance) {
            this.method = method;
            this.instance = instance;
            this.noRequestId = false;
            this.noRespond = false;
        }
    }
}
