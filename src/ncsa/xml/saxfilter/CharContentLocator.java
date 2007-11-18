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
 */
package ncsa.xml.saxfilter;

/**
 * a class for obtaining the location of an XML node in terms of its 
 * character position.  This provides a more efficient handling of locations
 * by a SAXFilteredReader and an attached SAXFilterContentHandler than
 * what is provided by org.xml.sax.Locator (particularly when documents
 * are inserted within other documents).  
 */
public interface CharContentLocator {

    /**
     * return the character position of the start of the current event 
     * being parsed.
     */
    public long getCharNumber();

    /**
     * return the number of characters in the current event
     */
    public int getCharLength();

    /**
     * return a String containing the characters in the current event
     */
    public String getContent();
}
