package rpc.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import rpc.codec.exception.UnsupportedDataTypeException;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class DefaultGenerator implements Generator {

    private static final Logger LOG = Logger.getLogger(DefaultGenerator.class.getName());
    protected static final long UNSIGNED_4BYTES_MAX = 4294967295L;
    protected static final long UNSIGNED_6BYTES_MAX = 281474976710655L;
    protected OutputStream out;
    private byte[] buffer = new byte[9];

    protected DefaultGenerator() {
    }

    protected DefaultGenerator(OutputStream outputStream) {
        this.out = outputStream;
    }

    @Override
    public byte[] generate(Object data) throws UnsupportedDataTypeException {
        return generate(32, data);
    }

    public byte[] generate(int size, Object data) throws UnsupportedDataTypeException {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream(size);
            write(byteStream, data);
            return byteStream.toByteArray();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public void write(OutputStream outputStream, Object data) throws IOException, UnsupportedDataTypeException {
        DefaultGenerator generator = new DefaultGenerator(outputStream);
        if (data instanceof List) {
            generator.writeList((List<Object>) data);
        } else if (data instanceof Map) {
            generator.writeMap((Map<String, Object>) data);
        } else {
            throw new UnsupportedDataTypeException();
        }
    }

    protected void writeMap(Map<String, Object> data) throws IOException, UnsupportedDataTypeException {
        out.write(1);

        for (String itemKey : data.keySet()) {
            Object item = data.get(itemKey);

            byte[] itemKeyBytes = itemKey.getBytes();
            out.write(itemKeyBytes.length);
            out.write(itemKeyBytes);

            writeItem(item);
        }

        out.write(0);
    }

    protected void writeList(List<Object> data) throws IOException, UnsupportedDataTypeException {
        out.write(2);

        for (Object item : data) {
            writeItem(item);
        }

        out.write(0);
    }

    protected void writeItem(Object item) throws IOException, UnsupportedDataTypeException {
        if (item instanceof Integer) {
            out.write(buffer, 0, packInt(6, (Integer) item));
        } else if (item instanceof String) {
            byte[] stringBytes = ((String) item).getBytes();

            int stringBytesLength = stringBytes.length;
            if (stringBytesLength <= 255) {
                buffer[0] = 12;
                buffer[1] = (byte) stringBytesLength;
                out.write(buffer, 0, 2);
            } else if (stringBytesLength <= 16777215) {
                buffer[0] = 13;
                buffer[1] = (byte) (stringBytesLength >> 16);
                buffer[2] = (byte) (stringBytesLength >> 8);
                buffer[3] = (byte) stringBytesLength;
                out.write(buffer, 0, 4);
            } else {
                throw new UnsupportedDataTypeException();
            }

            out.write(stringBytes, 0, stringBytesLength);
        } else if (item instanceof Long) {
            out.write(buffer, 0, packLong(7, (Long) item));
        } else if (item instanceof Date) {
            out.write(buffer, 0, packLong(11, ((Date) item).getTime()));
        } else if (item instanceof Float) {
            buffer[0] = (byte) 3;

            int _int = Float.floatToRawIntBits((Float) item);
            buffer[1] = (byte) (_int >> 24);
            buffer[2] = (byte) (_int >> 16);
            buffer[3] = (byte) (_int >> 8);
            buffer[4] = (byte) _int;
            out.write(buffer, 0, 5);
        } else if (item instanceof Double) {
            buffer[0] = (byte) 4;

            long _long = Double.doubleToRawLongBits((Double) item);
            buffer[1] = (byte) (_long >> 56);
            buffer[2] = (byte) (_long >> 48);
            buffer[3] = (byte) (_long >> 40);
            buffer[4] = (byte) (_long >> 32);
            buffer[5] = (byte) (_long >> 24);
            buffer[6] = (byte) (_long >> 16);
            buffer[7] = (byte) (_long >> 8);
            buffer[8] = (byte) _long;
            out.write(buffer, 0, 9);
        } else if (item instanceof byte[]) {
            byte[] binaryBytes = (byte[]) item;

            int binaryBytesLength = binaryBytes.length;
            if (binaryBytesLength <= 255) {
                buffer[0] = (byte) 14;
                buffer[1] = (byte) binaryBytesLength;
                out.write(buffer, 0, 2);
            } else if (binaryBytesLength <= 16777215) {
                buffer[0] = (byte) 15;
                buffer[1] = (byte) (binaryBytesLength >> 16);
                buffer[2] = (byte) (binaryBytesLength >> 8);
                buffer[3] = (byte) binaryBytesLength;
                out.write(buffer, 0, 4);
            } else {
                throw new UnsupportedDataTypeException();
            }

            out.write(binaryBytes, 0, binaryBytesLength);
        } else if (item instanceof Map) {
            writeMap((Map<String, Object>) item);
        } else if (item instanceof List) {
            writeList((List<Object>) item);
        } else if (item instanceof Boolean) {
            out.write((Boolean) item ? 8 : 9);
        } else if (item instanceof Short) {
            out.write(buffer, 0, packShort(5, (Short) item));
        } else if (item == null) {
            out.write(10);
        } else {
            throw new UnsupportedDataTypeException();
        }
    }

    protected int packShort(int type, short d) {
        // record sign
        int sign = (d >> 8) & 0x80;
        type |= sign;

        if (sign != 0) {
            d = (short) -d;
        }

        if (d <= 255) {
            type |= 0x10;
            buffer[0] = (byte) type;

            buffer[1] = (byte) (d);

            return 2;
        } else {
            type |= 0x20;
            buffer[0] = (byte) type;

            buffer[1] = (byte) (d >> 8);
            buffer[2] = (byte) (d);

            return 3;
        }
    }

    protected int packInt(int type, int _int) {
        // record sign
        int sign = (_int >> 24) & 0x80;
        type |= sign;

        if (sign != 0) {
            _int = -_int;
        }

        if (_int <= 65535) {
            if (_int <= 255) {
                buffer[0] = (byte) type;

                buffer[1] = (byte) (_int);

                return 2;
            } else {
                type |= 0x10;
                buffer[0] = (byte) type;

                buffer[1] = (byte) (_int >> 8);
                buffer[2] = (byte) (_int);

                return 3;
            }
        } else {
            if (_int <= 16777215) {
                type |= 0x20;
                buffer[0] = (byte) type;

                buffer[1] = (byte) (_int >> 16);
                buffer[2] = (byte) (_int >> 8);
                buffer[3] = (byte) (_int);

                return 4;
            } else {
                type |= 0x30;
                buffer[0] = (byte) type;

                buffer[1] = (byte) (_int >> 24);
                buffer[2] = (byte) (_int >> 16);
                buffer[3] = (byte) (_int >> 8);
                buffer[4] = (byte) (_int);

                return 5;
            }
        }
    }

    protected int packLong(int type, long d) {
        // record sign
        int sign = (int) ((d >> 56) & 0x80);
        type |= sign;

        if (sign != 0) {
            d = -d;
        }

        if (d <= 16777215) {
            if (d <= 255) {
                buffer[0] = (byte) type;

                buffer[1] = (byte) (d);

                return 2;
            } else if (d <= 65535) {
                type |= 0x10;
                buffer[0] = (byte) type;

                buffer[1] = (byte) (d >> 8);
                buffer[2] = (byte) (d);

                return 3;
            } else {
                type |= 0x20;
                buffer[0] = (byte) type;

                buffer[1] = (byte) (d >> 16);
                buffer[2] = (byte) (d >> 8);
                buffer[3] = (byte) (d);

                return 4;
            }
        } else {
            if (d <= UNSIGNED_4BYTES_MAX) {
                type |= 0x30;
                buffer[0] = (byte) type;

                buffer[1] = (byte) (d >> 24);
                buffer[2] = (byte) (d >> 16);
                buffer[3] = (byte) (d >> 8);
                buffer[4] = (byte) (d);

                return 5;
            } else if (d <= UNSIGNED_6BYTES_MAX) {
                type |= 0x50;
                buffer[0] = (byte) type;

                buffer[1] = (byte) (d >> 40);
                buffer[2] = (byte) (d >> 32);
                buffer[3] = (byte) (d >> 24);
                buffer[4] = (byte) (d >> 16);
                buffer[5] = (byte) (d >> 8);
                buffer[6] = (byte) (d);

                return 7;
            } else {
                type |= 0x70;
                buffer[0] = (byte) type;

                buffer[1] = (byte) (d >> 56);
                buffer[2] = (byte) (d >> 48);
                buffer[3] = (byte) (d >> 40);
                buffer[4] = (byte) (d >> 32);
                buffer[5] = (byte) (d >> 24);
                buffer[6] = (byte) (d >> 16);
                buffer[7] = (byte) (d >> 8);
                buffer[8] = (byte) (d);

                return 9;
            }
        }
    }
}