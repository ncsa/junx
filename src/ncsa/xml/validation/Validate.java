package ncsa.xml.validation;

import ncsa.horizon.util.CmdLine;

import java.io.Reader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder; 
import javax.xml.parsers.DocumentBuilderFactory; 
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.w3c.dom.Document;

/**
 * an application that will validate an XML document against the XML Schemas 
 * that it references.  
 */
public class Validate {

    DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();

    /**
     * validate a list of files
     */
    public static void main(String[] args) {
        CmdLine cl = new CmdLine("s:S:", (CmdLine.RELAX & CmdLine.USRWARN));
        Validate v = new Validate();

        for(int i = 0; i < args.length; i++) {
            try {
                if (v.validate(new FileReader(args[i]), 
                               new PrintWriter(System.out, true))) 
                {
                    System.out.print(args[i]);
                    System.out.println(": valid!");
                }
                else {
                    System.out.print(args[i]);
                    System.out.println(": not valid.");
                }
            } catch (Exception ex) {
                System.err.print("Validation failed: ");
                System.err.println(ex.getMessage());
            }
        }
    }

    /**
     * create the validater
     */
    public Validate(SchemaLocation sl) {
        if (sl == null) sl = new SchemaLocation(getClass());

        ValidationUtils.setForXMLValidation(fact, sl);
    }

    /**
     * create the validater
     */
    public Validate() {
        this(null);
    }

    /**
     * validate the XML document on the given stream
     */
    public boolean validate(Reader doc, PrintWriter errors) 
         throws ParserConfigurationException, IOException
    {
        DocumentBuilder db = fact.newDocumentBuilder();
        Handler eh = new Handler(errors);
        db.setErrorHandler(eh);

        try {
            Document result = db.parse(new InputSource(doc));
            return (result != null && eh.okay);
        }
        catch (SAXException ex) {
            eh.print("ABORT", ex);
            return false;
        }
    }

    class Handler extends DefaultHandler {
        PrintWriter out = null;
        Locator loc = null;
        boolean okay = true;

        public Handler(PrintWriter errors) {
            out = errors;
        }

        public void setDocumentLocator(Locator locator) {
            loc = locator;
        }

        public void error(SAXParseException ex) {
            okay = false;
            print("ERROR", ex);
        }

        public void warning(SAXParseException ex) {
            print("WARNING", ex);
        }

        public void fatalError(SAXParseException ex) {
            okay = false;
            print("FAILURE", ex);
        }

        void print(String level, SAXException ex) {
            out.print('[');
            out.print(level);
            out.print(']');

            if (loc != null) {
                String file = loc.getSystemId();
                int index = -1;
                if (file != null && (index=file.lastIndexOf('/')) > -1) 
                    file = file.substring(index+1);

                out.print(' ');
                out.print(file);
                out.print(':');
                out.print(loc.getLineNumber());
                out.print(':');
                out.print(loc.getColumnNumber());
            }
            out.print(": ");
            out.println(ex.getMessage());
        }
    }


}
