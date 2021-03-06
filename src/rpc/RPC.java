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

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import rpc.RPCRegistry.RPCRegistryMethod;
import rpc.annotation.RequestTypeId;
import rpc.annotation.Sequential;
import rpc.codec.exception.UnsupportedDataTypeException;
import rpc.exception.InvocationFailedException;
import rpc.packet.DefaultDepacketizer;
import rpc.packet.DefaultPacketizer;
import rpc.packet.Depacketizer;
import rpc.packet.DepacketizerListener;
import rpc.packet.Packet;
import rpc.packet.Packetizer;
import rpc.transport.RemoteInput;
import rpc.transport.RemoteOutput;
import rpc.util.ClassMaker;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class RPC<T> implements RemoteInput, Closeable {

    private static final Logger LOG = Logger.getLogger(RPC.class.getName());
    //
    protected final RPCRegistry rpcRegistry;
    //
    protected final List<RPCRegistryMethod> localMethodRegistry;
    protected final List<RPCRegistryMethod> remoteMethodRegistry;
    protected final Map<Class<?>, Integer> registeredLocalClasses;
    protected final Map<Class<?>, Integer> registeredRemoteClasses;
    //
    protected final RPCIdSet requestIdSet;
    protected final RPCIdSet respondIdSet;
    protected final Map<Integer, RPCRequest> requestList;
    protected final Map<Integer, RPCRequest> respondList;
    //
    protected final RPCIdSet[] _sequentialRequestIdSet;
    protected final RPCIdSet[] _sequentialRespondIdSet;
    protected final Map<Integer, RPCRequest>[] _sequentialRequestList;
    protected final Map<Integer, RPCRequest>[] _sequentialRespondList;
    //
    protected final RPCIdSet[] sequentialRequestIdSet;
    protected final RPCIdSet[] sequentialRespondIdSet;
    protected final Map<Integer, RPCRequest>[] sequentialRequestList;
    protected final Map<Integer, RPCRequest>[] sequentialRespondList;
    //
    protected final RPCRegistryMethod[] localMethodMap;
    protected final Map<Class<?>, Object> remoteImplementations;
    //
    protected long lastPacketReceiveTime;
    protected long lastHeartBeatSendTime;
    //
    protected final List<RPCListener> listeners;
    protected RemoteOutput out;
    protected T userObject;
    //
    protected final Packetizer packetizer;
    protected final Depacketizer depacketizer;

    protected RPC(RPCRegistry rpcRegistry,
            List<RPCRegistryMethod> localMethodRegistry, List<RPCRegistryMethod> remoteMethodRegistry,
            Map<Class<?>, Integer> registeredLocalClasses, Map<Class<?>, Integer> registeredRemoteClasses)
            throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException {
        this.rpcRegistry = rpcRegistry;

        this.localMethodRegistry = localMethodRegistry;
        this.remoteMethodRegistry = remoteMethodRegistry;
        this.registeredLocalClasses = registeredLocalClasses;
        this.registeredRemoteClasses = registeredRemoteClasses;

        requestIdSet = new RPCIdSet(-1);
        respondIdSet = new RPCIdSet(-1);
        requestList = Collections.synchronizedMap(new HashMap<Integer, RPCRequest>());
        respondList = Collections.synchronizedMap(new HashMap<Integer, RPCRequest>());

        //<editor-fold defaultstate="collapsed" desc="local">
        int localMethodTypeIdMax = 0;
        int localSequentialIdMax = 0;
        for (RPCRegistryMethod method : localMethodRegistry) {
            RequestTypeId requestTypeId = method.method.getAnnotation(RequestTypeId.class);
            if (requestTypeId.value() > localMethodTypeIdMax) {
                localMethodTypeIdMax = requestTypeId.value();
            }

            Sequential sequentialAnnotation = method.method.getAnnotation(Sequential.class);
            if (sequentialAnnotation != null && sequentialAnnotation.value() > localSequentialIdMax) {
                localSequentialIdMax = sequentialAnnotation.value();
            }
        }

        localMethodMap = new RPCRegistryMethod[localMethodTypeIdMax + 1];

        _sequentialRespondIdSet = new RPCIdSet[localSequentialIdMax + 1];
        _sequentialRespondList = new Map[localSequentialIdMax + 1];

        sequentialRespondIdSet = new RPCIdSet[localMethodTypeIdMax + 1];
        sequentialRespondList = new Map[localMethodTypeIdMax + 1];

        for (RPCRegistryMethod method : localMethodRegistry) {
            RequestTypeId requestTypeId = method.method.getAnnotation(RequestTypeId.class);

            if (localMethodMap[requestTypeId.value()] != null) {
                // find the class that has the this duplicated request type id
                Class<?> targetClass = null;
                for (Class<?> _class : registeredLocalClasses.keySet()) {
                    Method[] _methods = _class.getMethods();
                    for (Method _method : _methods) {
                        if (_method.equals(method.method)) {
                            targetClass = _class;
                            break;
                        }
                    }
                    if (targetClass != null) {
                        break;
                    }
                }
                LOG.log(Level.SEVERE, "local request type id duplicated, id: {0}, class: {1}, method: {2}", new Object[]{requestTypeId.value(), targetClass.getName(), method.method.getName()});
            } else {
                localMethodMap[requestTypeId.value()] = method;
            }

            Sequential sequentialAnnotation = method.method.getAnnotation(Sequential.class);
            if (sequentialAnnotation != null) {
                if (_sequentialRespondList[sequentialAnnotation.value()] == null) {
                    _sequentialRespondIdSet[sequentialAnnotation.value()] = new RPCIdSet(sequentialAnnotation.value());
                    _sequentialRespondList[sequentialAnnotation.value()] = Collections.synchronizedMap(new HashMap<Integer, RPCRequest>());
                }
                sequentialRespondIdSet[requestTypeId.value()] = _sequentialRespondIdSet[sequentialAnnotation.value()];
                sequentialRespondList[requestTypeId.value()] = _sequentialRespondList[sequentialAnnotation.value()];
            }
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="remote">
        int remoteMethodTypeIdMax = 0;
        int remoteSequentialIdMax = 0;
        for (RPCRegistryMethod method : remoteMethodRegistry) {
            RequestTypeId requestTypeId = method.method.getAnnotation(RequestTypeId.class);
            if (requestTypeId.value() > remoteMethodTypeIdMax) {
                remoteMethodTypeIdMax = requestTypeId.value();
            }

            Sequential sequentialAnnotation = method.method.getAnnotation(Sequential.class);
            if (sequentialAnnotation != null && sequentialAnnotation.value() > remoteSequentialIdMax) {
                remoteSequentialIdMax = sequentialAnnotation.value();
            }
        }

        _sequentialRequestIdSet = new RPCIdSet[remoteSequentialIdMax + 1];
        _sequentialRequestList = new Map[remoteSequentialIdMax + 1];

        sequentialRequestIdSet = new RPCIdSet[remoteMethodTypeIdMax + 1];
        sequentialRequestList = new Map[remoteMethodTypeIdMax + 1];

        for (RPCRegistryMethod method : remoteMethodRegistry) {
            RequestTypeId requestTypeId = method.method.getAnnotation(RequestTypeId.class);

            Sequential sequentialAnnotation = method.method.getAnnotation(Sequential.class);
            if (sequentialAnnotation != null) {
                if (_sequentialRequestIdSet[sequentialAnnotation.value()] == null) {
                    _sequentialRequestIdSet[sequentialAnnotation.value()] = new RPCIdSet(sequentialAnnotation.value());
                    _sequentialRequestList[sequentialAnnotation.value()] = Collections.synchronizedMap(new HashMap<Integer, RPCRequest>());
                }
                sequentialRequestIdSet[requestTypeId.value()] = _sequentialRequestIdSet[sequentialAnnotation.value()];
                sequentialRequestList[requestTypeId.value()] = _sequentialRequestList[sequentialAnnotation.value()];
            }
        }

        List<Integer> requestTypeIdList = new ArrayList<Integer>();
        remoteImplementations = new HashMap<Class<?>, Object>();
        for (Class<?> objClass : registeredRemoteClasses.keySet()) {
            Method[] methods = objClass.getMethods();
            for (Method method : methods) {
                RequestTypeId requestTypeIdAnnotation = method.getAnnotation(RequestTypeId.class);
                if (requestTypeIdList.indexOf((Integer) requestTypeIdAnnotation.value()) != -1) {
                    LOG.log(Level.SEVERE, "remote request type id duplicated, id: {0}, class: {1}, method: {2}", new Object[]{requestTypeIdAnnotation.value(), objClass.getName(), method.getName()});
                    continue;
                }
                requestTypeIdList.add(requestTypeIdAnnotation.value());
            }
            remoteImplementations.put(objClass, ClassMaker.makeInstance(objClass, RPC.class, this));
        }
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="add mapping for sending maximum sequential respondId and heart beat">
        _sequentialRequestIdSet[0] = new RPCIdSet(0);
        _sequentialRequestList[0] = Collections.synchronizedMap(new HashMap<Integer, RPCRequest>());
        sequentialRequestIdSet[0] = _sequentialRequestIdSet[0];
        sequentialRequestList[0] = _sequentialRequestList[0];

        _sequentialRespondIdSet[0] = new RPCIdSet(0);
        _sequentialRespondList[0] = Collections.synchronizedMap(new HashMap<Integer, RPCRequest>());
        sequentialRespondIdSet[0] = _sequentialRespondIdSet[0];
        sequentialRespondList[0] = _sequentialRespondList[0];

        localMethodMap[0] = new RPCRegistryMethod(null, null, false, false, false);
        //</editor-fold>

        lastPacketReceiveTime = System.currentTimeMillis();
        lastHeartBeatSendTime = lastPacketReceiveTime;

        listeners = Collections.synchronizedList(new ArrayList<RPCListener>());
        out = null;
        userObject = null;

        packetizer = new DefaultPacketizer();
        depacketizer = new DefaultDepacketizer();
        //<editor-fold defaultstate="collapsed" desc="add depacketizer listener">
        depacketizer.addListener(new DepacketizerListener() {

            @Override
            public void packetReceived(Packet packet) {
                lastPacketReceiveTime = System.currentTimeMillis();

                boolean isRespond = packet.isRespond();
                int requestTypeId = packet.getRequestTypeId();
                int requestId = packet.getRequestId();
                Object content = packet.getContent();

                if (!(content instanceof List)) {
                    LOG.log(Level.SEVERE, "respond is not a list");
                    return;
                }
                List<Object> contentList = ((List<Object>) content);

                if (isRespond) {
                    if (requestTypeId >= sequentialRequestIdSet.length) {
                        return;
                    }

                    RPCIdSet _idSet = sequentialRequestIdSet[requestTypeId];
                    Map<Integer, RPCRequest> _requestList = sequentialRequestList[requestTypeId];
                    if (_requestList == null) {
                        _idSet = requestIdSet;
                        _requestList = requestList;
                    }

                    RPCRequest request = _requestList.get(requestId);
                    if (request == null) {
                        return;
                    }

                    if (contentList.size() != 1) {
                        if (contentList.size() == 2
                                && contentList.get(0) == null && contentList.get(1) instanceof Short) {
                            RPCError rpcError = RPCError.getRPCError((Short) contentList.get(1));
                            if (rpcError == null) {
                                LOG.log(Level.SEVERE, null, new Exception(String.format("error code not found, code: %1$d", (Short) contentList.get(1))));
                                return;
                            }
                            switch (rpcError) {
                                case REMOTE_CONNECTION_METHOD_NOT_REGISTERED:
                                    LOG.log(Level.SEVERE, null, new Exception("method not registered by remote connection"));
                                    break;
                                case REMOTE_CONNECTION_METHOD_INSTANCE_NOT_REGISTERED:
                                    LOG.log(Level.SEVERE, null, new Exception("instance not binded by remote connection"));
                                    break;
                                case REMOTE_CONNECTION_SEQUENTIAL_ID_NOT_REGISTERED:
                                    LOG.log(Level.SEVERE, null, new Exception("sequential id not registered by remote connection"));
                                    break;
                                case REMOTE_METHOD_INVOKE_ERROR:
                                    LOG.log(Level.SEVERE, null, new Exception("error occurred when remote connection invoking method"));
                                    break;
                                case RESPOND_ID_UPDATE_FAILED:
                                    LOG.log(Level.SEVERE, null, new Exception("respond id update failed"));
                                    break;
                            }
                            request.requestFailed = true;
                        } else {
                            LOG.log(Level.SEVERE, null, new Exception("size of the respond list is incorrect"));
                            return;
                        }
                    }
                    request.respond = contentList.get(0);
                    request.responded = true;
                    synchronized (request) {
                        request.notified = true;
                        request.notifyAll();
                    }

                    synchronized (_idSet) {
                        if (requestId == _idSet.respondedId) {
                            _requestList.remove(requestId);
                            _idSet.respondedId++;
                            if (_idSet.respondedId > 1073741823) {
                                _idSet.respondedId = 1;
                            }

                            RPCRequest _request = null;
                            while ((_request = _requestList.get(_idSet.respondedId)) != null) {
                                if (!_request.responded) {
                                    break;
                                }
                                _requestList.remove(_idSet.respondedId);
                                _idSet.respondedId++;
                                if (_idSet.respondedId > 1073741823) {
                                    _idSet.respondedId = 1;
                                }
                            }
                        }
                    }
                } else {
                    RPCRegistryMethod method = null;
                    if (requestTypeId >= sequentialRespondIdSet.length || (method = localMethodMap[requestTypeId]) == null) {
                        LOG.log(Level.SEVERE, "method with requestTypeId {0} not found", new Object[]{requestTypeId});
                        try {
                            respond(requestId, requestTypeId, new Object[]{null, RPCError.REMOTE_CONNECTION_METHOD_NOT_REGISTERED.getValue()});
                        } catch (Exception ex) {
                            LOG.log(Level.SEVERE, null, ex);
                        }
                        return;
                    }

                    RPCIdSet _idSet = sequentialRespondIdSet[requestTypeId];
                    Map<Integer, RPCRequest> _respondList = sequentialRespondList[requestTypeId];
                    if (_respondList != null) {
                        synchronized (_idSet) {
                            if (_idSet.id == requestId) {
                                _idSet.id++;
                                if (_idSet.id > 1073741823) {
                                    _idSet.id = 1;
                                }

                                Object[] respond = invoke(requestTypeId, contentList.toArray());

                                RPCRequest rpcRequest = new RPCRequest(requestTypeId, requestId, System.currentTimeMillis(), null, respond);
                                rpcRequest.responded = true;
                                _respondList.put(requestId, rpcRequest);

                                if (!method.noRespond) {
                                    try {
                                        respond(requestId, requestTypeId, respond);
                                    } catch (Exception ex) {
                                        LOG.log(Level.SEVERE, null, ex);
                                    }
                                }

                                RPCRequest _rpcRequest;
                                while ((_rpcRequest = _respondList.get(_idSet.id)) != null) {
                                    _idSet.id++;
                                    if (_idSet.id > 1073741823) {
                                        _idSet.id = 1;
                                    }

                                    respond = invoke(requestTypeId, ((List<Object>) _rpcRequest.requestArgs).toArray());

                                    _rpcRequest.respond = respond;
                                    _rpcRequest.responded = true;

                                    if (!method.noRespond) {
                                        try {
                                            respond(_rpcRequest.requestId, _rpcRequest.requestTypeId, respond);
                                        } catch (Exception ex) {
                                            LOG.log(Level.SEVERE, null, ex);
                                        }
                                    }
                                }
                            } else {
                                RPCRequest rpcRequest = _respondList.get(requestId);
                                if (rpcRequest == null) {
                                    _respondList.put(requestId, new RPCRequest(requestTypeId, requestId, System.currentTimeMillis(), content, null));
                                } else {
                                    if (rpcRequest.responded && !method.noRespond) {
                                        try {
                                            respond(rpcRequest.requestId, rpcRequest.requestTypeId, (Object[]) rpcRequest.respond);
                                        } catch (Exception ex) {
                                            LOG.log(Level.SEVERE, null, ex);
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Object respond = null;

                        _idSet = respondIdSet;
                        _respondList = respondList;
                        synchronized (_idSet) {
                            RPCRequest rpcRequest = _respondList.get(requestId);
                            if (rpcRequest == null) {
                                respond = invoke(requestTypeId, contentList.toArray());

                                rpcRequest = new RPCRequest(requestTypeId, requestId, System.currentTimeMillis(), null, respond);
                                rpcRequest.responded = true;
                                _respondList.put(requestId, rpcRequest);
                            } else {
                                respond = rpcRequest.respond;
                            }
                        }

                        if (!method.noRespond) {
                            try {
                                respond(requestId, requestTypeId, (Object[]) respond);
                            } catch (Exception ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }
            }
        });
        //</editor-fold>
    }

    public void addListener(RPCListener listener) {
        listeners.add(listener);
    }

    public void removeListener(RPCListener listener) {
        listeners.remove(listener);
    }

    public void clearListeners() {
        listeners.clear();
    }

    public List<RPCListener> getListeners() {
        return new ArrayList<RPCListener>(listeners);
    }

    @Override
    public void close() throws IOException {
        if (out != null) {
            out.close();
        }
        if (userObject != null) {
            rpcRegistry.remove(userObject);
        }
        rpcRegistry.remove(this);
        synchronized (listeners) {
            for (RPCListener listener : listeners) {
                listener.rpcClosed();
            }
        }

        List<Map<Integer, RPCRequest>> requestListList = new ArrayList<Map<Integer, RPCRequest>>();
        requestListList.add(requestList);
        for (int i = 0, iEnd = _sequentialRequestList.length; i < iEnd; i++) {
            Map<Integer, RPCRequest> _requestList = _sequentialRequestList[i];
            if (_requestList != null) {
                requestListList.add(_requestList);
            }
        }
        for (Map<Integer, RPCRequest> _requestList : requestListList) {
            synchronized (_requestList) {
                for (RPCRequest _request : _requestList.values()) {
                    if (!_request.responded) {
                        synchronized (_request) {
                            _request.notified = true;
                            _request.notifyAll();
                        }
                    }
                }
            }
        }
    }

    public void setRemoteOutput(RemoteOutput out) {
        this.out = out;
    }

    public RemoteOutput getRemoteOutput() {
        return out;
    }

    public void setUserObject(T userObject) {
        if (userObject == null) {
            this.rpcRegistry.remove(this.userObject);
            this.userObject = userObject;
        } else {
            if (this.userObject != null) {
                setUserObject(null);
            }
            this.rpcRegistry.put(userObject, this);
            this.userObject = userObject;
        }
    }

    public T getUserObject() {
        return userObject;
    }

    protected Object send(int requestTypeId, Object[] args, boolean respond, boolean blocking, boolean broadcast)
            throws IOException, UnsupportedDataTypeException, InvocationFailedException {
        if (broadcast) {
            int broadcastListIndex = args[0] instanceof Object[] ? 0 : 1;
            Object[] broadcastList = (Object[]) args[broadcastListIndex];
            args[broadcastListIndex] = null;

            for (Object _userObject : broadcastList) {
                RPC<?> _rpc = rpcRegistry.get(_userObject);
                if (_rpc == null) {
                    continue;
                }
                _rpc.send(requestTypeId, args, respond, false, false);
            }
            return null;
        }

        if (out == null) {
            return null;
        }

        if (requestTypeId >= sequentialRequestIdSet.length) {
            return null;
        }

        RPCIdSet _idSet = sequentialRequestIdSet[requestTypeId];
        Map<Integer, RPCRequest> _requestList = sequentialRequestList[requestTypeId];
        if (_requestList == null) {
            _idSet = requestIdSet;
            _requestList = requestList;
        }

        int requestId = 0;
        synchronized (_idSet) {
            while (requestId == 0 || _requestList.get(requestId) != null) {
                requestId = _idSet.id++;
                if (_idSet.id > 1073741823) {
                    _idSet.id = 1;
                }
            }
        }

        return genericSend(_requestList, false, requestTypeId, requestId, args, respond, blocking);
    }

    protected void respond(int requestId, int requestTypeId, Object[] respond)
            throws IOException, UnsupportedDataTypeException {
        try {
            genericSend(null, true, requestTypeId, requestId, respond, false, false);
        } catch (InvocationFailedException ex) {
            LOG.log(Level.SEVERE, "Respond should not cause invocation failed exception", ex);
        }
    }

    protected Object genericSend(Map<Integer, RPCRequest> requestList, boolean isRespond, int requestTypeId, int requestId, Object[] args, boolean respond, boolean blocking)
            throws IOException, UnsupportedDataTypeException, InvocationFailedException {
        byte[] packetData = packetizer.pack(isRespond, requestTypeId, requestId, Arrays.asList(args));
        return genericSend(requestList, packetData, requestId, respond, blocking);
    }

    protected Object genericSend(Map<Integer, RPCRequest> requestList, byte[] packetData, int requestId, boolean respond, boolean blocking) throws IOException, InvocationFailedException {
        // isolate this out for test purpose
        if (out == null) {
            throw new IOException("RemoteOutput is not set");
        }

        RPCRequest request = null;
        if (respond) {
            request = new RPCRequest(requestId, System.currentTimeMillis(), packetData);
            if (!requestList.containsKey(requestId)) {
                requestList.put(requestId, request);
            }
        }

        if (respond) {
            if (!blocking) {
                out.write(packetData);
                return null;
            } else {
                synchronized (request) {
                    out.write(packetData);
                    while (!request.notified) {
                        try {
                            request.wait();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Thread interruptted when waiting for respond");
                        }
                    }
                }
                if (!request.responded) {
                    throw new IOException("Connection closed");
                }
                if (request.requestFailed) {
                    throw new IOException("Request failed due to unsynchronized class registration on local and remote connection");
                }
                return request.respond;
            }
        } else {
            out.write(packetData);
            return null;
        }
    }

    protected Object[] invoke(int requestTypeId, Object[] args) {
        if (requestTypeId == 0) {
            if (args.length == 2 && args[0] instanceof Integer && args[1] instanceof Integer) {
                // respondId notification
                int _targetSequenceId = (Integer) args[0];
                int _targetRespondedId = (Integer) args[1];

                if (_targetSequenceId >= _sequentialRespondIdSet.length) {
                    return new Object[]{null, RPCError.REMOTE_CONNECTION_SEQUENTIAL_ID_NOT_REGISTERED.getValue()};
                }

                RPCIdSet _idSet = _sequentialRespondIdSet[_targetSequenceId];
                Map<Integer, RPCRequest> _respondList = _sequentialRespondList[_targetSequenceId];
                if (_idSet == null) {
                    return new Object[]{null, RPCError.REMOTE_CONNECTION_SEQUENTIAL_ID_NOT_REGISTERED.getValue()};
                }

                if (_targetRespondedId + 1 < _idSet.respondedId) {
                    return new Object[]{null, RPCError.RESPOND_ID_UPDATE_FAILED.getValue()};
                }
                for (int i = _idSet.respondedId; i <= _targetRespondedId; i++) {
                    _respondList.remove(i);
                }
                _idSet.respondedId = _targetRespondedId;
            } else if (args.length == 1 && args[0] instanceof Integer) {
                // respondId notification, no sequential id
                int _targetRespondedId = (Integer) args[0];

                if (_targetRespondedId + 1 < respondIdSet.respondedId) {
                    return new Object[]{null, RPCError.RESPOND_ID_UPDATE_FAILED.getValue()};
                }
                for (int i = respondIdSet.respondedId; i <= _targetRespondedId; i++) {
                    respondList.remove(i);
                }
                respondIdSet.respondedId = _targetRespondedId;
            } else {
                // heart beat: args.length == 1 && args[0] == null
            }

            return new Object[]{null};
        }

        Object[] returnObject = new Object[0];

        if (requestTypeId > localMethodMap.length) {
            LOG.log(Level.SEVERE, "requestTypeId {0} greater than array size {1}", new Object[]{requestTypeId, localMethodMap.length});
            return new Object[]{null, RPCError.REMOTE_CONNECTION_METHOD_NOT_REGISTERED.getValue()};
        }

        RPCRegistryMethod method = localMethodMap[requestTypeId];
        if (method == null) {
            LOG.log(Level.SEVERE, "method with requestTypeId {0} not found", new Object[]{requestTypeId});
            return new Object[]{null, RPCError.REMOTE_CONNECTION_METHOD_NOT_REGISTERED.getValue()};
        }

        if (method.instance == null) {
            LOG.log(Level.SEVERE, "instance with requestTypeId {0} not found", new Object[]{requestTypeId});
            return new Object[]{null, RPCError.REMOTE_CONNECTION_METHOD_INSTANCE_NOT_REGISTERED.getValue()};
        }

        if (method.userObject) {
            Object[] newArgs = new Object[args.length + 1];
            newArgs[0] = userObject;
            System.arraycopy(args, 0, newArgs, 1, args.length);
            args = newArgs;
        }

        try {
            returnObject = new Object[]{method.method.invoke(method.instance, args)};
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, null, ex);
            return new Object[]{null, RPCError.REMOTE_METHOD_INVOKE_ERROR.getValue()};
        }

        return returnObject;
    }

    public <R> R getRemote(Class<R> objClass) {
        return objClass.cast(remoteImplementations.get(objClass));
    }

    public <L> void bind(Class<L> objectClass, L instance) {
        Integer methodRegistryIndex;
        if ((methodRegistryIndex = registeredLocalClasses.get(objectClass)) == null) {
            LOG.log(Level.SEVERE, "class {0} not registered", objectClass.getName());
            return;
        }

        //for (int i = methodRegistryIndex + objectClass.getDeclaredMethods().length - 1; i >= methodRegistryIndex; i--) {
        int end = methodRegistryIndex + objectClass.getDeclaredMethods().length;
        for (int i = methodRegistryIndex; i < end; i++) {
            RPCRegistryMethod method = localMethodRegistry.get(i);
            method.instance = instance;
        }
    }

    public void unbind(Class<?> objectClass) {
        Integer methodRegistryIndex;
        if ((methodRegistryIndex = registeredLocalClasses.get(objectClass)) == null) {
            LOG.log(Level.SEVERE, "class {0} not registered", objectClass.getName());
            return;
        }

        int end = methodRegistryIndex + objectClass.getDeclaredMethods().length;
        for (int i = methodRegistryIndex; i < end; i++) {
            RPCRegistryMethod method = localMethodRegistry.get(i);
            method.instance = null;
        }
    }

    @Override
    public void feed(byte[] b, int offset, int length) {
        depacketizer.unpack(b, offset, length);
    }

    protected static class RPCIdSet {

        protected final int sequentialId;
        protected int id;
        protected int respondedId;
        //
        protected int lastRespondId;
        protected long lastRespondIdSendTime;

        protected RPCIdSet(int sequentialId) {
            this.sequentialId = sequentialId;
            id = 1;
            respondedId = 1;

            lastRespondId = 0;
            lastRespondIdSendTime = System.currentTimeMillis();
        }
    }

    protected static class RPCRequest {

        protected final int requestTypeId;
        protected final int requestId;
        protected long time;
        protected byte[] packetData;
        protected Object requestArgs;
        protected Object respond;
        protected boolean responded;
        protected boolean requestFailed;
        protected boolean notified;

        protected RPCRequest(int requestId, long time, byte[] packetData) {
            this(0, requestId, time, packetData, null, null);
        }

        protected RPCRequest(int requestTypeId, int requestId, long time, Object requestArgs, Object respond) {
            this(requestTypeId, requestId, time, null, requestArgs, respond);
        }

        private RPCRequest(int requestTypeId, int requestId, long time, byte[] packetData, Object requestArgs, Object respond) {
            this.requestTypeId = requestTypeId;
            this.requestId = requestId;
            this.time = time;
            this.packetData = packetData;
            this.requestArgs = requestArgs;
            this.respond = respond;
            responded = false;
            requestFailed = false;
            notified = false;
        }
    }
}
