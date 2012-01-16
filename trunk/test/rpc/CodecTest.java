package rpc;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import rpc.codec.CodecFactory;
import static org.junit.Assert.*;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class CodecTest {

    public CodecTest() {
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

    @Test
    public void test() throws Throwable {
        List<Object> objectList = new ArrayList<Object>();
        // Short
        objectList.addAll(Arrays.asList(new Object[]{
                    (short) 0, (short) 255, (short) 256, (short) -1, (short) -256, (short) -257,
                    Short.MIN_VALUE, Short.MAX_VALUE
                }));
        // Integer
        objectList.addAll(Arrays.asList(new Object[]{
                    0, 255, 256, -1, -256, -257,
                    65535, 65536, -65536, -65537,
                    16777215, 16777216, -16777216, -16777217,
                    Integer.MIN_VALUE, Integer.MAX_VALUE
                }));
        // Long
        objectList.addAll(Arrays.asList(new Object[]{
                    0L, 255L, 256L, -1L, -256L, -257L,
                    65535L, 65536L, -65536L, -65537L,
                    16777215L, 16777216L, -16777216L, -16777217L,
                    4294967295L, 4294967296L, -4294967296L, -4294967297L,
                    281474976710655L, 281474976710656L, -281474976710656L, -281474976710657L,
                    Long.MIN_VALUE, Long.MAX_VALUE
                }));
        // Float
        objectList.addAll(Arrays.asList(new Object[]{
                    0F, 255F, 256F, -1F, -256F, -257F,
                    65535F, 65536F, -65536F, -65537F,
                    16777215F, 16777216F, -16777216F, -16777217F,
                    Float.MIN_VALUE, Float.MAX_VALUE
                }));
        // Double
        objectList.addAll(Arrays.asList(new Object[]{
                    (double) 0L, (double) 255L, (double) 256L, (double) -1L, (double) -256L, (double) -257L,
                    (double) 65535L, (double) 65536L, (double) -65536L, (double) -65537L,
                    (double) 16777215L, (double) 16777216L, (double) -16777216L, (double) -16777217L,
                    (double) 4294967295L, (double) 4294967296L, (double) -4294967296L, (double) -4294967297L,
                    (double) 281474976710655L, (double) 281474976710656L, (double) -281474976710656L, (double) -281474976710657L,
                    Double.MIN_VALUE, Double.MAX_VALUE
                }));
        // String
        objectList.addAll(Arrays.asList(new Object[]{generateString(0), generateString(1), generateString(255), generateString(256), generateString(16777215)}));
        // byte[]
        objectList.addAll(Arrays.asList(new Object[]{generateByte(0), generateByte(1), generateByte(255), generateByte(256), generateByte(16777215)}));
        // other
        objectList.addAll(Arrays.asList(new Object[]{true, false, null}));

        List<Object> resultList = (List<Object>) CodecFactory.getParser().parse(CodecFactory.getGenerator().generate(objectList));
        assertTrue(ArgumentsAssert.assertEquals(objectList, resultList));

        Map<Object, Object> objectMap = new HashMap<Object, Object>();
        for (Object key : objectList) {
            if (key instanceof byte[]) {
                continue;
            }
            objectMap.put(key, key);
        }

        Map<Object, Object> resultMap = (Map<Object, Object>) CodecFactory.getParser().parse(CodecFactory.getGenerator().generate(objectMap));
        assertTrue(ArgumentsAssert.assertEquals(objectMap, resultMap));
    }

    public static byte[] generateByte(int length) {
        byte[] returnValue = new byte[length];
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            returnValue[i] = (byte) random.nextInt(256);
        }
        return returnValue;
    }
    public static final char[] selection = ("\t\r\n"/*3*/
            + " !\"#$%&'()*+,-./0123456789:;<=>?"/*32*/
            + "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_"/*32*/
            + "`abcdefghijklmnopqrstuvwxyz{|}~"/*31*/).toCharArray();

    public static String generateString(int byteLength) {
        StringBuilder sb = new StringBuilder(byteLength);

        int stringLength = selection.length;

        Random random = new Random();
        for (int i = 0; i < byteLength; i++) {
            sb.append(selection[random.nextInt(stringLength)]);
        }

        return sb.toString();
    }
}
