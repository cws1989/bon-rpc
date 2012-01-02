package rpc;

import java.io.IOException;
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
    protected final List<RPCRegistryMethod> localMethodRegistry;
    protected final List<RPCRegistryMethod> remoteMethodRegistry;
    protected final Map<Class<?>, Integer> registeredLocalClasses;
    protected final Map<Class<?>, Integer> registeredRemoteClasses;
    //
    protected final byte[] packetHeader;
    protected final AtomicInteger requestIdIncrementor;
    protected RemoteOutput out;
    //
    protected final Map<Integer, RPCRequest> requestList;
    //
    protected final RPCRegistryMethod[] localMethodMap;
    protected final Map<Class<?>, Object> remoteImplementations;
    //
    protected final PacketParser packetParser;

    protected RPC(List<RPCRegistryMethod> localMethodRegistry, List<RPCRegistryMethod> remoteMethodRegistry,
            Map<Class<?>, Integer> registeredLocalClasses, Map<Class<?>, Integer> registeredRemoteClasses)
            throws NotFoundException, CannotCompileException, InstantiationException, IllegalAccessException {
        this.localMethodRegistry = localMethodRegistry;
        this.remoteMethodRegistry = remoteMethodRegistry;
        this.registeredLocalClasses = registeredLocalClasses;
        this.registeredRemoteClasses = registeredRemoteClasses;

        packetHeader = new byte[2];
        packetHeader[0] = (byte) 1;
        packetHeader[1] = (byte) 7;
        requestIdIncrementor = new AtomicInteger(0);

        requestList = Collections.synchronizedMap(new HashMap<Integer, RPCRequest>());

        //<editor-fold defaultstate="collapsed" desc="local">
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
        //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="remote">
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
        //</editor-fold>

        packetParser = new PacketParser();
    }

    public void setRemoteOutput(RemoteOutput out) {
        this.out = out;
    }

    protected Object send(int requestTypeId, Object[] args, boolean respond, boolean blocking)
            throws IOException, UnsupportedDataTypeException {
        int requestId = respond ? requestIdIncrementor.incrementAndGet() : 0;
        return genericSend(false, requestId, requestTypeId, args, respond, blocking);
    }

    protected void respond(int requestId, int requestTypeId, Object respond)
            throws IOException, UnsupportedDataTypeException {
        genericSend(true, requestId, requestTypeId, new Object[]{respond}, false, false);
    }

    protected Object genericSend(boolean isRespond, int requestId, int requestTypeId, Object[] args, boolean respond, boolean blocking)
            throws IOException, UnsupportedDataTypeException {
        if (out == null) {
            throw new IOException("RemoteOutput is not set");
        }

        //<editor-fold defaultstate="collapsed" desc="prepare requestId and requestTypeId">
        int sendBufferIndex = 0;
        byte[] sendBuffer = new byte[6];

        if (requestId != 0) {
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
        sendBuffer[sendBufferIndex] = isRespond ? (byte) 128 : (byte) 0;

        if (requestTypeId <= 63) {
            sendBuffer[sendBufferIndex++] |= (byte) (requestTypeId & 0xff);
        } else {
            // max: 16383
            sendBuffer[sendBufferIndex] |= (byte) ((requestTypeId >> 8) & 0xff);
            sendBuffer[sendBufferIndex++] |= 64;
            sendBuffer[sendBufferIndex++] = (byte) (requestTypeId & 0xff);
        }
        //</editor-fold>

        byte[] content = CodecFactory.getGenerator().generate(Arrays.asList(args));
        if (content == null) {
            throw new IOException("error occurred when packing the data");
        }

        //<editor-fold defaultstate="collapsed" desc="prepare packet">
        int packetLength = content.length;
        int byteLength = 2 + (packetLength <= 32767 ? 2 : 4) + sendBufferIndex + content.length + 4;

        int packetBufferIndex = 0;
        byte[] packetBuffer = new byte[byteLength];

        System.arraycopy(packetHeader, 0, packetBuffer, packetBufferIndex, 2);
        packetBufferIndex += 2;

        if (packetLength <= 32767) {
            packetBuffer[packetBufferIndex++] = (byte) (packetLength >> 8);
            packetBuffer[packetBufferIndex++] = (byte) (packetLength);
        } else {
            packetBuffer[packetBufferIndex] = (byte) (packetLength >> 24);
            packetBuffer[packetBufferIndex++] |= 128;
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

        RPCRequest request = new RPCRequest(requestId, System.currentTimeMillis());
        if (respond) {
            requestList.put(requestId, request);
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
        } catch (Exception ex) {
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

    @Override
    public void feed(byte[] b, int offset, int length) throws IOException {
        packetParser.feed(b, offset, length);
    }

    protected class PacketParser implements RemoteInput {

        protected final Parser parser;
        //
        protected boolean packetStarted = false;
        protected int _headerRead = 0;
        protected final byte[] _buffer = new byte[10];
        protected int _bufferRead = 0;
        protected int _packetLength = -1;
        protected int _requestId = -1;
        protected boolean _isRespond = false;
        protected int _requestTypeId = -1;
        protected byte[] _content;
        protected int _contentRead = 0;
        protected final byte[] _crcBuffer = new byte[4];
        protected int _crcBufferRead = 0;
        protected boolean _crcMatched = false;
        protected final CRC32 _crc32 = new CRC32();

        protected PacketParser() {
            parser = CodecFactory.getParser();
        }

        @Override
        public void feed(byte[] b, int offset, int length) throws IOException {
            int start = offset, end = offset + length;

            //<editor-fold defaultstate="collapsed" desc="read packet header">
            if (!packetStarted) {
                while (start < end) {
                    try {
                        if (b[start] == packetHeader[0]) {
                            _headerRead = 1;
                        } else if (_headerRead == 1 && b[start] == packetHeader[1]) {
                            packetStarted = true;
                            _headerRead = 0;

                            _bufferRead = 0;
                            _packetLength = -1;
                            _requestId = -1;
                            _isRespond = false;
                            _requestTypeId = -1;
                            _content = null;
                            _contentRead = 0;
                            _crcBufferRead = 0;
                            _crcMatched = false;
                            _crc32.reset();

                            break;
                        } else {
                            _headerRead = 0;
                        }
                    } finally {
                        start++;
                    }
                }
            }
            if (!packetStarted) {
                return;
            }
            //</editor-fold>

            //<editor-fold defaultstate="collapsed" desc="read packetLength, requestId, isRespond and requestTypeId">
            if (_requestTypeId == -1) {
                start = fillBuffer(b, start, end);
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
                            _buffer[_bufferRead] &= 63;
                            _requestId |= (_buffer[_bufferRead++] & 0xff) << 16;
                            _requestId |= (_buffer[_bufferRead++] & 0xff) << 8;
                            _requestId |= (_buffer[_bufferRead++] & 0xff);
                        } else {
                            _buffer[_bufferRead] &= 63;
                            _requestId |= (_buffer[_bufferRead++] & 0xff) << 24;
                            _requestId |= (_buffer[_bufferRead++] & 0xff) << 16;
                            _requestId |= (_buffer[_bufferRead++] & 0xff) << 8;
                            _requestId |= (_buffer[_bufferRead++] & 0xff);
                        }
                    }

                    _isRespond = (_buffer[_bufferRead] & 128) != 0;

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
                        int _newBufferRead = fillContent(_buffer, _bufferRead, 10);
                        if (_newBufferRead != 10) {
                            _newBufferRead = fillCRCBuffer(_buffer, _newBufferRead, 10);
                        }
                        if (_newBufferRead != 10) {
                            LOG.log(Level.SEVERE, "buffer not consumed");
                        }
                    }
                }
            }
            if (_requestTypeId == -1) {
                return;
            }
            //</editor-fold>

            //<editor-fold defaultstate="collapsed" desc="read content">
            if (_contentRead != _packetLength) {
                start = fillContent(b, start, end);

                if (_contentRead == _packetLength) {
                    _crc32.update(_content);
                }
            }
            if (_contentRead != _packetLength) {
                return;
            }
            //</editor-fold>

            //<editor-fold defaultstate="collapsed" desc="read crc32">
            if (_crcBufferRead != 4) {
                start = fillCRCBuffer(b, start, end);

                if (_crcBufferRead == 4) {
                    packetStarted = false;

                    long crc32 = 0;
                    crc32 |= (_crcBuffer[0] & 0xff) << 24;
                    crc32 |= (_crcBuffer[1] & 0xff) << 16;
                    crc32 |= (_crcBuffer[2] & 0xff) << 8;
                    crc32 |= (_crcBuffer[3] & 0xff);

                    _crcMatched = crc32 == _crc32.getValue();
                }
            }
            if (_crcBufferRead != 4) {
                return;
            }
            //</editor-fold>

            if (_crcMatched) {
                Object content = null;
                try {
                    content = parser.parse(_content);
                } catch (InvalidFormatException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                    return;
                }

                if (_isRespond) {
                    RPCRequest request = requestList.remove(_requestId);
                    if (request != null) {
                        request.respondContent = content;
                        synchronized (request) {
                            request.notifyAll();
                        }
                    }
                } else {
                    RPCRegistryMethod method = localMethodMap[_requestTypeId];
                    if (method == null) {
                        LOG.log(Level.SEVERE, "method with requestTypeId {0} not found", new Object[]{_requestTypeId});
                        return;
                    }

                    Object respond = null;
                    try {
                        respond = invoke(_requestTypeId, ((List<Object>) content).toArray());
                    } catch (ClassCastException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                        return;
                    }

                    if (!method.noRespond) {
                        try {
                            respond(_requestId, _requestTypeId, respond);
                        } catch (UnsupportedDataTypeException ex) {
                            LOG.log(Level.SEVERE, null, ex);
                        }
                    }
                }
            } else {
                // crc failed, put the packet (except the header) back for reading again

                byte[] __buffer = new byte[_bufferRead];
                System.arraycopy(_buffer, 0, __buffer, 0, _bufferRead);
                feed(__buffer, 0, _bufferRead);

                byte[] __content = new byte[_packetLength];
                System.arraycopy(_content, 0, __content, 0, _packetLength);
                feed(__content, 0, _packetLength);

                byte[] __crcBuffer = new byte[4];
                System.arraycopy(_crcBuffer, 0, __crcBuffer, 0, 4);
                feed(__crcBuffer, 0, 4);
            }

            if (start != end) {
                feed(b, start, end - start);
            }
        }

        protected int fillBuffer(byte[] b, int start, int end) {
            // b != null
            // start >= 0, end >= 0, end <= start
            // _bufferRead >= 0, _bufferRead <= 10
            int maxLength = end - start;
            int byteToRead = 10 - _bufferRead;
            if (maxLength < byteToRead) {
                byteToRead = maxLength;
            }
            if (byteToRead == 0) {
                return start;
            }
            System.arraycopy(b, start, _buffer, _bufferRead, byteToRead);
            _bufferRead += byteToRead;
            return start + byteToRead;
        }

        protected int fillContent(byte[] b, int start, int end) {
            // b != null
            // start >= 0, end >= 0, end <= start
            // _contentRead >= 0, _contentRead <= _packetLength
            // _packetLength >= 0
            // _contentRead <= _packetLength
            // _content != null
            // _content.length == _packetLength
            int maxLength = end - start;
            int byteToRead = _packetLength - _contentRead;
            if (maxLength < byteToRead) {
                byteToRead = maxLength;
            }
            if (byteToRead == 0) {
                return start;
            }
            System.arraycopy(b, start, _content, _contentRead, byteToRead);
            _contentRead += byteToRead;
            return start + byteToRead;
        }

        protected int fillCRCBuffer(byte[] b, int start, int end) {
            // b != null
            // start >= 0, end >= 0, end <= start
            // _crcBufferRead >= 0, _crcBufferRead <= 4
            int maxLength = end - start;
            int byteToRead = 4 - _crcBufferRead;
            if (maxLength < byteToRead) {
                byteToRead = maxLength;
            }
            if (byteToRead == 0) {
                return start;
            }
            System.arraycopy(b, start, _crcBuffer, _crcBufferRead, byteToRead);
            _crcBufferRead += byteToRead;
            return start + byteToRead;
        }
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
