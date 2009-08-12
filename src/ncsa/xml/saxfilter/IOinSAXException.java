package ncsa.xml.saxfilter;

import java.io.IOException;
import org.xml.sax.SAXException;

/**
 * an indication of an IOException during SAX processing.
 *
 * With a {@link SAXFilterContentHandler} in which it is possible to 
 * insert data into the character stream, it is possible to encounter 
 * IOExceptions.  The SAX ContentHandler interface, however, only permits 
 * emitting SAXExceptions.  This class allows the original IOException to 
 * be wrapped in a SAXException for later retrieval.
 */
public class IOinSAXException extends SAXException {

    protected IOException wrapped = null;

    /**
     * wrap an IO exception
     * @param msg     a custom message
     * @param ioex    the original IOException being wrapped
     */
    public IOinSAXException(String msg, IOException ioex) {
        super(msg);
        wrapped = ioex;
    }

    /**
     * wrap an IO exception
     * @param msg     a custom message
     */
    public IOinSAXException(String msg) {
        this(msg, null);
    }

    /**
     * return the wrapped IOException
     */
    public IOException getIOException() { return wrapped; }

}