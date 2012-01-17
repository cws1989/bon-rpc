package rpc;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import rpc.transport.RemoteInput;
import rpc.transport.RemoteOutput;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class Simulator implements RemoteInput, RemoteOutput {

    private static final Logger LOG = Logger.getLogger(Simulator.class.getName());
    protected final RPC<Integer> localRPC;
    protected RemoteInput[] remoteRPC;
    protected final Queue<Packet> receiveQueue;
    protected final Queue<Packet> sendQueue;
    protected Thread receiveThread;
    protected Thread sendThread;
    protected final AtomicInteger sequenceId;
    protected Map<Integer, Error> errorList;

    protected Simulator(RPC<Integer> localRPC) {
        this.localRPC = localRPC;
        this.receiveQueue = new ArrayDeque<Packet>();
        this.sendQueue = new ArrayDeque<Packet>();

        receiveThread = new Thread(new Runnable() {

            @Override
            public void run() {
                Random random = new Random();
                Packet packet;
                while (true) {
                    while (true) {
                        synchronized (receiveQueue) {
                            if ((packet = receiveQueue.poll()) == null) {
                                break;
                            }
                        }
                        synchronized (sequenceId) {
                            byte[] feedBytes = null;
                            Error error = null;
                            if ((error = errorList.get(sequenceId.getAndIncrement())) != null) {
                                switch (error.errorMode) {
                                    case HEAD:
                                        feedBytes = new byte[packet.length + error.byteLength];
                                        for (int i = 0, iEnd = error.byteLength; i < iEnd; i++) {
                                            feedBytes[i] = (byte) random.nextInt(256);
                                        }
                                        System.arraycopy(packet.b, packet.offset, feedBytes, error.byteLength, packet.length);
                                        break;
                                    case CONTENT:
                                        feedBytes = new byte[packet.length];
                                        System.arraycopy(packet.b, packet.offset, feedBytes, 0, packet.length);
                                        for (int i = 0, iEnd = error.byteLength; i < iEnd; i++) {
                                            // don't care about duplicate index for laziness
                                            feedBytes[random.nextInt(packet.length)] = (byte) random.nextInt(256);
                                        }
                                        break;
                                    case TAIL:
                                        feedBytes = new byte[packet.length + error.byteLength];
                                        System.arraycopy(packet.b, packet.offset, feedBytes, 0, packet.length);
                                        for (int i = packet.offset + packet.length, iEnd = packet.offset + packet.length + error.byteLength; i < iEnd; i++) {
                                            feedBytes[i] = (byte) random.nextInt(256);
                                        }
                                        break;
                                    case DISCARD:
                                        break;
                                }
                            } else {
                                if (packet.offset != 0 && packet.length != packet.b.length) {
                                    feedBytes = new byte[packet.length];
                                    System.arraycopy(packet.b, packet.offset, feedBytes, 0, packet.length);
                                } else {
                                    feedBytes = packet.b;
                                }
                            }
                            if (feedBytes != null) {
                                Simulator.this.localRPC.feed(feedBytes, 0, feedBytes.length);
                            }
                        }
                    }

                    if (Thread.interrupted()) {
                        break;
                    }

                    synchronized (receiveQueue) {
                        if (receiveQueue.isEmpty()) {
                            try {
                                receiveQueue.wait();
                            } catch (InterruptedException ex) {
                                break;
                            }
                        }
                    }
                }
            }
        }, "Simulator - receive");
        receiveThread.start();

        sendThread = new Thread(new Runnable() {

            @Override
            public void run() {
                Packet packet;
                while (true) {
                    while (true) {
                        synchronized (sendQueue) {
                            if ((packet = sendQueue.poll()) == null) {
                                break;
                            }
                        }
                        for (RemoteInput rpc : Simulator.this.remoteRPC) {
                            rpc.feed(packet.b, packet.offset, packet.length);
                        }
                    }

                    if (Thread.interrupted()) {
                        break;
                    }

                    synchronized (sendQueue) {
                        if (sendQueue.isEmpty()) {
                            try {
                                sendQueue.wait();
                            } catch (InterruptedException ex) {
                                break;
                            }
                        }
                    }
                }
            }
        }, "Simulator - send");
        sendThread.start();

        sequenceId = new AtomicInteger(0);
        errorList = Collections.synchronizedMap(new HashMap<Integer, Error>());
    }

    public void setRemoteRPC(RemoteInput... remoteRPC) {
        this.remoteRPC = new RemoteInput[remoteRPC.length];
        System.arraycopy(remoteRPC, 0, this.remoteRPC, 0, remoteRPC.length);
    }

    public void addReceiveError(int sequence, ErrorMode errorMode, int byteLength) throws Exception {
        synchronized (sequenceId) {
            if (errorList.containsKey(sequence)) {
                throw new Exception("Sequence already exist");
            }
            if (sequence < sequenceId.get()) {
                throw new Exception(String.format("sequence passed, input: %1$d, current: %2$d", sequence, sequenceId.get()));
            }
            errorList.put(sequence, new Error(sequence, errorMode, byteLength));
        }
    }

    public synchronized void stop() {
        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }
        if (sendThread != null) {
            sendThread.interrupt();
            sendThread = null;
        }
    }

    @Override
    public void feed(byte[] b, int offset, int length) {
        synchronized (receiveQueue) {
            receiveQueue.add(new Packet(b, offset, length));
            receiveQueue.notifyAll();
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        synchronized (sendQueue) {
            sendQueue.add(new Packet(b, 0, b.length));
            sendQueue.notifyAll();
        }
    }

    @Override
    public void close() throws IOException {
        stop();
    }

    public static enum ErrorMode {

        HEAD, CONTENT, TAIL, DISCARD;
    };

    protected static class Error {

        protected int sequence;
        protected ErrorMode errorMode;
        protected int byteLength;

        protected Error(int sequence, ErrorMode errorMode, int byteLength) {
            this.sequence = sequence;
            this.errorMode = errorMode;
            this.byteLength = byteLength;
        }
    }

    protected static class Packet {

        protected final byte[] b;
        protected final int offset;
        protected final int length;

        protected Packet(byte[] b, int offset, int length) {
            this.b = b;
            this.offset = offset;
            this.length = length;
        }
    }
}