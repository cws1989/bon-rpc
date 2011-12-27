package rpc;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import rpc.RPCRegistry.RPCRegistryMethod;
import rpc.annotation.NoRequestId;
import rpc.annotation.NoRespond;
import rpc.annotation.RequestTypeId;
import rpc.codec.Generator;
import rpc.codec.Parser;
import rpc.codec.exception.UnsupportedDataTypeException;
import rpc.exception.RequestExpiredException;
import rpc.transport.RemoteInput;
import rpc.util.ClassMaker;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class RPC implements RemoteInput {

    protected List<RPCRegistryMethod> localMethodRegistry;
    protected List<RPCRegistryMethod> remoteMethodRegistry;
    protected Map<Class<?>, Integer> registeredLocalClasses;
    protected Map<Class<?>, Integer> registeredRemoteClasses;
    //
    protected int localMethodTypeIdMax;
    protected RPCRegistryMethod[] localMethodMap;
    protected Map<Class<?>, Object> remoteImplementations;
    // for send function
    private int requestIdIncrementor;
    private byte[] sendBuffer;
    //
    private Generator generator;
    private Parser parser;

    protected RPC(List<RPCRegistryMethod> localMethodRegistry, List<RPCRegistryMethod> remoteMethodRegistry, Map<Class<?>, Integer> registeredLocalClasses, Map<Class<?>, Integer> registeredRemoteClasses) throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException {
        this.localMethodRegistry = localMethodRegistry;
        this.remoteMethodRegistry = remoteMethodRegistry;
        this.registeredLocalClasses = registeredLocalClasses;
        this.registeredRemoteClasses = registeredRemoteClasses;

        this.requestIdIncrementor = 1;
        this.sendBuffer = new byte[4];

        initialize();
    }

    private void initialize() throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException {
        // local
        for (RPCRegistryMethod method : localMethodRegistry) {
            RequestTypeId requestTypeId = method.method.getAnnotation(RequestTypeId.class);
            if (requestTypeId != null && requestTypeId.value() > localMethodTypeIdMax) {
                localMethodTypeIdMax = requestTypeId.value();
            }
        }
        localMethodMap = new RPCRegistryMethod[localMethodTypeIdMax + 1];
        for (RPCRegistryMethod method : localMethodRegistry) {
            RequestTypeId requestTypeId = method.method.getAnnotation(RequestTypeId.class);
            if (requestTypeId == null) {
                continue;
            }

            NoRequestId noRequestId = method.method.getAnnotation(NoRequestId.class);
            if (noRequestId != null) {
                method.noRequestId = true;
            }

            NoRespond noRespond = method.method.getAnnotation(NoRespond.class);
            if (noRespond != null) {
                method.noRespond = true;
            }

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
                Logger.getLogger(RPC.class.getName()).log(Level.SEVERE, "RPC:initialize(): local request type id duplicated, id: {0}, class: {1}, method: {2}", new Object[]{requestTypeId.value(), targetClass.getName(), method.method.getName()});
                continue;
            }
            if (requestTypeId.value() < 0) {
                continue;
            }
            localMethodMap[requestTypeId.value()] = method;
        }

        // remote
        List<Integer> requestTypeIdList = new ArrayList<Integer>();
        remoteImplementations = new HashMap<Class<?>, Object>();
        for (Class<?> objClass : registeredRemoteClasses.keySet()) {
            Method[] methods = objClass.getMethods();
            for (Method method : methods) {
                RequestTypeId requestTypeIdAnnotation = method.getAnnotation(RequestTypeId.class);
                if (requestTypeIdAnnotation == null) {
                    continue;
                }
                if (requestTypeIdList.indexOf((Integer) requestTypeIdAnnotation.value()) != -1) {
                    Logger.getLogger(RPC.class.getName()).log(Level.SEVERE, "RPC:initialize(): remote request type id duplicated, id: {0}, class: {1}, method: {2}", new Object[]{requestTypeIdAnnotation.value(), objClass.getName(), method.getName()});
                    continue;
                }
                requestTypeIdList.add(requestTypeIdAnnotation.value());
            }
            remoteImplementations.put(objClass, ClassMaker.makeInstance(objClass, RPC.class, this));
        }
    }

    protected synchronized Object send(int requestTypeId, Object[] args, boolean withRequestId, boolean respond, boolean blocking, int expiryTime) throws RequestExpiredException, IOException, UnsupportedDataTypeException {
        OutputStream out = null;

        int requestId = requestIdIncrementor++;

        if (respond) {
            if (requestId < 31) {
                sendBuffer[0] = (byte) (requestId & 0xff);
                out.write(sendBuffer, 0, 1);
            } else if (requestId < 8191) {
                sendBuffer[0] = (byte) ((requestId >> 8) & 0xff);
                sendBuffer[0] |= 32;
                sendBuffer[1] = (byte) (requestId & 0xff);
                out.write(sendBuffer, 0, 2);
            } else if (requestId < 2097151) {
                sendBuffer[0] = (byte) ((requestId >> 16) & 0xff);
                sendBuffer[0] |= 64;
                sendBuffer[1] = (byte) ((requestId >> 8) & 0xff);
                sendBuffer[2] = (byte) (requestId & 0xff);
                out.write(sendBuffer, 0, 3);
            } else {
                sendBuffer[0] = (byte) ((requestId >> 24) & 0xff);
                sendBuffer[0] |= 96;
                sendBuffer[1] = (byte) ((requestId >> 16) & 0xff);
                sendBuffer[2] = (byte) ((requestId >> 8) & 0xff);
                sendBuffer[3] = (byte) (requestId & 0xff);
                out.write(sendBuffer, 0, 4);
            }
        }

        if (requestTypeId <= 63) {
            sendBuffer[0] = (byte) (requestTypeId & 0xff);
            sendBuffer[0] |= 128;
            out.write(sendBuffer, 0, 1);
        } else {
            sendBuffer[0] = (byte) ((requestTypeId >> 8) & 0xff);
            sendBuffer[0] |= 192;
            sendBuffer[1] = (byte) (requestTypeId & 0xff);
            out.write(sendBuffer, 0, 2);
        }

        generator.write(out, Arrays.asList(args));

        out.flush();

        System.out.println(requestTypeId);
        System.out.println("object size: " + args.length);
        System.out.println(withRequestId);
        System.out.println(respond);
        System.out.println(blocking);
        System.out.println(expiryTime);
        return new Object();
    }

    public Object receive(int requestTypeId, Object[] args) {
        Object returnObject = null;

        if (requestTypeId > localMethodMap.length) {
            Logger.getLogger(RPC.class.getName()).log(Level.SEVERE, "RPC:receive(): requestTypeId {0} greater than array size {1}", new Object[]{requestTypeId, localMethodMap.length});
            return returnObject;
        }

        RPCRegistryMethod method = localMethodMap[requestTypeId];
        if (method == null) {
            Logger.getLogger(RPC.class.getName()).log(Level.SEVERE, "RPC:receive(): method with requestTypeId {0} not found", new Object[]{requestTypeId});
            return returnObject;
        }

        try {
            returnObject = method.method.invoke(method.instance, args);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(RPC.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(RPC.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(RPC.class.getName()).log(Level.SEVERE, null, ex);
        }

        return returnObject;
    }

    public <T> T getRemote(Class<T> objClass) {
        return objClass.cast(remoteImplementations.get(objClass));
    }

    public void bind(Class<?> objectClass, Object instance) {
        Integer methodRegistryIndex;
        if ((methodRegistryIndex = registeredLocalClasses.get(objectClass)) == null) {
            Logger.getLogger(RPC.class.getName()).log(Level.SEVERE, "RPC:bindClass(): class {0} not registered", objectClass.getName());
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
            Logger.getLogger(RPC.class.getName()).log(Level.SEVERE, "RPC:unbindClass(): class {0} not registered", objectClass.getName());
            return;
        }

        int end = methodRegistryIndex + objectClass.getDeclaredMethods().length;
        for (int i = methodRegistryIndex; i < end; i++) {
            RPCRegistryMethod method = localMethodRegistry.get(i);
            method.instance = null;
        }
    }

    @Override
    public void feed(byte[] b) throws IOException {
    }
}
