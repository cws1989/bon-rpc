package rpc;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import rpc.packet.DepacketizerListener;
import rpc.packet.DefaultDepacketizer;
import rpc.packet.DefaultPacketizer;
import org.junit.Test;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import static org.junit.Assert.*;
import rpc.packet.Packet;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class PacketizerTest {

    public PacketizerTest() {
    }

    protected static String getClassName() {
        return new Object() {
        }.getClass().getEnclosingClass().getName();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.out.println("***** " + getClassName() + " *****");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        System.out.println("******************************\r\n");
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }
    protected boolean isRespond;
    protected int requestTypeId;
    protected int requestId;
    protected Object[] args;

    @Test
    public void test() throws Throwable {
        boolean[] isRespondList = new boolean[]{true, false};
        int[] requestTypeIdList = new int[]{0, 1, 63, 64, 16383};
        int[] requestIdList = new int[]{1, 32767, 32768,
            4194303, 4194304,
            1073741823};
        int[] contentLengthList = new int[]{0, 1, 2, 255, 256, 32767, 32768, 65535, 65536};

        DefaultPacketizer packetizer = new DefaultPacketizer();
        final AtomicBoolean packetReceived = new AtomicBoolean(false);

        DepacketizerListener depacketizerListener = new DepacketizerListener() {

            @Override
            public void packetReceived(Packet packet) {
                packetReceived.set(true);
                assertEquals(isRespond, packet.isRespond());
                assertEquals(requestTypeId, packet.getRequestTypeId());
                assertEquals(requestId, packet.getRequestId());
                assertTrue(ArgumentsAssert.assertEquals(args, ((List<Object>) packet.getContent()).toArray()));
            }
        };
        byte[] buffer = new byte[17];

        for (boolean _isRespond : isRespondList) {
            isRespond = _isRespond;
            for (int _requestTypeId : requestTypeIdList) {
                requestTypeId = _requestTypeId;
                for (int _requestId : requestIdList) {
                    requestId = _requestId;
                    for (int _contentLength : contentLengthList) {
                        Object[] _args = new Object[]{CodecTest.generateByte(Math.max(_contentLength - 4, 0))};
                        args = _args;

                        byte[] packetByte = packetizer.pack(_isRespond, _requestTypeId, _requestId, Arrays.asList(_args));
                        int packetByteLength = packetByte.length;

                        packetReceived.set(false);
                        DefaultDepacketizer depacketizer = new DefaultDepacketizer();
                        depacketizer.addListener(depacketizerListener);
                        depacketizer.unpack(packetByte, 0, packetByte.length);
                        assertTrue(packetReceived.get());

                        for (int segmentLength : Arrays.asList(new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 13, 16, 17})) {
                            if (segmentLength > packetByteLength) {
                                continue;
                            }
                            packetReceived.set(false);
                            depacketizer = new DefaultDepacketizer();
                            depacketizer.addListener(depacketizerListener);
                            for (int i = 0, iEnd = packetByteLength; i < iEnd; i += segmentLength) {
                                int remaining = i + segmentLength > packetByteLength ? packetByteLength - i : segmentLength;
                                System.arraycopy(packetByte, i, buffer, 0, remaining);
                                depacketizer.unpack(buffer, 0, remaining);
                            }
                            assertTrue(packetReceived.get());
                        }
                    }
                }
            }
        }
    }
}
