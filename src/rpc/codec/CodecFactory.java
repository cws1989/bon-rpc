package rpc.codec;

/**
 * @author Chan Wai Shing <cws1989@gmail.com>
 */
public class CodecFactory {

    protected CodecFactory() {
    }

    public static Generator getGenerator() {
        return new DefaultGenerator();
    }

    public static Parser getParser() {
        return new DefaultParser();
    }
}
