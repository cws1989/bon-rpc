package rpc;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import rpc.RPCRegistry.RPCRegistryMethod;
import rpc.annotation.NoRespond;
import rpc.annotation.RequestTypeId;
import rpc.codec.CodecFactory;
import rpc.codec.Generator;
import rpc.codec.Parser;
import rpc.codec.exception.InvalidFormatException;
import rpc.codec.exception.UnsupportedDataTypeException;
import rpc.transport.RemoteInput;
import rpc.transport.RemoteOutput;
import rpc.util.ClassMaker;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class RPC implements RemoteInput {

    private static final Logger LOG = Logger.getLogger(RPC.class.getName());
    protected List<RPCRegistryMethod> localMethodRegistry;
    protected List<RPCRegistryMethod> remoteMethodRegistry;
    protected Map<Class<?>, Integer> registeredLocalClasses;
    protected Map<Class<?>, Integer> registeredRemoteClasses;
    // for send function
    protected AtomicInteger requestIdIncrementor;
    protected RemoteOutput out;
    protected byte[] packetHeader;
    //
    protected final Map<Integer, RPCRequest> requestList;
    //
    protected RPCRegistryMethod[] localMethodMap;
    protected Map<Class<?>, Object> remoteImplementations;
    //
    protected Generator generator;
    protected Parser parser;

    protected RPC(List<RPCRegistryMethod> localMethodRegistry, List<RPCRegistryMethod> remoteMethodRegistry,
            Map<Class<?>, Integer> registeredLocalClasses, Map<Class<?>, Integer> registeredRemoteClasses)
            throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException {
        this.localMethodRegistry = localMethodRegistry;
        this.remoteMethodRegistry = remoteMethodRegistry;
        this.registeredLocalClasses = registeredLocalClasses;
        this.registeredRemoteClasses = registeredRemoteClasses;

        requestIdIncrementor = new AtomicInteger(0);
        packetHeader = new byte[2];
        packetHeader[0] = (byte) 1;
        packetHeader[1] = (byte) 7;

        requestList = Collections.synchronizedMap(new HashMap<Integer, RPCRequest>());

        generator = CodecFactory.getGenerator();
        parser = CodecFactory.getParser();

        // local
        int localMethodTypeIdMax = 0;
        for (RPCRegistryMethod method : localMethodRegistry) {
            RequestTypeId requestTypeId = method.method.getAnnotation(RequestTypeId.class);
            if (requestTypeId != null && requestTypeId.value() > localMethodTypeIdMax) {
                localMethodTypeIdMax = requestTypeId.value();
            }
        }
        localMethodMap = new RPCRegistryMethod[localMethodTypeIdMax + 1];
        for (RPCRegistryMethod method : localMethodRegistry) {
            RequestTypeId requestTypeId = method.method.getAnnotation(RequestTypeId.class);
            if (requestTypeId == null || requestTypeId.value() < 0) {
                continue;
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
                LOG.log(Level.SEVERE, "local request type id duplicated, id: {0}, class: {1}, method: {2}", new Object[]{requestTypeId.value(), targetClass.getName(), method.method.getName()});
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
                    LOG.log(Level.SEVERE, "remote request type id duplicated, id: {0}, class: {1}, method: {2}", new Object[]{requestTypeIdAnnotation.value(), objClass.getName(), method.getName()});
                    continue;
                }
                requestTypeIdList.add(requestTypeIdAnnotation.value());
            }
            remoteImplementations.put(objClass, ClassMaker.makeInstance(objClass, RPC.class, this));
        }
    }

    public void setRemoteOutput(RemoteOutput out) {
        this.out = out;
    }

    protected Object send(int requestTypeId, Object[] args, boolean respond, boolean blocking)
            throws IOException, UnsupportedDataTypeException {
        if (out == null) {
            throw new IOException("RemoteOutput is not set");
        }

        //<editor-fold defaultstate="collapsed" desc="prepare requestId and requestTypeId">
        int sendBufferIndex = 0;
        byte[] sendBuffer = new byte[6];

        int requestId = 0;
        if (respond) {
            requestId = requestIdIncrementor.incrementAndGet();
            if (requestId <= 32767) {
                sendBuffer[sendBufferIndex++] = (byte) ((requestId >> 8) & 0xff);
                // first bit is 0
                sendBuffer[sendBufferIndex++] = (byte) (requestId & 0xff);
            } else if (requestId <= 4194303) {
                sendBuffer[sendBufferIndex] = (byte) ((requestId >> 16) & 0xff);
                sendBuffer[sendBufferIndex++] |= 128;
                // first bit is 1, second bit is 0
                sendBuffer[sendBufferIndex++] = (byte) ((requestId >> 8) & 0xff);
                sendBuffer[sendBufferIndex++] = (byte) (requestId & 0xff);
            } else {
                // max: 1073741823
                sendBuffer[sendBufferIndex] = (byte) ((requestId >> 24) & 0xff);
                sendBuffer[sendBufferIndex++] |= 192;
                // first bit is 1, second bit is 1
                sendBuffer[sendBufferIndex++] = (byte) ((requestId >> 16) & 0xff);
                sendBuffer[sendBufferIndex++] = (byte) ((requestId >> 8) & 0xff);
                sendBuffer[sendBufferIndex++] = (byte) (requestId & 0xff);
            }
        }

        // first bit is the packet type, 0 for send, 1 for respond
        if (requestTypeId <= 63) {
            sendBuffer[sendBufferIndex++] = (byte) (requestTypeId & 0xff);
        } else {
            // max: 16383
            sendBuffer[sendBufferIndex] = (byte) ((requestTypeId >> 8) & 0xff);
            sendBuffer[sendBufferIndex++] |= 64;
            sendBuffer[sendBufferIndex++] = (byte) (requestTypeId & 0xff);
        }
        //</editor-fold>

        byte[] content = generator.generate(Arrays.asList(args));

        //<editor-fold defaultstate="collapsed" desc="prepare packet">
        int packetLength = content.length;
        int byteLength = packetHeader.length + sendBufferIndex + (packetLength <= 32767 ? 2 : 4) + content.length + 4;

        int packetBufferIndex = 0;
        byte[] packetBuffer = new byte[byteLength];

        System.arraycopy(packetHeader, 0, packetBuffer, packetBufferIndex, packetHeader.length);
        packetBufferIndex += packetHeader.length;

        if (packetLength <= 32767) {
            packetBuffer[packetBufferIndex++] = (byte) (packetLength >> 8);
            packetBuffer[packetBufferIndex++] = (byte) (packetLength);
        } else {
            byte firstByte = (byte) (packetLength >> 24);
            firstByte |= 128;
            packetBuffer[packetBufferIndex++] = firstByte;
            packetBuffer[packetBufferIndex++] = (byte) (packetLength >> 16);
            packetBuffer[packetBufferIndex++] = (byte) (packetLength >> 8);
            packetBuffer[packetBufferIndex++] = (byte) (packetLength);
        }

        System.arraycopy(sendBuffer, 0, packetBuffer, packetBufferIndex, sendBufferIndex);
        packetBufferIndex += sendBufferIndex;
        System.arraycopy(content, 0, packetBuffer, packetBufferIndex, content.length);
        packetBufferIndex += content.length;

        CRC32 crc32 = new CRC32();
        crc32.update(sendBuffer, 0, sendBufferIndex);
        crc32.update(content);

        long crc32Value = crc32.getValue();
        packetBuffer[packetBufferIndex++] = (byte) (crc32Value >> 24);
        packetBuffer[packetBufferIndex++] = (byte) (crc32Value >> 16);
        packetBuffer[packetBufferIndex++] = (byte) (crc32Value >> 8);
        packetBuffer[packetBufferIndex++] = (byte) (crc32Value);
        //</editor-fold>

        final RPCRequest request = new RPCRequest(requestId, System.currentTimeMillis());
        if (respond) {
            requestList.put(byteLength, request);
        }

        out.write(packetBuffer);

        if (respond) {
            if (!blocking) {
                return null;
            } else {
                synchronized (request) {
                    try {
                        request.wait();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Thread interruptted when waiting for respond");
                    }
                }
                return request.respondContent;
            }
        } else {
            return null;
        }
    }

    protected Object invoke(int requestTypeId, Object[] args) {
        Object returnObject = null;

        if (requestTypeId > localMethodMap.length) {
            LOG.log(Level.SEVERE, "requestTypeId {0} greater than array size {1}", new Object[]{requestTypeId, localMethodMap.length});
            return returnObject;
        }

        RPCRegistryMethod method = localMethodMap[requestTypeId];
        if (method == null) {
            LOG.log(Level.SEVERE, "method with requestTypeId {0} not found", new Object[]{requestTypeId});
            return returnObject;
        }

        if (method.instance == null) {
            LOG.log(Level.SEVERE, "no instance for requestTypeId {0} found", new Object[]{requestTypeId});
            return null;
        }

        try {
            returnObject = method.method.invoke(method.instance, args);
        } catch (IllegalAccessException ex) {
            LOG.log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            LOG.log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }

        return returnObject;
    }

    public <T> T getRemote(Class<T> objClass) {
        return objClass.cast(remoteImplementations.get(objClass));
    }

    public void bind(Class<?> objectClass, Object instance) {
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
    protected boolean packetStarted = false;
    protected byte[] _buffer = new byte[10];
    protected int _bufferRead = 0;
    protected int _packetLength = -1;
    protected int _requestId = -1;
    protected int _requestTypeId = -1;
    protected byte[] _content;
    protected int _contentRead = 0;
    protected CRC32 _crc32 = new CRC32();

    @Override
    public void feed(byte[] b, int offset, int length) throws IOException {
        int start = offset, end = offset + length;

        if (!packetStarted) {
            while (start < end) {
                if (b[start] == packetHeader[0]) {
                    _bufferRead = 1;
                } else if (_bufferRead == 1 && b[start] == packetHeader[1]) {
                    packetStarted = true;

                    _bufferRead = 0;
                    _packetLength = -1;
                    _requestId = -1;
                    _requestTypeId = -1;
                    _content = null;
                    _contentRead = 0;
                    _crc32.reset();

                    start++;
                    break;
                } else {
                    _bufferRead = 0;
                }
                start++;
            }
        }
        if (!packetStarted) {
            return;
        }

        if (_requestTypeId == -1) {
            start = fillBuffer(b, start, end, 10);
            if (_bufferRead == 10) {
                _bufferRead = 0;

                if ((_buffer[_bufferRead] & 128) == 0) {
                    _packetLength |= (_buffer[_bufferRead++] & 0xff) << 8;
                    _packetLength |= (_buffer[_bufferRead++] & 0xff);
                } else {
                    _buffer[_bufferRead] &= 127;
                    _packetLength |= (_buffer[_bufferRead++] & 0xff) << 24;
                    _packetLength |= (_buffer[_bufferRead++] & 0xff) << 16;
                    _packetLength |= (_buffer[_bufferRead++] & 0xff) << 8;
                    _packetLength |= (_buffer[_bufferRead++] & 0xff);
                }


                if ((_buffer[_bufferRead] & 128) == 0) {
                    _requestId |= (_buffer[_bufferRead++] & 0xff) << 8;
                    _requestId |= (_buffer[_bufferRead++] & 0xff);
                } else {
                    if ((_buffer[_bufferRead] & 64) == 0) {
                        _requestId |= (_buffer[_bufferRead++] & 0xff) << 16;
                        _requestId |= (_buffer[_bufferRead++] & 0xff) << 8;
                        _requestId |= (_buffer[_bufferRead++] & 0xff);
                    } else {
                        _requestId |= (_buffer[_bufferRead++] & 0xff) << 24;
                        _requestId |= (_buffer[_bufferRead++] & 0xff) << 16;
                        _requestId |= (_buffer[_bufferRead++] & 0xff) << 8;
                        _requestId |= (_buffer[_bufferRead++] & 0xff);
                    }
                }

                if ((_buffer[_bufferRead] & 64) == 0) {
                    _buffer[_bufferRead] &= 63;
                    _requestTypeId = (_buffer[_bufferRead++] & 0xff);
                } else {
                    _buffer[_bufferRead] &= 63;
                    _requestTypeId |= (_buffer[_bufferRead++] & 0xff) << 8;
                    _requestTypeId |= (_buffer[_bufferRead++] & 0xff);
                }

                _crc32.update(_buffer, 0, _bufferRead);
                _content = new byte[_packetLength];

                if (_bufferRead != 10) {
                    int byteToRead = 10 - _bufferRead;
                    if (byteToRead != 0) {
                        _contentRead = byteToRead;
                        System.arraycopy(_buffer, _bufferRead, _content, 0, byteToRead);
                    }
                }

                _bufferRead = 0;
            }
        }
        if (_bufferRead != 10) {
            return;
        }

        if (_contentRead != _packetLength) {
            int byteToRead = _packetLength - _contentRead;
            if (end - start < byteToRead) {
                byteToRead = end - start;
            }
            System.arraycopy(b, start, _buffer, _contentRead, byteToRead);
            _contentRead += byteToRead;

            if (_contentRead == _packetLength) {
                _crc32.update(_content);
            }
        }
        if (_contentRead != _packetLength) {
            return;
        }

        start = fillBuffer(b, start, end, 4);
        if (_bufferRead == 4) {
            long crc32 = 0;
            crc32 |= (_buffer[_bufferRead++] & 0xff) << 24;
            crc32 |= (_buffer[_bufferRead++] & 0xff) << 16;
            crc32 |= (_buffer[_bufferRead++] & 0xff) << 8;
            crc32 |= (_buffer[_bufferRead++] & 0xff);

            if (crc32 == _crc32.getValue()) {
                try {
                    List<Object> parsedResult = (List<Object>) parser.parse(_content);
                    Object respond = invoke(_requestTypeId, parsedResult.toArray());
                    // respond according to the requestId
                } catch (InvalidFormatException ex) {
                    LOG.log(Level.WARNING, null, ex);
                } finally {
                    packetStarted = false;
                }
            }
        }
    }

    protected int fillBuffer(byte[] b, int start, int end, int fillSize) {
        int byteToRead = fillSize - _bufferRead;
        if (end - start < byteToRead) {
            byteToRead = end - start;
        }
        System.arraycopy(b, start, _buffer, _bufferRead, byteToRead);
        _bufferRead += byteToRead;
        return start;
    }

    protected static class RPCRequest {

        protected final int requestId;
        protected long sendTime;
        protected Object respondContent;

        protected RPCRequest(int requestId, long sendTime) {
            this.requestId = requestId;
            this.sendTime = sendTime;
            respondContent = null;
        }
    }
}
