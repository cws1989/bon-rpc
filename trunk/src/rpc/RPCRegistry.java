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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import rpc.RPC.RPCIdSet;
import rpc.RPC.RPCRequest;
import rpc.annotation.NoRespond;
import rpc.annotation.UserObject;

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
    protected final Map<RPC, RPCMonitorRecord> retryRecords;

    public RPCRegistry() {
        localMethodRegistry = new ArrayList<RPCRegistryMethod>();
        remoteMethodRegistry = new ArrayList<RPCRegistryMethod>();
        registeredLocalClasses = new HashMap<Class<?>, Integer>();
        registeredRemoteClasses = new HashMap<Class<?>, Integer>();

        rpcList = new ArrayList<RPC>();
        retryRecords = new HashMap<RPC, RPCMonitorRecord>();
        retryTask = new Runnable() {

            @Override
            public void run() {
                long currentTime, sleepTime, lastReceiveTimeDiff;
                while (true) {
                    if (Thread.interrupted()) {
                        synchronized (RPCRegistry.this) {
                            retryThread = null;
                            RPCRegistry.this.notifyAll();
                        }
                        break;
                    }

                    synchronized (RPCRegistry.this) {
                        currentTime = System.currentTimeMillis();

                        RPC[] rpcArray = null;
                        synchronized (rpcList) {
                            rpcArray = rpcList.toArray(new RPC[rpcList.size()]);
                        }

                        for (RPC rpc : rpcArray) {
                            if (rpc.out == null) {
                                continue;
                            }

                            RPCMonitorRecord monitorRecord = retryRecords.get(rpc);

                            try {
                                if (rpc.requestIdSet.respondedId < monitorRecord.lastRespondId) {
                                    rpc.send(0, new Object[]{0, 1073741823}, true, false);
                                    monitorRecord.lastRespondId = 1073741823;
                                    monitorRecord.lastRespondIdSentTime = currentTime;
                                }
                                if (rpc.requestIdSet.respondedId - monitorRecord.lastRespondId > 100 || currentTime - monitorRecord.lastRespondIdSentTime > 30000) {
                                    rpc.send(0, new Object[]{0, rpc.requestIdSet.respondedId}, true, false);
                                    monitorRecord.lastRespondId = rpc.requestIdSet.respondedId;
                                    monitorRecord.lastRespondIdSentTime = currentTime;
                                }
                                for (int i = 0, iEnd = rpc.sequentialRequestIdSet.length; i < iEnd; i++) {
                                    RPCIdSet idSet = rpc.sequentialRequestIdSet[i];
                                    if (idSet == null) {
                                        continue;
                                    }
                                    if (idSet.respondedId < monitorRecord.lastRespondId) {
                                        rpc.send(0, new Object[]{i, 1073741823}, true, false);
                                        monitorRecord.lastRespondId = 1073741823;
                                        monitorRecord.lastRespondIdSentTime = currentTime;
                                    }
                                    if (idSet.respondedId - monitorRecord.lastRespondId > 100 || currentTime - monitorRecord.lastRespondIdSentTime > 30000) {
                                        rpc.send(0, new Object[]{i, idSet.respondedId}, true, false);
                                        monitorRecord.lastRespondId = idSet.respondedId;
                                        monitorRecord.lastRespondIdSentTime = currentTime;
                                    }
                                }
                            } catch (Exception ex) {
                                LOG.log(Level.INFO, null, ex);
                            }

                            List<Map<Integer, RPCRequest>> requestListList = new ArrayList<Map<Integer, RPCRequest>>();
                            requestListList.add(rpc.requestList);
                            for (int i = 0, iEnd = rpc.sequentialRequestIdSet.length; i < iEnd; i++) {
                                Map<Integer, RPCRequest> _requestList = rpc.sequentialRequestList[i];
                                if (_requestList == null) {
                                    continue;
                                }
                                requestListList.add(_requestList);
                            }
                            for (Map<Integer, RPCRequest> _requestList : requestListList) {
                                synchronized (_requestList) {
                                    for (RPCRequest _request : _requestList.values()) {
                                        if (_request.respond == null && currentTime - _request.time > 5000) {
                                            try {
                                                rpc.genericSend(_request.packetData, _request.requestId, true, false);
                                            } catch (IOException ex) {
                                                LOG.log(Level.INFO, null, ex);
                                            }
                                            _request.time = currentTime;
                                        }
                                    }
                                }
                            }

                            lastReceiveTimeDiff = currentTime - rpc.lastPacketReceiveTime;
                            if (lastReceiveTimeDiff > 35000) {
                                rpc.close();
                            } else if (lastReceiveTimeDiff > 10000 && currentTime - monitorRecord.lastHeartBeatSendTime > 10000) {
                                try {
                                    rpc.send(0, new Object[]{null}, true, false);
                                    monitorRecord.lastHeartBeatSendTime = currentTime;
                                } catch (Exception ex) {
                                    LOG.log(Level.INFO, null, ex);
                                }
                            }

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

    protected void remove(RPC rpc) {
        synchronized (this) {
            rpcList.remove(rpc);
            retryRecords.remove(rpc);
        }
    }

    public RPC getRPC() throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException {
        RPC rpc = new RPC(this, new ArrayList<RPCRegistryMethod>(localMethodRegistry), new ArrayList<RPCRegistryMethod>(remoteMethodRegistry),
                new HashMap<Class<?>, Integer>(registeredLocalClasses), new HashMap<Class<?>, Integer>(registeredRemoteClasses));
        synchronized (this) {
            rpcList.add(rpc);
            retryRecords.put(rpc, new RPCMonitorRecord());
        }
        return rpc;
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
            boolean noRespond = false;
            NoRespond noRespondAnnotation = method.getAnnotation(NoRespond.class);
            if (noRespondAnnotation != null) {
                noRespond = true;
            }

            boolean userObject = false;
            UserObject userObjectAnnotation = method.getAnnotation(UserObject.class);
            if (userObjectAnnotation != null && method.getParameterTypes().length > 0) {
                userObject = true;
            }

            methodList.add(new RPCRegistryMethod(method, null, noRespond, userObject));
        }

        return methods.length;
    }

    protected static class RPCRegistryMethod {

        protected final Method method;
        protected Object instance;
        protected boolean noRespond;
        protected boolean userObject;

        protected RPCRegistryMethod(Method method, Object instance, boolean noRespond, boolean userObject) {
            this.method = method;
            this.instance = instance;
            this.noRespond = noRespond;
            this.userObject = userObject;
        }
    }

    protected static class RPCMonitorRecord {

        protected long lastHeartBeatSendTime;
        protected int lastRespondId;
        protected long lastRespondIdSentTime;

        protected RPCMonitorRecord() {
            lastHeartBeatSendTime = System.currentTimeMillis();
            lastRespondId = 1;
            lastRespondIdSentTime = lastHeartBeatSendTime;
        }
    }
}
