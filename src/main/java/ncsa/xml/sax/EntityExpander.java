/*
 * NCSA BIMAgrid
 * Radio Astronomy Imaging Group
 * National Center for Supercomputing Applications
 * University of Illinois at Urbana-Champaign
 * 605 E. Springfield, Champaign IL 61820
 * rai@ncsa.uiuc.edu
 *-------------------------------------------------------------------------
 * History: 
 *  2003/08/08   rlp  initial version
 */
package ncsa.xml.sax;

import java.io.PrintStream;
import java.util.Enumeration;
import java.util.Properties;

/**
 * a class that can lookup XML entities and expand them to their ASCII form.
 */
public class EntityExpander {

    private Properties map = new Properties();
    private boolean allowRedefine = false;

    public EntityExpander() {
        define("amp", "&");
        define("lt", "<");
        define("gt", ">");
        define("quot", "\"");
        define("apos", "'");
    }

    /**
     * define an entity
     * @param entname    the name of the entity (without the & or ;)
     * @param value      the value to expand the entity to
     */
    public synchronized boolean define(String entname, String value) {
        entname = "&" + entname + ";";
        if (! allowRedefine && map.get(entname) != null) return false;
        map.put(entname, value);
        return true;
    }

    /**
     * return the value that an entity is defined to expand to
     * @param entname    the name of the entity (without the & or ;)
     * @return String    the value to expand the entity to or null if 
     *                      the entity is not defined.
     */
    public String getExpansion(String entname) {
        return map.getProperty(entname);
    }

    /**
     * set whether entities can be redefined once loaded.  
     */
    public void allowRedefine(boolean tf) {
        allowRedefine = tf;
    }

    /**
     * return whether entities can be redefined once loaded.  
     */
    public boolean redefineAllowed() {
        return allowRedefine;
    }

    /**
     * search for and expand any recognized entities
     */
    public synchronized String expand(String in) {
        int a, i, p = 0, n = 0;
        Enumeration e;
        String key = null, sub = null;
        while ((a = in.indexOf("&", p)) >= 0) {
            for(i=0, e = map.keys(); e.hasMoreElements(); i++) {
                key = (String) e.nextElement();
                if (in.startsWith(key, a)) {
                    sub = map.getProperty(key);
                    in = in.substring(0, a) + sub + 
                        in.substring(a + key.length());
                    p = a + sub.length();
                    break;
                }
                i++;
            } 
            if(i >= map.size()) p++;
        } 

        return in;
    }

    public static void main(String args[]) {
        EntityExpander exp = new EntityExpander();
        exp.define("apos", "`");
        exp.define("tab", "\t");
        for(int i = 0; i < args.length; i++)
            System.out.println(exp.expand(args[i]));
    }
}
