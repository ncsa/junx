package ncsa.xml.sax;

import java.util.Properties;
import java.util.Enumeration;
import java.util.Stack;
import java.util.StringTokenizer;

/**
 * keep track of the evolving set of namespaces in use while traversing an
 * XML hierarchy.  This class provides an interface for updating the 
 * mappings while traversing or parsing an XML document (e.g. in the context
 * of a SAX parser).  
 */
public class NamespaceMap implements Namespaces, Cloneable {

    final int READY = 0;
    final int ADDING = 1;
    final int REMOVING = 2;
    private Stack history = new Stack();
    private Snapshot cur = new Snapshot();
    private int state = 0;
    private int validityDepth = 0;
    private static int anoncounter = 0;

    /**
     * create an empty NamespaceMap
     */
    public NamespaceMap() { }

    /**
     * register a prefix mapping that is now in scope with the current location
     * in the XML document being traversed.
     */
    public synchronized void startPrefixMapping(String prefix, String uri) {
        if(state == 2) doneRemoving();
        if(state == 0) setAddingState();
        cur.addMapping(prefix, uri);
    }

    void setAddingState() {
        cur.validityDepth = validityDepth;
        history.push(cur);
        cur = new Snapshot(cur);
        validityDepth = 0;
        state = 1;
    }

    void doneRemoving() {
        if(validityDepth <= 0 && history.size() > 0)
        {
            cur = (Snapshot)history.pop();
            validityDepth = cur.validityDepth;
        }
        state = 0;
    }

    /**
     * unregister a prefix mapping that is now going out of scope.  This 
     * may be called if the position in the XML document moves upward.  
     */
    public synchronized void endPrefixMapping(String prefix) {
        cur.removeMapping(prefix);
        state = 2;
    }

    /**
     * register the start of an Element.  This is called to help this object
     * keep track of where it is in the hierarchy and determine which prefixes
     * are in scope.
     */
    public synchronized void startElement() {
        if(state == 1)
            state = 0;
        else
        if(state == 2 || validityDepth <= 0)
            doneRemoving();
        validityDepth++;
    }

    /**
     * register the close of an Element.  This is called to help this object
     * keep track of where it is in the hierarchy and determine which prefixes
     * are in scope.
     */
    public void endElement() {
        validityDepth--;
    }

    /**
     * create, register, and return a prefix to associate with a given 
     * namespace.
     */
    public String createMapping(String uri) {
        String prefix = "ns" + ++anoncounter;
        startPrefixMapping(prefix, uri);
        return prefix;
    }

    public String getURI(String prefix)
    {
        return cur.pre2ns.getProperty(prefix);
    }

    /**
     * return the preferred prefix for a given namespace.  
     */
    public synchronized String getPrefix(String uri) {
        String prefix = cur.ns2pre.getProperty(uri);
        if(prefix == null || !uri.equals(cur.pre2ns.getProperty(prefix)))
            return null;
        else
            return prefix;
    }

    /**
     * return an enumeration of the currently defined prefixes
     */
    public Enumeration prefixes() {
        return cur.pre2ns.propertyNames();
    }

    /**
     * return an enumeration of the currently defined namespaces
     */
    public Enumeration uris() {
        return cur.ns2pre.propertyNames();
    }

    /**
     * return the current default namespace
     */
    public String getDefaultNS() {
        return getURI("");
    }

    /**
     * return the current default namespace
     */
    public synchronized void setDefaultNS(String uri) {
        if(uri != null)
            startPrefixMapping("", uri);
    }

    /**
     * associated a physical location for the schema identified by the given
     * namespace.
     * @param ns   the namespace having a location
     * @param loc  the location URL
     */
    public void addLocation(String ns, String loc) {
        if(state == 0)
            setAddingState();
        cur.addLocation(ns, loc);
    }

    /**
     * register a set of namespace-location pairs.  The format of the 
     * input string is the same as an xsi:schemaLocation attribute 
     */
    public void addLocations(String schemaLocations) {
        StringTokenizer st = new StringTokenizer(schemaLocations);
        String ns = null;
        String loc = null;
        do
        {
            if(!st.hasMoreTokens())
                break;
            ns = st.nextToken();
            if(!st.hasMoreTokens())
                break;
            loc = st.nextToken();
            addLocation(ns, loc);
        } while(true);
    }

    /**
     * return the location for a given namespace or null if it is not known
     */
    public String getLocation(String uri) {
        return cur.ns2loc.getProperty(uri);
    }

    /**
     * return an enumeration of the namespaces for which a location is known
     */
    public Enumeration locatedNamespaces() {
        return cur.ns2loc.propertyNames();
    }

    /**
     * create a copy of the an ancestor's Namespaces object.  This is 
     * equivalent to ancestor().
     */
    public NamespaceMap cloneAncestor(int steps)
    {
        NamespaceMap out = (NamespaceMap)clone();
        for(; steps > 0; steps--)
        {
            out.validityDepth--;
            if(out.validityDepth <= 0 && out.history.size() > 0)
            {
                out.cur = (Snapshot)out.history.pop();
                out.validityDepth = out.cur.validityDepth;
            }
        }

        return out;
    }

    /**
     * create an instance of a Namespaces object that is applicable to an
     * ancestor XML node.  
     * @param steps   the number of steps upward in the hierarchy to the 
     *                   desired ancestor element.  
     */
    public Namespaces ancestor(int steps) {
        return cloneAncestor(steps);
    }

    /**
     * deeply copy this object
     */
    public Object clone()  {
        NamespaceMap out = (NamespaceMap)clone();
        out.history = new Stack();
        for(Enumeration e = history.elements(); e.hasMoreElements();)
            out.history.push( ((Snapshot)e.nextElement()).clone() );
        out.cur = (Snapshot) cur.clone();
        return out;
    }

    static class Snapshot implements Cloneable {

        public Properties pre2ns = null;
        public Properties ns2pre = null;
        public Properties ns2loc = null;
        public int validityDepth = 0;
        static final String CLN = ":";
        static final String empty = "";

        public Snapshot() {
            pre2ns = null;
            ns2pre = null;
            ns2loc = null;
            validityDepth = 0;
            pre2ns = new Properties();
            pre2ns.setProperty("", "");
            ns2pre = new Properties();
            ns2pre.setProperty("", "");
            ns2pre = new Properties();
        }

        public Snapshot(Snapshot parent) {
            pre2ns = new Properties(parent.pre2ns);
            ns2pre = new Properties(parent.ns2pre);
            ns2loc = new Properties(parent.ns2loc);
        }

        public Object clone() {
            Snapshot out;
            try {
                out = (Snapshot)super.clone();
                out.pre2ns = (Properties)pre2ns.clone();
                out.ns2pre = (Properties)ns2pre.clone();
                out.ns2loc = (Properties)ns2loc.clone();
                return out;
            } catch (CloneNotSupportedException ex) {
                throw new InternalError("programmer clone error");
            }
        }

        public void addMapping(String prefix, String uri) {
            if(uri == null)
                throw new NullPointerException("Null URI given");
            if(prefix == null) 
                throw new NullPointerException("Null prefix given");

            pre2ns.setProperty(prefix, uri);
            ns2pre.setProperty(uri, prefix);
        }

        public void addLocation(String namespace, String loc)
        {
            ns2loc.setProperty(namespace, loc);
        }

        public void removeMapping(String prefix)
        {
            if(prefix == null)
                throw new NullPointerException("Null prefix given");
            String uri = (String)pre2ns.remove(prefix);
            if(uri != null)
                ns2pre.remove(uri);
        }

    }
}
