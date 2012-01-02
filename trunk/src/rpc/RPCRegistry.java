// Copyright (c) 2012 Chan Wai Shing
//
// This file is part of BON RPC.
//
// BON RPC is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// BON RPC is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with BON RPC.  If not, see <http://www.gnu.org/licenses/>.
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
    protected final List<RPCRegistryMethod> localMethodRegistry;
    protected final List<RPCRegistryMethod> remoteMethodRegistry;
    protected final Map<Class<?>, Integer> registeredLocalClasses;
    protected final Map<Class<?>, Integer> registeredRemoteClasses;

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
        return new RPC(new ArrayList<RPCRegistryMethod>(localMethodRegistry), new ArrayList<RPCRegistryMethod>(remoteMethodRegistry),
                new HashMap<Class<?>, Integer>(registeredLocalClasses), new HashMap<Class<?>, Integer>(registeredRemoteClasses));
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

        classMap.put(objectClass, methodList.size());

        Method[] methods = objectClass.getDeclaredMethods();
        for (Method method : methods) {
            methodList.add(new RPCRegistryMethod(method, null));
        }

        return methods.length;
    }

    protected static class RPCRegistryMethod {

        protected final Method method;
        protected Object instance;
        protected boolean noRespond;

        protected RPCRegistryMethod(Method method, Object instance) {
            this.method = method;
            this.instance = instance;
            this.noRespond = false;
        }
    }
}
