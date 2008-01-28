/*
 * NCSA BIMAgrid
 * Radio Astronomy Imaging Group
 * National Center for Supercomputing Applications
 * University of Illinois at Urbana-Champaign
 * 605 E. Springfield, Champaign IL 61820
 * rai@ncsa.uiuc.edu
 *
 *-------------------------------------------------------------------------
 * History: 
 *  2001/07/26  rlp  initial version
 *  2001/09/24  rlp  added support for setPause().
 *  2007/11/16  rlp  reconstructed from jad-decompiled class file
 */
package ncsa.xml.saxfilter;

import java.io.*;
import java.util.*;
import ncsa.xml.sax.NamespaceMap;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * a reader that filters its content via SAX.   <p>
 *
 * The purpose of this reader is to allow one to open an XML document
 * with an attached XML ContentHandler that can react to the XML data
 * while passing the contents as if from any other Reader.  In particular,
 * the ContentHandler has the ability to filter what data gets passed.
 * For example, it could cause certain nodes to be skipped over or have
 * the contents of another document inserted at an appropriate location.
 * The ability to control the flow requires a special type of 
 * ContentHandler, namely a {@link SAXFilterContentHandler}.
 * <p>
 * Note that the text that is read from this classes Reader interface is 
 * not a re-serialization of the XML data, but the original character
 * stream (apart from the changes made by the content handler), including 
 * the original spacing, namespace prefixes, etc.
 * <p>
 * It is intended that this class will evolve to fully support SAX parsing.
 */
public class SAXFilteredReader extends Reader {
    private ContentHandler chandler = null;
    private PushbackReader src = null;
    private TextBuffer buf = new TextBuffer();
    private OnDemandParserDelegate evts = new OnDemandParserDelegate();
    private CharLocator loc = new CharLocator();
    private boolean strict = false;
    private boolean eof = false;
    private boolean started = false;
    private boolean parseAhead = false;
    private Stack prefixHistory = new Stack();

    // the trail of breadcrumbs indicating where flow should be turned on/off
    private SkipSchedule skip = new SkipSchedule(false);

    // the stack of input sources 
    Stack srcstack = null;

    // the position relative to the start of the document of the beginning
    // of the characters in the TextBuffer
    private long cpos = 0;

    // sent = the number of characters from the beginning of the TextBuffer
    //        that have already been sent.  sent >= 0
    // parsed = the number of characters from the beginning of the TextBuffer
    //        that have been parsed.  parsed >= sent
    private int sent=0, parsed=0;

    // the pending-parsed position.  This set before any call to a 
    // ContentHandler method to the value parsed will immediately after
    // that method returns.  It is the effective parsed position from the
    // perspective of the ContentHandler method.
    private int pending = parsed;

    private ParseRequestMgr prq = null;
    private Stack prefixes = null;
    private NamespaceMap namespaces = new NamespaceMap();

    private PauseMarkers pmarks = null;
    private char pausechar = '\004';
    private char[] cb = new char[1];
    private boolean addpause = false;

    /**
     * create a "closed" reader.  No source reader is set, and so it behaves
     * as if it is closed.  It can be opened by calling the setSource()
     * method.
     */
    public SAXFilteredReader() {
        super();
    }

    /**
     * create a reader that retrieves text from a given reader.
     * @param source   the source Reader
     */
    public SAXFilteredReader(Reader source) {
        this();
        setSource(source);
    }

    /**
     * create a reader that retrieves text from a given reader.
     * @param source   the source Reader
     * @param ch       a content handler for filtering the XML data
     */
    public SAXFilteredReader(Reader source, ContentHandler ch) {
        this(source);
        setContentHandler(ch);
    }

    /**
     * return true if IOExceptions will be thrown when bad XML is 
     * encountered
     */
    public boolean isStrict() { return strict; }

    /**
     * set whether IOExceptions should be thrown when bad XML is 
     * encountered
     * @param val   if true, IOExceptions will be thrown
     */
    public void setStrict(boolean val) { strict = val; }

    /**
     * set the source of the text
     * @param source    the source Reader
     */
    public void setSource(Reader source) {
        src = new PushbackReader(source, 128);
        eof = false;
    }

    /**
     * return the current source of this Reader
     * null is returned if none has been set.
     */
    public Reader getSource() { return src; }

    /**
     * set the content handler that will receive SAX events
     */
    public void setContentHandler(ContentHandler handler) {
        chandler = handler;
        if (chandler != null && (chandler instanceof SAXFilterContentHandler)) {
            SAXFilterContentHandler sfhandler = 
                (SAXFilterContentHandler) handler;
            sfhandler.setParseRequestMgr(getPrm());
            sfhandler.setFlowController(getPrm());
            sfhandler.setNamespaces(namespaces);
        }
    }

    /**
     * set the character that will be used as a pause marker.  This should 
     * be a character that is not normally expected to be encounter in the
     * stream.
     */
    public void setPauseChar(char c) { pausechar = c; }

    /**
     * return the character that will be used as a pause marker.  The default 
     * is \004.  
     */
    public char getPauseChar() { return pausechar; }

    /**
     * schedule the insertion of a marker at the position where we should 
     * pause in the parsing
     */
    public void addPauseMarker(long pos) {
        if (pmarks == null)
            pmarks = new PauseMarkers();
        pmarks.setMark(pos);
    }

    private ParseRequestMgr getPrm() {
        if (prq == null) prq = new ParseRequestMgr();
        return prq;
    }

    /**
     * return the content handler that is set to receive SAX events.
     * null is returned if none has been set.
     */
    public ContentHandler getContentHandler() { return chandler; }

    /**
     * close this stream.  The source stream will in turn be closed and 
     * set to null.  To reopen this stream, pass an open stream to 
     * setSource().
     */
    public void close() throws IOException {
        if (src != null) {
            src.close();
            src = null;
        }
    }

    /**
     * Tell whether this stream is ready to be read without blocking.
     * Note that if no source is currently set, true is returned (but
     * any subsequent read will throw an IOException).
     */
    public boolean ready() throws IOException {
        return (src == null || src.ready());
    }

    /**
     * read a single character
     */
    public int read() throws IOException {
        int n;
        while((n = read(cb, 0, 1)) == 0) { }

        if (n < 0)
            return -1;
        else
            return cb[0];
    }

    /**
     * Read characters into a portion of an array.  If a content handler 
     * is set, the characters will trigger SAX events.
     */
    public synchronized int read(char chars[], int off, int len)
        throws IOException
    {
        if (src == null)
            throw new IOException("read on closed input stream");
//        if (eof) throw new EOFException();

        int sendable;
        int need = len;
        int[] tilleof = new int[1];

        if (addpause) {
            chars[off] = pausechar;
            addpause = false;
            return 0;
        }

        try {
            while (need > 0) {
                sendable = parseBuffer(need);
                if (sendable <= 0) {
                    if (need >= len) return -1;
                    break;
                }

                sendable = Math.min(need, sendable);
                sendable = skipLimit(sendable);

                // check to see if we need to pause
                if (pmarks != null && sendable > 0) {
                    addpause = pmarks.applyPause(sendable, tilleof);
                    if (tilleof[0] < sendable) sendable = tilleof[0];
                }

                if (sendable > 0) {
                    buf.copy(sent, chars, off, sendable);
                    sent += sendable;
                    off += sendable;
                    need -= sendable;
                    skip.popTo(cpos+sent);
                }

                if (addpause && off < chars.length) {
                    chars[off] = pausechar;
                    addpause = false;
                    break;
                }
            }

//            if (need == len) throw new EOFException();
//            else if (need > 0) eof = true;

            if (eof && chandler != null && evts.isEnabled(evts.DOCUMENT)) {
                pending = parsed;
                chandler.endDocument();
            }
        } catch (SAXException ex) {
            throw new IOException("SAX processing error: " + ex.getMessage());
        }

        sendable = len-need;
//         if (sendable == 0) sendable = -1;
        return sendable;
    }

    /**
     * return the number of characters read
     */
    public long readCount() {
        return sent + cpos;
    }

    /**
     * return the position of the next pause marker
     */
    public long nextPauseMarker() {
        return ((pmarks != null) ? pmarks.nextMark() : -1);
    }

    // a buffer for reading characters from the source stream
    private char[] cbuf = new char[128];

    // an iterator into the TextBuffer pointing to the next Substring
    // of interest
    private BufferIterator bi = new BufferIterator();

    // lip = the position of the offset in the current Substring relative 
    //       to the beginning of the TextBuffer.
    // lilen = the length of the current Substring
    private int lip = 0, lilen = 0;

    public static final String COMMENT_START = "<!--";
    public static final String COMMENT_END = "-->";
    public static final String PROC_INSTR_START = "<?";
    public static final String PROC_INSTR_END = "?>";
    public static final String CDATA_START = "<![CDATA[";
    public static final String CDATA_END = "]]>";
    public static final String EMPTYSTR = "";

    private static final String XSI = 
        "http://www.w3.org/2001/XMLSchema-instance";

    /**
     * parse and refresh characters sitting in the buffer until there are 
     * nchars unsent characters available.
     */
    private int parseBuffer(int nchars) throws IOException, SAXException {
        applySkip();
        if (parsed - sent > nchars) return parsed - sent;

        if (!started) {
            pending = parsed;
            if (chandler != null && evts.isEnabled(evts.DOCUMENT)) 
                chandler.startDocument();
            started = true;
        }

        // first shift off the characters no longer needed in the buffer
        trimBuffer();

        String use = null;
        Substring sub = getSubstring(parsed);
        if (sub == null) return parsed - sent;

        // p = a pointer into the current Substring to a character position 
        //     of interest
        int p = sub.off;

        // lp = (mneumonic: local parsed) a pointer indicating the position 
        //      of parsed in the current Substring.  lp <= p.  can be < 0 if
        //      it corresponds to previously handled Substrings.
        int lp = p;

        while (parsed-sent < nchars || parseAhead) {

            // parse and process
            p = sub.str().indexOf('<', lp);
            if (p >= 0) {

                // process any text nodes
                if (p > 0 && evts.isEnabled(evts.CHARACTERS)) {
                    try {
                        handleChars(parsed, p - lp, true);
                    } catch(SAXException ex) {
                        if (strict)
                            throw new IOException("SAX processing error: " + 
                                                  ex.getMessage());
                    }

                    // handler may have changed size of buffer; use global
                    // vars (updated by handler) to reset local markers.
//                  sub = bi.current();
                    lp = parsed - bi.pos();
                    p = lp + loc.getCharLength();
                }
                parsed += p - lp;
                lp = p;

                if (sub.str().startsWith(COMMENT_START, p)) {

                    // found a comment; advance to the end of it
                    p = sub.str().indexOf(COMMENT_END, p);
                    while (p < 0) {
                        lp -= sub.str().length();
                        sub = nextSubstring();
                        if (sub == null) break;
                        p = sub.str().indexOf("-->", sub.off);
                    } 
                    if (p < 0)
                        p = 0;
                    else
                        p += COMMENT_END.length();
//                    if (evts.isEnabled(COMMENTS)) {
//                        try {
//                            handleComment(parsed, p-lp);
//                        } catch (SAXException ex) {
//                          if (strict)
//                            throw new IOException("SAX processing error: " +
//                                                  ex.getMessage());
//                        }
//                    }
                    parsed += p - lp;
                    lp = p;
                    if (sub == null) break;
                } 
                else if (sub.str().startsWith(PROC_INSTR_START, p)) {

                    // found a processing instruction
                    p = sub.str().indexOf(PROC_INSTR_END, p);
                    while (p < 0) {
                        lp -= sub.str().length();
                        sub = nextSubstring();
                        if (sub == null) break;
                        p = sub.str().indexOf(PROC_INSTR_END, sub.off);
                    } 
                    if (p < 0)
                        p = 0;
                    else
                        p += PROC_INSTR_END.length();
                    if (evts.isEnabled(evts.PROC_INSTR)) {
                        try {
                            handleProcInstr(parsed, p - lp);
                        } catch(SAXException ex) {
                          if (strict)
                            throw new IOException("SAX processing error: " + 
                                                  ex.getMessage());
                        }

                        // handler may have changed size of buffer; use global
                        // vars (updated by handler) to reset local markers.
//                      sub = bi.current();
                        lp = parsed - bi.pos();
                        p = lp + loc.getCharLength();
                    }
                    parsed += p - lp;
                    lp = p;
                    if (sub == null) break;
                } 
                else if (sub.str().startsWith(CDATA_START, p)) {

                    // found a CDATA section instruction
                    p = sub.str().indexOf(CDATA_END, p);
                    while (p < 0) {
                        lp -= sub.str().length();
                        sub = nextSubstring();
                        if (sub == null) break;
                        p = sub.str().indexOf(CDATA_END, sub.off);
                    } 
                    if (p < 0) p = 0;
                    if (evts.isEnabled(evts.CHARACTERS)) {
                        try {
                            handleChars(parsed+CDATA_START.length(), 
                                        p-lp, false);
                        } catch(SAXException ex) {
                          if (strict)
                            throw new IOException("SAX processing error: " + 
                                                  ex.getMessage());
                        }

                        // handler may have changed size of buffer; use global
                        // vars (updated by handler) to reset local markers.
//                      sub = bi.current();
                        lp = parsed - bi.pos();
                        p = lp + loc.getCharLength();
                    }
                    p += CDATA_END.length();
                    parsed += p - lp;
                    lp = p;
                    if (sub == null) break;
                } 
                else {

                    // found a tag; advance to the end of it
                    char tagtype = sub.str().charAt(p + 1);
                    p = sub.str().indexOf('>', p);
                    while (p < 0) {
                        lp -= sub.str().length();
                        sub = nextSubstring();
                        if (sub == null) break;
                        p = sub.str().indexOf('>', sub.off);
                    } 
                    if (p < 0)
                        p = 0;
                    else
                        p += 1;
                    if (tagtype != '!' && evts.anyEnabled(evts.ELEMENT|
                                                          evts.ATTRIBUTES|
                                                          evts.NAMESPACES)) 
                    {
                        try {
                            if (tagtype == '/') {
                                handleEndElement(parsed, p - lp);
                            } else {
                                handleStartElement(parsed, p - lp);
                            }
                        } catch(SAXException ex) {
                          if (strict)
                            throw new IOException("SAX processing error: " + 
                                                  ex.getMessage());
                        }

                        // handler may have changed size of buffer; use global
                        // vars (updated by handler) to reset local markers.
//                      sub = bi.current();
                        lp = parsed - bi.pos();
                        p = lp + loc.getCharLength();
                    }
                    parsed += p - lp;
                    lp = p;
                    if (sub == null) break;
                }
            } 
            else {
                if (evts.isEnabled(evts.CHARACTERS) && 
                    lp < sub.str().length())
                {
                    try {
                        handleChars(parsed, sub.str().length() - lp, true);
                    } catch(SAXException ex) {
                        if (strict)
                            throw new IOException("SAX processing error: " + 
                                                  ex.getMessage());
                    }

                    // handler may have changed size of buffer; use global
                    // vars (updated by handler) to reset local markers.
//                  sub = bi.current();
                    lp = parsed - bi.pos();
                    p = lp + loc.getCharLength();
                }
                p = sub.str().length();
                parsed += p - lp;
                lp = p;
            }

            applySkip();

            if ((parsed-sent < nchars || parseAhead) &&
                (sub == null || p >= sub.str().length() || p < 0))
            {
                lp -= sub.str().length();
                sub = nextSubstring();
                if (sub == null) break;
                p = sub.off;
            } 
        }

        return parsed - sent;
    }

    private int applySkip() {
        int skipped = 0;
        if (skip.skipping()) {
            long swtch = skip.nextSwitch();
            skipped = (int) Math.min(parsed, swtch-cpos);
            sent = skipped;
            skip.popTo(cpos+skipped);
        }
        return skipped;
    }

    private int skipLimit(int need) {
        if (need <= 0) return 0;
        need -= applySkip();
        if (need <= 0) return 0;

        long sendable = skip.nextSwitch()-cpos-sent;
        if (sendable < 0) {
            System.err.println("Programmer warning: skip schedule out of sync");
            return need;
        }
        if (! skip.skipping && sendable < need) {
            // Note that if 0 < sendable < need, sendable will fit into an int
            need = (int)sendable;
        }

        return need;
    }

    /**
     * shift off the characters no longer needed in the buffer
     */
    private void trimBuffer() {
        if (sent > 0) {
            buf.popChars(sent);
            cpos += sent;
            parsed -= sent;
            sent = 0;
        }
    }

    /**
     * return the Substring containing the character at a given position,
     * resetting the iteration handled by nextSubstring().
     */
    private Substring getSubstring(int p) throws IOException {
        TextBuffer.Iter li = null;

        if (p < buf.size() || fillBuffer(p + 1) > p)
            li = buf.getSubstring(p);

//         if (li == null) {
//             System.err.println("position " + p + " out of range");
//      }

        bi.resetTo(li, p);
        return bi.current();
    }

    /**
     * get the next Substring
     */
    private Substring nextSubstring() throws IOException {
        Substring out = null;

        if (bi.hasNext()) {
            out = (Substring)bi.next();
        } 
        else {
            out = bi.current();
            if (out != null) {
                int upto = bi.pos()+out.str().length()+1;
                int got = fillBuffer(upto);
                TextBuffer.Iter li = buf.iterAtLast();
                ((Substring) li.previous()).off = 1;
                bi.resetTo(li, upto);
                out = (got >= upto) ? (Substring) bi.current() : null;
            }
        }
        if (out != null) out.off = 0;

        return out;
    }

    /**
     * fill the buffer until contains at least p characters
     * @return int   the number of characters in the buffer as a result of the
     *               call to this method.
     */
    private synchronized int fillBuffer(int p) throws IOException {
        int n, nl;
        StringBuffer sb = new StringBuffer(Math.max(cbuf.length, 
                                                    p-buf.size()));
        while (p > buf.size()) {

            // add text to the buffer.  Each piece of new text should be 
            // terminated with a newline character to ensure easy parsing.
            nl = -1;
            n = cbuf.length;
            while (nl < 0) {
                n = src.read(cbuf, 0, cbuf.length);
                if (n < 0 && srcstack != null) {
                    while (n < 0 && srcstack.size() > 0) {
                        src.close();
                        src = (PushbackReader) srcstack.pop();
                        n = src.read(cbuf, 0, cbuf.length);
                    }
                }
                if (n < 0) break;

                // find the last newline 
                nl = n-1;
                while (nl >= 0 && cbuf[nl] != '\n') nl--;
                
                if (nl < 0) 
                    sb.append(cbuf, 0, n);     // no newline found; add it all
                else 
                    sb.append(cbuf, 0, nl+1);  // append up to end of line
            }

            buf.append(sb.toString());
            if (nl >= 0) {
                src.unread(cbuf, nl+1, n-nl-1);
            }
            else if (n < cbuf.length) { 
                eof = true;
                break;
//                return false;
            }
        }

        return buf.size();
    }

    /**
     * handle a text node.  This method updates the locator.
     * @param start   the starting position of the data relative to the 
     *                   start of the text buffer
     * @param len     the number characters making up the data
     * @return boolean   true if the buffer contents were changed enough to 
     *                     to require character pointers to be updated
     */
    void handleChars(int start, int len, boolean whiteIgnorable)
        throws SAXException, IOException
    {
        if (chandler == null) return;
        int l, m;

        // update the locator
        pending = parsed + len;
        loc.setChars(cpos + (long)start, len);

        // copy characters into an array
        char[] sb = new char[len];
        ListIterator li = buf.getSubstring(start);
        Substring sub = (Substring)li.next();
        start = 0;
        l = Math.min(len, sub.str().length() - sub.off);
        while (len > 0) {
            sub.str().getChars(sub.off, sub.off + l, sb, start);
            len -= l;
            start += l;
            if (len > 0) sub = (Substring) li.next();
            l = Math.min(sub.off+len, sub.str().length());
        }

        l = 0;
        m = sb.length;
        if (whiteIgnorable) {

            // look for leading white space
            for(l = 0; l < sb.length && Character.isWhitespace(sb[l]); l++);
            if (l > 0 && evts.isEnabled(evts.IGNORE_WHITE_SPACE)) 
                chandler.ignorableWhitespace(sb, 0, l);

            // look for trailing white space
            for(m = sb.length; m > 0 && Character.isWhitespace(sb[m-1]); m--);
        }

        if (l < m) {
            chandler.characters(sb, l, m - l);
            if (m < sb.length && evts.isEnabled(evts.IGNORE_WHITE_SPACE)) 
                chandler.ignorableWhitespace(sb, m, sb.length - m);
        }
    }

    void handleProcInstr(int start, int len) throws SAXException, IOException {
        if (chandler == null) return;
        int l, m;

        // update the locator
        pending = parsed + len;
        loc.setChars(cpos+start, len);

        ListIterator li = buf.getSubstring(start);
        Substring sub = (Substring)li.next();
        m = sub.off+PROC_INSTR_START.length();
        for(l=m+1; 
            l < sub.str().length() && 
                ! Character.isWhitespace(sub.str().charAt(l)); 
            l++);
        String target = sub.str().substring(m, l);
        len -= l-sub.off;

        StringBuffer sb = new StringBuffer();
        while (len > 0)  {
            m = Math.min(sub.str().length(), l+len);
            len -= m - l;
            if (len <= 0) m -= PROC_INSTR_END.length();
            sb.append(sub.str().substring(l, m));
        }

        chandler.processingInstruction(target, sb.toString());
    }

    void handleStartElement(int start, int len)
        throws SAXException, IOException
    {
        if (chandler == null) return;

        // update the locator
        pending = parsed + len;
        loc.setChars(cpos+start, len);

        boolean empty = false;

        // load element tag contents
        ListIterator li = buf.getSubstring(start);
        Substring sub = (Substring) li.next();
        StringBuffer sb = new StringBuffer();
        int use = 0, suboff = sub.off+1;
        len--;
        while (len > 0) {
            use = Math.min(len, sub.str().length() - suboff);
            len -= use;
            if (len <= 0) {
                if (sub.str().charAt((suboff + use) - 2) == '/') {
                    empty = true;
                    use--;
                }
                use--;
            }
            sb.append(sub.str().substring(suboff, suboff + use));
            if (len > 0) sub = (Substring)li.next();
            suboff = 0;
        }
        StringTokenizer st = new StringTokenizer(sb.toString(), 
                                                 " \t\n\r\f", true);

        // get tag name
        if (!st.hasMoreTokens()) {
            if (strict)
                throw new SAXException("Element Tag without a name: " + 
                                       sb.toString());
            return;
        }
        String elname = st.nextToken();
        String qelname = EMPTYSTR;
        String prefix = EMPTYSTR;
        String namesp = EMPTYSTR;
        if (st.hasMoreTokens())
            st.nextToken();
        if (evts.isEnabled(evts.NAMESPACES)) {
            qelname = elname;
            int p;
            if ((p = elname.indexOf(':')) >= 0) {
                prefix = qelname.substring(0, p);
                elname = qelname.substring(p + 1);
            }
        }

        // load attributes as desired
        AttributesImpl tmplist = new AttributesImpl();
        AttributesImpl attrlist = tmplist;
        StringBuffer aval = null;
        HashSet prefixes = null;
        if (evts.anyEnabled(evts.ATTRIBUTES|evts.NAMESPACES)) {
            String attr, qname, nsname, pname, lname;
            int p, q;
            prefixes = new HashSet();
            prefixHistory.push(prefixes);

            while (st.hasMoreTokens()) {
                attr = st.nextToken();

                // parse attribute name and value
                p = attr.indexOf('=');
                if (p < 0 || p > attr.length()-3) {
                    if (strict)
                        throw new SAXException("bad attribute syntax: " + attr);
                    continue;
                } 
                lname = attr.substring(0, p);
                qname = EMPTYSTR;
                nsname = EMPTYSTR;
                pname = EMPTYSTR;

                if (attr.charAt(++p) != '"' && attr.charAt(p) != '\'') {
                  if (strict)
                    throw new SAXException("bad attribute syntax: no quotes: "
                                           + attr);
                  p--;
                }
                aval = new StringBuffer(attr.substring(p + 1));
                if (attr.charAt(p) != '=') {
                    // we're being strict, so find the ending quote that 
                    // matches the quote character at position p
                    q = p + 1;
                    String tmp = attr;
                    while(st.hasMoreTokens() && 
                          (q = tmp.indexOf(attr.charAt(p), q)) < 0)
                    {
                        tmp = st.nextToken();
                        aval.append(tmp);
                        q = 0;
                    }

                    attr = tmp;
                }
                if (st.hasMoreTokens()) st.nextToken();

                // lop off possible trailing '>' and '/'
                if (aval.charAt(aval.length() - 1) == '>') {
                    aval.deleteCharAt(aval.length() - 1);
                    if (aval.charAt(aval.length() - 1) == '/')
                            aval.deleteCharAt(aval.length() - 1);
                }

                // lop off the trailing quote
                if (aval.charAt(aval.length() - 1) == '"' || 
                    aval.charAt(aval.length() - 1) == '\'')
                  aval.deleteCharAt(aval.length() - 1);

                String value = aval.toString();    // the parsed attribute val

                if (evts.isEnabled(evts.NAMESPACES)) {
                    // check to see if attribute has a namespace prefix
                    qname = lname;
                    if ((q = qname.indexOf(':')) >= 0) {
                        lname = qname.substring(q + 1);
                        pname = qname.substring(0, q);

                        if (evts.isEnabled(evts.PREFIX_MAPPING)) {
                            if (pname.equals("xmlns")) {
                                // register a namespace definition
                                prefixes.add(lname);
                                namespaces.startPrefixMapping(lname, value);
                                chandler.startPrefixMapping(lname, value);
                            } 
                            else if (namespaces.getURI(pname) != null) {
                                nsname = namespaces.getURI(pname);
                            }
                        }
                    }
                    else if (evts.isEnabled(evts.PREFIX_MAPPING) && 
                             qname.equals("xmlns"))
                    {
                        nsname = value;
                        prefixes.add(EMPTYSTR);
                        namespaces.setDefaultNS(nsname);
                        chandler.startPrefixMapping(EMPTYSTR, value);
                    }
                    if (XSI.equals(nsname) && lname.equals("schemaLocation"))
                        namespaces.addLocations(value);
                }
                tmplist.addAttribute(nsname, lname, qname, "CDATA", value);
            } 

            if (evts.isEnabled(evts.PREFIX_MAPPING)) {
                if (prefix.length() > 0) {
                    namesp = namespaces.getURI(prefix);
                    if (namesp == null) namesp = EMPTYSTR;
                } 
                else {
                    namesp = namespaces.getDefaultNS();
                }
            }

            if (evts.isEnabled(evts.ATTRIBUTES)) {
                Set wantatts = null;
                if (namesp.length() > 0)
                    wantatts = evts.attributesFor(namesp + elname);
                if (wantatts == null && qelname.length() > 0)
                    wantatts = evts.attributesFor(qelname);
                if (wantatts == null)
                    wantatts = evts.attributesFor(elname);
                if (wantatts == null)
                    wantatts = evts.attributesFor(EMPTYSTR);

                if (wantatts != null) {
                    attrlist = new AttributesImpl();
                    for(int i = 0; i < tmplist.getLength(); i++) {
                        if (wantatts.contains(tmplist.getQName(i)))
                            attrlist.addAttribute(tmplist.getURI(i), 
                                                  tmplist.getLocalName(i), 
                                                  tmplist.getQName(i), 
                                                  tmplist.getType(i), 
                                                  tmplist.getValue(i));
                    }
                }
            } 
            else {
                attrlist = new AttributesImpl();
            }
        }

        namespaces.startElement();
        chandler.startElement(namesp, elname, qelname, attrlist);
        if (empty) {
            namespaces.endElement();
            chandler.endElement(namesp, elname, qelname);

            if (evts.anyEnabled(evts.PREFIX_MAPPING|evts.NAMESPACES) && 
                prefixes != null)
            {
                Iterator it = null;
                for(it = prefixes.iterator(); 
                    it.hasNext(); 
                    namespaces.endPrefixMapping((String) it.next()));

                if (evts.anyEnabled(evts.PREFIX_MAPPING)) {
                    for(it = prefixes.iterator(); 
                        it.hasNext(); 
                        chandler.endPrefixMapping((String)it.next()));
                }
            }
        }
    }

    void handleEndElement(int start, int len)
        throws SAXException, IOException
    {
        if (chandler == null) return;
        int q, p;
        String prefix, elname, qelname, namesp;

        // update the locator
        pending = parsed+len;
        loc.setChars(cpos + (long)start, len);

        ListIterator li = buf.getSubstring(start);
        Substring sub = (Substring)li.next();

        int suboff = sub.off + 2;
        len -= 3;
        if (suboff+len > sub.str().length())
            len = sub.str().length()-suboff;
        elname = sub.str().substring(suboff, suboff + len);
        qelname = elname;
        prefix = EMPTYSTR;
        namesp = EMPTYSTR;

        HashSet prefixes = (HashSet) prefixHistory.pop();
        if ((p = qelname.indexOf(':')) >= 0) {
            prefix = qelname.substring(0, p);
            elname = qelname.substring(p + 1);
        }

        namespaces.endElement();
        chandler.endElement(EMPTYSTR, elname, qelname);

        // update the scope of namespace prefixes as necessary
        if (prefixes != null && 
            evts.anyEnabled(evts.NAMESPACES|evts.PREFIX_MAPPING)) 
        {
            Iterator i = null;
            for(i = prefixes.iterator(); 
                i.hasNext(); 
                namespaces.endPrefixMapping((String)i.next()));

            if (evts.isEnabled(evts.PREFIX_MAPPING)) {
                for(i = prefixes.iterator(); 
                    i.hasNext(); 
                    chandler.endPrefixMapping((String)i.next()));
            }
        }
    }

    class PauseMarkers {
        TreeSet marks;

        public PauseMarkers() {
            marks = new TreeSet();
        }

        public PauseMarkers(long mark) {
            marks = new TreeSet();
            setMark(mark);
        }

        public void setMark(long pos) {
            marks.add(new Long(pos));
        }

        public long nextMark() {
            if (marks.size() > 0) return -1;
                
            Long next = null;
            for(Iterator i = marks.iterator(); i.hasNext();) {
                next = (Long)i.next();
                if (next.longValue() - cpos - ((long) sent) >= 0)
                    return next.longValue();
            }

            return -1;
        }

        public boolean applyPause(int want, int send[]) {
            Long next = null;
            long diff = 0;

            send[0] = want;
            while(marks.size() > 0)  {
                next = (Long) marks.first();
                diff = next.longValue() - cpos - (long)sent;
                if (diff > (long) want) return false;
                    
                marks.remove(next);
                if (diff >= 0L) {
                    send[0] = (int) diff;
                    return true;
                }
            }

            return false;
        }
    }
    
    /**
     * an interface for controlling this Reader.  An instance of this 
     * class is usually passed to compatable ContentHandlers allowing 
     * them to control the flow of characters from this Reader.
     */
    public class ParseRequestMgr implements OnDemandParser, 
        SAXFilterFlowControl
    {
        /**
         * create the controller attached the parent Reader
         */
        public ParseRequestMgr() { }

        /**
         * stop sending characters from the source stream starting at the 
         * given character position.  
         * @param pos   the zero-based character index relative to the start 
         *                  of the file.
         */
        public void skipFrom(long pos) {
            skip.skipFrom(pos);
        }

        /**
         * pause the sending of characters so that more input data can be 
         * parsed until further notice.  This does not cause any data to be 
         * skipped; however, by parsing ahead one can issue a skip request 
         * starting at a previously parsed position and guarantee that it is 
         * honored.
         */
        public void setPause(boolean pause) {
            parseAhead = pause;
        }

        /**
         * resume sending characters from the source stream starting at the 
         * given character position.  
         * @param pos   the zero-based character index relative to the start 
         *                  of the file.
         */
        public void resumeFrom(long pos) {
            skip.skipTo(pos);
        }

        /**
         * insert a string at the the given position in the stream.
         * @param chars  the character data to insert into the reader's stream.
         * @param pos    the zero-based character index relative to the start
         *                   of the file to insert the string at.  
         * @throws IllegalStateException  if the indicated position has already
         *            been received by the reader.  (That is, you can't go too 
         *            far back in time).
         */
        public void insert(String chars, long pos) throws IOException {

            // can't insert before characters already sent out to Reader
            if (pos < cpos + (long)sent)
                throw new IllegalStateException("Too late for insert");

            int bpos = (int)(pos - cpos);  // position within buffer
            if (bpos == buf.size()) {
                buf.append(chars);
            } 
            else if (bpos > buf.size()) {
                // FIXME: allow this in the future
                throw new IllegalStateException("Too soon for insert");
            }
            else {
                buf.insert(chars, bpos);
            }

            // adjust our markers.  If bpos (the insert position relative 
            // to the the start of the buffer) is at the parsed position or 
            // before, we will interpret the new text as already parsed.
            if (bpos <= parsed) parsed += chars.length();
            if (bpos < bi.pos()) bi.pos += chars.length();
            if (pos <= loc.getCharNumber()) {
                loc.setChars(loc.getCharNumber()+chars.length(), 
                             loc.getCharLength());
            }
            else if (pos < loc.getCharNumber()+loc.getCharLength()) {
                loc.setChars(loc.getCharNumber(), 
                             loc.getCharLength()+chars.length());
            }

            // update the skip schedule
            skip.insert(pos, chars.length());
        }

        /**
         * substitute in a string for a range of characters in the stream.  
         * This method will adjust the parser's markers and buffer iterator
         * accordingly. 
         * @param chars  the character data to substitute into the reader's 
         *                   stream
         * @param pos    the zero-based character index relative to the start
         *                   of the file to begin the substitution at. 
         * @param len    the number of characters to pull out of the stream 
         *                   beginning at the pos position.
         */
        public void substitute(String chars, long pos, int len)
            throws IOException
        {
            // can't insert before characters already sent out to Reader
            if (pos < cpos+sent)
                throw new IllegalStateException("Too late for substitution");

            int bpos = (int) (pos - cpos);  // position within buffer
            if (bpos == buf.size()) {
                buf.append(chars);
            } 
            else if ((bpos + len) - 1 > buf.size()) {
                // FIXME: allow this in the future
                throw new IllegalStateException("Too soon for substitution");
            }
            else {
                buf.substitute(chars, bpos, len);

                // adjust the parsed marker position
                if (bpos < parsed) {
                    if (bpos + len <= parsed) {
                        // the new text falls before the parsed marker: just
                        // push marker up the appropriate amount
                        parsed += chars.length()-len;
                    }
                    else {
                        // the new text strattles the parsed marker;
                        // move marker to end of new text.  
                        // (Probabably a dangerous request.)
                        parsed = bpos + len;
                    }
                }

                if (bpos+len <= bi.pos())
                    bi.pos += chars.length() - len;
                else if (bpos+len > bi.pos && bpos+chars.length() <= bi.pos)
                    bi.pos = bpos + chars.length();

                // update the locator position
                if (pos + (long)len <= loc.getCharNumber()) {
                    // substitution range is prior to locator start position:
                    // just push locator range up.
                    loc.setChars(loc.getCharNumber()+chars.length()-len, 
                                 loc.getCharLength());
                } 
                else if (pos+len < loc.getCharNumber() + loc.getCharLength()) {
                    // substitution range is prior to locator end position:
                    // extend range of locator by the amount of text added.
                    loc.setChars(loc.getCharNumber(), 
                                 (int) loc.getCharLength()+chars.length()-len);
                }
                else if (pos < loc.getCharNumber()+loc.getCharLength() && 
                         pos+len >= loc.getCharNumber()+loc.getCharLength() && 
                         pos+chars.length() < 
                              loc.getCharNumber()+loc.getCharLength())
                {
                    // range of characters to pull out strattles locator end 
                    // position, but new text falls within the locator range:
                    // pull the end of the locator range to the end of the 
                    // substituted text.  (probably a dangerous situation)
                    loc.setChars(loc.getCharNumber(), 
                                 (int)(pos+chars.length()-loc.getCharNumber()));
                }

                // update the skip schedule
                skip.insert(pos, chars.length() - len);

                // update the buffer iterator
//              getSubstring((int) (loc.getCharNumber() + loc.getCharLength() +
//                                  1 - cpos));
            }
        }

        /**
         * push a new input stream onto the stack of input streams.  This is 
         * used for inserting one document into another.  If the given position
         * is passed, the source will be closed and ignored.  
         */
        public void pushSource(Reader source)  {
            if (srcstack == null) srcstack = new Stack();

            // push our current source onto the source stack
            srcstack.push(src);

            // if our buffer has unparsed characters, push them onto the stack
            // in the form of StringReaders (wrapped in PushbackReaders).
            int pending = ((int) (loc.getCharNumber()-cpos)) + 
                                                           loc.getCharLength();
            if (buf.size() > pending) {
                ListIterator li = buf.getSubstring(pending);
                Substring psub = (Substring)li.next();
                String head = psub.str().substring(0, psub.off);
                String tail = psub.str().substring(psub.off);

                if (tail.length() > 0)
                    srcstack.push(
                        new PushbackReader(new StringReader(tail), 128));

                li = buf.iterAtLast();
                Substring sub = null;
                while (li.hasPrevious()) {
                    sub = (Substring) li.previous();
                    if (sub == psub) break;
                    li.remove();

                    srcstack.push(
                        new PushbackReader(new StringReader(sub.str()), 128));
                } 

                if (head.length() > 0) {
                    if (sub == psub)
                        buf.replace(sub, head);
                    else
                        buf.append(head);
                }
            }

            // make the new source the current source
            src = new PushbackReader(source, 128);
        }

        /**
         * schedule the insertion of a marker at the position where we should 
         * pause in the parsing
         */
        public void addPauseMarker(long pos) {
            SAXFilteredReader.this.addPauseMarker(pos);
        }

        /**
         * set the character that will be used as a pause marker
         */
        public char setPauseChar(char c) {
            char out = SAXFilteredReader.this.getPauseChar();
            SAXFilteredReader.this.setPauseChar(c);
            return out;
        }
    
        /**
         * return the character that will be used as a pause marker
         */
        public char getPauseChar() {
            return SAXFilteredReader.this.getPauseChar();
        }

        /**
         * enable the given OR-ed collection of events
         * @param events   the set of event codes defined by this interface,
         *                 OR-ed together
         */
        public void enableEvents(int events) {
            OnDemandParserDelegate _tmp = evts;
            if ((events & 0x100) > 0)
            {
                OnDemandParserDelegate _tmp1 = evts;
                events |= 0x80;
            }
            evts.enableEvents(events);
        }

        /**
         * disable the given OR-ed collection of events
         * @param events   the set of event codes defined by this interface,
         *                 OR-ed together
         */
        public void disableEvents(int events) {
            evts.disableEvents(events);
        }

        /**
         * start or stop sending character events to the ContentHandler
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         */
        public void enableCharacters(boolean yes) {
            evts.enableCharacters(yes);
        }

        /**
         * start or stop sending start/end-document events to the 
         * ContentHandler
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         */
        public void enableDocument(boolean yes) {
            evts.enableDocument(yes);
        }

        /**
         * start or stop namespace parsing.  When processing is disabled, all
         * element and attribute names are passed as local names which may or 
         * may not contain a namespace qualifier, and the qualified name will 
         * be an empty string.
         * @param yes   if true, namespace parsing will be enabled; otherwise 
         *                 it will be disabled.
         */
        public void enableNamespaces(boolean yes) {
            evts.enableNamespaces(yes);
        }

        /**
         * start or stop sending start/end-Prefix events to the ContentHandler
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         */
        public void enablePrefix(boolean yes) {
            evts.enablePrefix(yes);
            if (yes) enableNamespaces(yes);
        }

        /**
         * start or stop sending start/end-Element events to the ContentHandler
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         */
        public void enableElement(boolean yes) {
            evts.enableElement(yes);
        }

        /**
         * start or stop sending processing instruction events to the 
         * ContentHandler
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         */
        public void enableProcInstr(boolean yes) {
            evts.enableProcInstr(yes);
        }

        /**
         * start or stop sending skipped entity events to the ContentHandler
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         */
        public void enableSkippedEntity(boolean yes) {
            evts.enableSkippedEntity(yes);
        }

        /**
         * start or stop loading Attributes into element events.  This only has
         * an effect when element events are enabled.
         * @param yes   if true, this event will be enabled; otherwise it will 
         *                 be disabled.
         * @see OnDemandParser#loadAttributes(Set)
         */
        public void enableAttributes(boolean yes) {
            evts.enableAttributes(yes);
        }

        /**
         * when sending an event for a given element, pass only selected 
         * attributes.  By default, all attributes will be loaded and passed
         * to the ContentHandler during element events (assuming attributes
         * have be enabled).  The parser should compare the given 
         * <code>elname</code> first with the qualified name and then with the 
         * local name.  If <code>elname</code> is null or empty, the attribute 
         * selection applies to all, otherwise-unspecified elements.
         * @param elName  the qualified or local name for the element.  If 
         *                  null or empty, the attribute selection applies to 
         *                  all, otherwise-unspecified elements.
         * @param attrs   the attributes given as a Set of Strings.  A null 
         *                  value or empty set resets the selection such that 
         *                  all attributes will be loaded.
         */
        public void loadAttributes(String elname, Set attnames) {
            evts.loadAttributes(elname, attnames);
        }

        /**
         * resume loading all attributes during element events.  This discards
         * all previous attribute selections made with loadAttributes().  
         */
        public void loadAllAttributes() {
            evts.loadAllAttributes();
        }

        /**
         * return true if given events are enabled
         * @param events   the OR-ed set of events to check
         */
        public boolean isEnabled(int events) {
            return evts.isEnabled(events);
        }

        /**
         * return true if any of the given events are enabled
         * @param events   the OR-ed set of events to check
         */
        public boolean anyEnabled(int events) {
            return evts.anyEnabled(events);
        }

        /**
         * return a reference to a CharContentLocator for obtaining character
         * positions of content events.  The same locator should be usable
         * throughout the parsing of a document; thus, this method need only 
         * be called once.  
         */
        public CharContentLocator getCharLocator() {
            return loc;
        }
    }

    class CharLocator implements CharContentLocator {
        long start = 0;
        int length = 0;

        public final long getCharNumber() { return start; }
        public final int getCharLength() { return length; }

        public final void setChars(long begin, int len) {
            start = begin;
            length = len;
        }

        public String getContent() {
            if (start < cpos)
                throw new IllegalStateException("Content missing from memory");

            StringBuffer sb = new StringBuffer(length);
            ListIterator li = buf.getSubstring((int)(start - cpos));
            int need = length; 
            while (li.hasNext() && need > 0) {
                Substring sub = (Substring) li.next();
                sub.len = Math.min(sub.str().length() - sub.off, need);
                sb.append(sub.toString());
                need -= sub.len;
            }

            return sb.toString();
        }
    }

    class BufferIterator implements ListIterator {
        private TextBuffer.Iter li = null;
        private Substring cur = null;
        int pos = 0;

        public BufferIterator() { }

        public BufferIterator(TextBuffer.Iter tbiter, int requestedPosition) {
            resetTo(tbiter, requestedPosition);
        }

        public void resetTo(TextBuffer.Iter tbiter, int requestedPosition) {
            li = tbiter;
            if (li != null && li.hasNext()) {
                cur = (Substring) li.next();
                pos = requestedPosition - cur.off;
            } 
            else {
                cur = null;
                pos = buf.size();
            }
        }

        /**
         * return the object returned by the last call to next() or previous()
         */
        public Substring current() { return cur; }

        void unsupp(String op) {
            throw new UnsupportedOperationException(op);
        }

        /**
         * return the index of the first character in the current substring
         * relative to the start of the document.
         */
        public int pos() { return pos; }

        public final void add(Object o) { unsupp("add"); }
        public final void set(Object o) { unsupp("set"); }
        public final void remove() { unsupp("remove"); }
        public final boolean hasNext() { return li.hasNext(); }
        public final boolean hasPrevious() { return li.hasPrevious(); }
        public final int nextIndex() { return li.nextIndex(); }
        public final int previousIndex() { return li.previousIndex(); }

        public final Object next() {
            Substring prev = cur;
            try {
                if (li.hasNext()) {
                    cur = (Substring)li.next();
                } 
                else {
                    int upto = pos + prev.string.length();
                    if (fillBuffer(upto) < upto)
                        cur = null;
                    else
                        cur = (Substring)li.next();
                }
                pos += prev.string.length();
            }
            catch(ConcurrentModificationException ex)
            {
                li = buf.getSubstring(pos + prev.string.length());
                if (li == null) {
                    li = buf.iterAtLast();
                    cur = null;
                    pos = buf.size();
                } 
                else {
                    pos += prev.string.length();
                }
            }
            catch(IOException ex) {
                throw new IOExcep(ex);
            }

            return cur;
        }

        public final Object previous() {
            try {
                if (li.hasPrevious()) {
                    cur = (Substring)li.previous();
                    pos -= cur.str().length();
                } 
                else {
                    li = buf.getSubstring(0);
                    cur = null;
                    pos = 0;
                }
            }
            catch (ConcurrentModificationException ex) {
                li = buf.getSubstring(pos);
                return previous();
            }

            return cur;
        }

        public class IOExcep extends RuntimeException {
            IOException wrapped = null;
            public IOExcep(java.io.IOException ex) {
                super(ex.getMessage());
                wrapped = ex;
            }
            public IOException getException() { return wrapped; }
        }
    }
} 
