package rpc.codec;

import java.io.IOException;
import java.io.OutputStream;
import rpc.codec.exception.UnsupportedDataTypeException;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public interface Generator {

    byte[] generate(Object data) throws UnsupportedDataTypeException;

    void write(OutputStream outputStream, Object data) throws IOException, UnsupportedDataTypeException;
}
