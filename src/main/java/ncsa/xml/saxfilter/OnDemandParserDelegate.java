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
 *  2007/11/16  rlp  reconstructed from jad-decompiled class file
 */
package ncsa.xml.saxfilter;

import java.util.Hashtable;
import java.util.Set;

/**
 * a class that manages instructions via the OnDemandParser interface. <p>
 *
 * This class is intended to help classes that wish to implement the 
 * OnDemandParser interface.  It stores the state of instructions passed
 * to it which can be queried for as needed.  Classes wishing to support 
 * the OnDemandParser interface can keep a OnDemandParserDelegate as a 
 * member field.
 */
public class OnDemandParserDelegate implements OnDemandParser {
    private Hashtable attrSel = new Hashtable();
    private int events = 0;

    /**
     * create an OnDemandParserDelegate with nothing initially enabled.
     */
    public OnDemandParserDelegate() { this(0); }

    /**
     * create an OnDemandParserDelegate
     * @param enableAll  if true, all events will be enabled
     */
    public OnDemandParserDelegate(boolean enableAll) {
        this(enableAll ? 511 : 0);
    }

    /**
     * create an OnDemandParserDelegate with selected events enabled
     * @param events    the OR-ed set of events to enable
     */
    public OnDemandParserDelegate(int events) {
        attrSel = new Hashtable();
        this.events = 0;
        enableEvents(events);
    }

    /**
     * return true if given events are enabled
     * @param events   the OR-ed set of events to check
     */
    public final boolean isEnabled(int events) {
        return (this.events & events) == events;
    }

    /**
     * return true if any of the given events are enabled
     * @param events   the OR-ed set of events to check
     */
    public final boolean anyEnabled(int events) {
        return (this.events & events) > 0;
    }

    /**
     * return the set of attribute names that should be loaded for a 
     * given element.  Note edits to the returned set will affect future
     * accesses to it.
     * @param elname   the qualified or local element name
     * @return Set  a set of Strings representing attribute names
     */
    public final Set getAttrsToLoad(String elname) {
        return (Set)attrSel.get(elname);
    }

    /**
     * enable the given OR-ed collection of events
     * @param events   the set of event codes defined by this interface,
     *                 OR-ed together
     */
    public final void enableEvents(int events) {
        this.events |= events;
    }

    /**
     * return the OR-ed set of currently enabled events
     */
    public final int getEvents() {
        return events;
    }

    /**
     * disable the given OR-ed collection of events
     * @param events   the set of event codes defined by this interface,
     *                 OR-ed together
     */
    public final void disableEvents(int events) {
        this.events &= ~events;
    }

    final void setEvents(int events, boolean yes) {
        if (yes)
            enableEvents(events);
        else
            disableEvents(events);
    }

    /**
     * start or stop sending character events to the ContentHandler
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     */
    public void enableCharacters(boolean yes) {
        setEvents(1, yes);
    }

    /**
     * start or stop sending start/end-document events to the ContentHandler
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     */
    public void enableDocument(boolean yes) {
        setEvents(2, yes);
    }

    /**
     * start or stop namespace parsing.  When processing is disabled, all
     * element and attribute names are passed as local names which may or may
     * not contain a namespace qualifier, and the qualified name will be an
     * empty string.
     * @param yes   if true, namespace parsing will be enabled; otherwise it 
     *                 will be disabled.
     */
    public void enableNamespaces(boolean yes) {
        setEvents(128, yes);
    }

    /**
     * start or stop sending start/end-Prefix events to the ContentHandler
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     */
    public void enablePrefix(boolean yes) {
        setEvents(256, yes);
    }

    /**
     * start or stop sending start/end-Element events to the ContentHandler
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     */
    public void enableElement(boolean yes) {
        setEvents(4, yes);
    }

    /**
     * start or stop sending processing instruction events to the 
     * ContentHandler
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     */
    public void enableProcInstr(boolean yes) {
        setEvents(64, yes);
    }

    /**
     * start or stop sending skipped entity events to the ContentHandler
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     */
    public void enableSkippedEntity(boolean yes) {
        setEvents(32, yes);
    }

    /**
     * start or stop loading Attributes into element events.  This only has
     * an effect when element events are enabled.
     * @param yes   if true, this event will be enabled; otherwise it will 
     *                 be disabled.
     * @see OnDemandParser#loadAttributes(Set)
     */
    public void enableAttributes(boolean yes) {
        setEvents(8, yes);
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
     *                  value resets the selection such that 
     *                  all attributes will be loaded.  An empty set means
     *                  than no attributes for this element should be loaded.
     */
    public void loadAttributes(String elname, Set attnames) {
        if(elname == null)
            elname = "";
        if(attnames == null)
            attrSel.remove(elname);
        else
            attrSel.put(elname, attnames);
    }

    /**
     * return the Set of attributes desired for a given element name
     * @param name   the element name
     * @return Set   the set attribute names that the content handler is 
     *                  interested in.  null is returned if all attributes
     *                  are wanted.
     */
    public Set attributesFor(String name) {
        if(name == null) name = "";
        return (Set) attrSel.get(name);
    }

    /**
     * resume loading all attributes during element events.  This discards
     * all previous attribute selections made with loadAttributes().  
     */
    public void loadAllAttributes() {
        attrSel.clear();
    }
}
