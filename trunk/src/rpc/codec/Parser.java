package rpc.codec;

import java.io.IOException;
import java.io.InputStream;
import rpc.codec.exception.InvalidFormatException;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public interface Parser {

    Object parse(byte[] data) throws InvalidFormatException;

    Object read(InputStream inputStream) throws IOException, InvalidFormatException;
}
