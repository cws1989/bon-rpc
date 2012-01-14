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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import rpc.RPC.RPCIdSet;
import rpc.RPC.RPCRequest;
import rpc.annotation.Blocking;
import rpc.annotation.Broadcast;
import rpc.annotation.NoRespond;
import rpc.annotation.RequestTypeId;
import rpc.annotation.UserObject;
import rpc.exception.ClassRegisteredException;
import rpc.exception.ConditionConflictException;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class RPCRegistry {

    private static final Logger LOG = Logger.getLogger(RPCRegistry.class.getName());
    protected final List<RPCRegistryMethod> localMethodRegistry;
    protected final List<RPCRegistryMethod> remoteMethodRegistry;
    protected final Map<Class<?>, Integer> registeredLocalClasses;
    protected final Map<Class<?>, Integer> registeredRemoteClasses;
    //
    protected Runnable retryTask;
    protected Thread retryThread;
    protected final List<RPC> rpcList;
    protected final Map<Object, RPC> userObjectRPCMap;

    public RPCRegistry() {
        localMethodRegistry = new ArrayList<RPCRegistryMethod>();
        remoteMethodRegistry = new ArrayList<RPCRegistryMethod>();
        registeredLocalClasses = new HashMap<Class<?>, Integer>();
        registeredRemoteClasses = new HashMap<Class<?>, Integer>();

        rpcList = Collections.synchronizedList(new ArrayList<RPC>());
        userObjectRPCMap = Collections.synchronizedMap(new HashMap<Object, RPC>());
        retryTask = new Runnable() {

            @Override
            public void run() {
                long sleepTime;
                while (true) {
                    if (Thread.interrupted()) {
                        synchronized (RPCRegistry.this) {
                            retryThread = null;
                            RPCRegistry.this.notifyAll();
                        }
                        break;
                    }

                    synchronized (RPCRegistry.this) {
                        long currentTime = System.currentTimeMillis();

                        RPC[] rpcArray = null;
                        synchronized (rpcList) {
                            rpcArray = rpcList.toArray(new RPC[rpcList.size()]);
                        }

                        for (RPC rpc : rpcArray) {
                            if (rpc.out == null) {
                                continue;
                            }

                            //<editor-fold defaultstate="collapsed" desc="send largest sequential respondedId">
                            try {
                                List<RPCIdSet> idSetList = new ArrayList<RPCIdSet>();
                                idSetList.add(rpc.requestIdSet);
                                for (int i = 0, iEnd = rpc.sequentialRequestIdSet.length; i < iEnd; i++) {
                                    RPCIdSet _idSet = rpc.sequentialRequestIdSet[i];
                                    if (_idSet != null) {
                                        idSetList.add(_idSet);
                                    }
                                }
                                for (RPCIdSet _idSet : idSetList) {
                                    if (_idSet.respondedId < _idSet.lastRespondId) {
                                        Object[] sendObject = _idSet.sequentialId == -1 ? new Object[]{1073741823} : new Object[]{_idSet.sequentialId, 1073741823};
                                        rpc.send(0, sendObject, true, false, false);
                                        _idSet.lastRespondId = 1;
                                        _idSet.lastRespondIdSendTime = currentTime;
                                    }
                                    if (_idSet.respondedId - _idSet.lastRespondId > 100 || currentTime - _idSet.lastRespondIdSendTime > 30000) {
                                        Object[] sendObject = _idSet.sequentialId == -1 ? new Object[]{_idSet.respondedId} : new Object[]{_idSet.sequentialId, _idSet.respondedId};
                                        rpc.send(0, sendObject, true, false, false);
                                        _idSet.lastRespondId = _idSet.respondedId;
                                        _idSet.lastRespondIdSendTime = currentTime;
                                    }
                                }
                            } catch (Exception ex) {
                                LOG.log(Level.INFO, null, ex);
                            }
                            //</editor-fold>

                            //<editor-fold defaultstate="collapsed" desc="retry send request">
                            List<Map<Integer, RPCRequest>> requestListList = new ArrayList<Map<Integer, RPCRequest>>();
                            requestListList.add(rpc.requestList);
                            for (int i = 0, iEnd = rpc.sequentialRequestIdSet.length; i < iEnd; i++) {
                                Map<Integer, RPCRequest> _requestList = rpc.sequentialRequestList[i];
                                if (_requestList != null) {
                                    requestListList.add(_requestList);
                                }
                            }
                            for (Map<Integer, RPCRequest> _requestList : requestListList) {
                                synchronized (_requestList) {
                                    for (RPCRequest _request : _requestList.values()) {
                                        if (_request.respond == null && currentTime - _request.time > 5000) {
                                            try {
                                                rpc.genericSend(_request.packetData, _request.requestId, true, false);
                                            } catch (Exception ex) {
                                                LOG.log(Level.INFO, null, ex);
                                            }
                                            _request.time = currentTime;
                                        }
                                    }
                                }
                            }
                            //</editor-fold>

                            //<editor-fold defaultstate="collapsed" desc="heart beat">
                            long lastReceiveTimeDiff = currentTime - rpc.lastPacketReceiveTime;
                            if (lastReceiveTimeDiff > 35000) {
                                rpc.close();
                            } else if (lastReceiveTimeDiff > 10000 && currentTime - rpc.lastHeartBeatSendTime > 10000) {
                                try {
                                    rpc.send(0, new Object[]{null}, true, false, false);
                                    rpc.lastHeartBeatSendTime = currentTime;
                                } catch (Exception ex) {
                                    LOG.log(Level.INFO, null, ex);
                                }
                            }
                            //</editor-fold>
                        }

                        sleepTime = Math.max(0, 2500 - (System.currentTimeMillis() - currentTime));
                    }

                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ex) {
                        synchronized (RPCRegistry.this) {
                            retryThread = null;
                            RPCRegistry.this.notifyAll();
                        }
                        break;
                    }
                }
            }
        };
        start();
    }

    public void start() {
        synchronized (this) {
            if (retryThread != null) {
                return;
            }
            retryThread = new Thread(retryTask);
            retryThread.start();
        }
    }

    public void stop() {
        synchronized (this) {
            retryThread.interrupt();
            try {
                this.wait();
            } catch (InterruptedException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
    }

    public void clear() {
        localMethodRegistry.clear();
        remoteMethodRegistry.clear();
        registeredLocalClasses.clear();
        registeredRemoteClasses.clear();
    }

    protected void put(Object userObject, RPC rpc) {
        userObjectRPCMap.put(userObject, rpc);
    }

    protected RPC remove(Object userObject) {
        return userObjectRPCMap.remove(userObject);
    }

    protected RPC get(Object userObject) {
        return userObjectRPCMap.get(userObject);
    }

    protected void remove(RPC rpc) {
        rpcList.remove(rpc);
    }

    public RPC getRPC() throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException {
        RPC rpc = new RPC(this, new ArrayList<RPCRegistryMethod>(localMethodRegistry), new ArrayList<RPCRegistryMethod>(remoteMethodRegistry),
                new HashMap<Class<?>, Integer>(registeredLocalClasses), new HashMap<Class<?>, Integer>(registeredRemoteClasses));
        rpcList.add(rpc);
        return rpc;
    }

    public void registerLocal(Class<?> objectClass) throws ClassRegisteredException, ConditionConflictException {
        registerClass(objectClass, registeredLocalClasses, localMethodRegistry);
    }

    public void registerRemote(Class<?> objectClass) throws ClassRegisteredException, ConditionConflictException {
        registerClass(objectClass, registeredRemoteClasses, remoteMethodRegistry);
    }

    protected int registerClass(Class<?> objectClass, Map<Class<?>, Integer> classMap, List<RPCRegistryMethod> methodList) throws ClassRegisteredException, ConditionConflictException {
        if (classMap.get(objectClass) != null) {
            throw new ClassRegisteredException(String.format("class %1$s already registered", objectClass.getName()));
        }

        classMap.put(objectClass, methodList.size());

        Method[] methods = objectClass.getDeclaredMethods();
        for (Method method : methods) {
            RequestTypeId requestTypeIdAnnotation = method.getAnnotation(RequestTypeId.class);
            if (requestTypeIdAnnotation == null || requestTypeIdAnnotation.value() <= 0) {
                throw new ConditionConflictException(String.format("'RequestTypeId' not found, class: %1$s, function: %2$s", objectClass.getName(), method.getName()));
            }

            boolean blocking = false;
            Blocking blockingAnnotation = method.getAnnotation(Blocking.class);
            if (blockingAnnotation != null) {
                blocking = true;
            }

            boolean broadcast = false;
            Broadcast broadcastAnnotation = method.getAnnotation(Broadcast.class);
            if (broadcastAnnotation != null) {
                broadcast = true;
            }

            boolean noRespond = false;
            NoRespond noRespondAnnotation = method.getAnnotation(NoRespond.class);
            if (noRespondAnnotation != null) {
                noRespond = true;
            }

            boolean userObject = false;
            UserObject userObjectAnnotation = method.getAnnotation(UserObject.class);
            if (userObjectAnnotation != null) {
                userObject = true;
            }

//1. Blocking
//2. Broadcast
//3. NoRespond
//4. RequestTypeId
//5. Sequential
//6. UserObject
//
//1. Blocking
//   - if Broadcast => no Blocking
//   - if NoRespond => no Blocking
//   - if return value type is not null => Blocking
//2. Broadcast
//   - if UserObject => second argument is an array
//     else => first argument is an array
//3. NoRespond
//   - if return value type is not null => have Respond
//   - if Blocking => have Respond (duplicated)
//6. UserObject
//   - if Broadcast => second argument is an array
//     else => first argument is an array

            if (broadcast && blocking) {
                throw new ConditionConflictException(String.format("condition 'Broadcast' cannot use with 'Blocking', class: %1$s, function: %2$s", objectClass.getName(), method.getName()));
            }
            if (noRespond && blocking) {
                throw new ConditionConflictException(String.format("condition 'NoRespond' cannot use with 'Blocking', class: %1$s, function: %2$s", objectClass.getName(), method.getName()));
            }
            if (!method.getReturnType().equals(void.class) && !blocking) {
                throw new ConditionConflictException(String.format("return type is not void but condition 'Blocking' not exist , class: %1$s, function: %2$s", objectClass.getName(), method.getName()));
            }

            if (broadcast) {
                if (userObject && (method.getParameterTypes().length < 2 || !method.getParameterTypes()[1].isArray())) {
                    throw new ConditionConflictException(String.format("condition 'Broadcast' and 'UserObject' exist but the parameters length is less than 2 or second parameter is not an array, class: %1$s, function: %2$s", objectClass.getName(), method.getName()));
                }
                if (!userObject && (method.getParameterTypes().length < 1 || !method.getParameterTypes()[0].isArray())) {
                    throw new ConditionConflictException(String.format("condition 'Broadcast' exist but the parameters length is less than 1 or first parameter is not an array, class: %1$s, function: %2$s", objectClass.getName(), method.getName()));
                }
            }

            if (!method.getReturnType().equals(void.class) && noRespond) {
                throw new ConditionConflictException(String.format("condition 'NoRespond' exist but the return type is not null, class: %1$s, function: %2$s", objectClass.getName(), method.getName()));
            }

            if (userObject && (method.getParameterTypes().length < 1 || method.getParameterTypes()[0].isArray())) {
                throw new ConditionConflictException(String.format("condition 'UserObject' exist but the parameters length is less than 1 or the first argument is an array, class: %1$s, function: %2$s", objectClass.getName(), method.getName()));
            }

            methodList.add(new RPCRegistryMethod(method, null, noRespond, userObject, broadcast));
        }

        return methods.length;
    }

    protected static class RPCRegistryMethod {

        protected final Method method;
        protected Object instance;
        protected boolean noRespond;
        protected boolean userObject;
        protected boolean broadcast;

        protected RPCRegistryMethod(Method method, Object instance, boolean noRespond, boolean userObject, boolean broadcast) {
            this.method = method;
            this.instance = instance;
            this.noRespond = noRespond;
            this.userObject = userObject;
            this.broadcast = broadcast;
        }
    }
}
