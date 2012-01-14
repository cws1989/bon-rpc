package rpc;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import rpc.transport.RemoteInput;
import rpc.transport.RemoteOutput;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class Simulator implements RemoteInput, RemoteOutput {

    private static final Logger LOG = Logger.getLogger(Simulator.class.getName());
    protected final RPC localRPC;
    protected final RPC[] remoteRPC;
    protected final Queue<Packet> receiveQueue;
    protected final Queue<Packet> sendQueue;
    protected Thread receiveThread;
    protected Thread sendThread;

    protected Simulator(RPC localRPC, RPC... remoteRPC) {
        this.localRPC = localRPC;
        this.remoteRPC = remoteRPC;
        this.receiveQueue = new ArrayDeque<Packet>();
        this.sendQueue = new ArrayDeque<Packet>();

        receiveThread = new Thread(new Runnable() {

            @Override
            public void run() {
                Packet packet;
                while (true) {
                    while (true) {
                        synchronized (receiveQueue) {
                            if ((packet = receiveQueue.poll()) == null) {
                                break;
                            }
                        }
                        try {
                            Simulator.this.localRPC.feed(packet.b, packet.offset, packet.length);
                        } catch (IOException ex) {
                            LOG.log(Level.SEVERE, null, ex);
                        }
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
                        for (RPC rpc : Simulator.this.remoteRPC) {
                            try {
                                rpc.feed(packet.b, packet.offset, packet.length);
                            } catch (IOException ex) {
                                LOG.log(Level.SEVERE, null, ex);
                            }
                        }
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
    public void feed(byte[] b, int offset, int length) throws IOException {
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