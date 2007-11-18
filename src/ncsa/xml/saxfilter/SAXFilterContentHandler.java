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
 *  2001/09/17  rlp  added setFlowController()
 *  2007/11/16  rlp  reconstructed from jad-decompiled class file
 */
package ncsa.xml.saxfilter;

import ncsa.xml.sax.Namespaces;
import org.xml.sax.ContentHandler;

/**
 * an XML SAX ContentHandler with extended capabilities for use with the 
 * SAXFilteredReader.
 */
public interface SAXFilterContentHandler extends ContentHandler {

    /**
     * provide an interface for controlling parsing
     */
    public void setParseRequestMgr(OnDemandParser prm);

    /**
     * provide an interface for controlling content flow
     */
    public void setFlowController(SAXFilterFlowControl control);

    /**
     * provide a namespace tracker
     */
    public void setNamespaces(Namespaces namespaces);
}
