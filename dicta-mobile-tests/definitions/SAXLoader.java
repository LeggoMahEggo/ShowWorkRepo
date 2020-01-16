package definitions;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;
import java.io.*;
import java.util.*;

public class SAXLoader
{
    // Parses through an XML file and loads the appropriate page data from it
    // (String) filename: the xml file to parse through
    public static DefsHandler LoadXMLFile(String filename) {
        DefsHandler defsData = null;
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();

        try {
            SAXParser saxParser = saxParserFactory.newSAXParser();
            SAXHandler saxHandler = new SAXHandler();
            saxParser.parse(filename, saxHandler); // Parses the xml file
            defsData = saxHandler.GetDefsHandler(); // Gets the defintion data loaded by the parser

            return defsData;
            
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        
        return defsData;
    }
    
    // Overloaded - loads a single page (by langauge)
    // (String) filename: the xml file to parse through
    // (String) pageToLoad: the particular page's elements to load
    // (String) language: the langauge of the page being loaded
    public static DefsHandler LoadXMLFile(String filename, String pageToLoad, String language) {
        DefsHandler defsData = null;
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();

        try {
            SAXParser saxParser = saxParserFactory.newSAXParser();
            SAXHandler saxHandler = new SAXHandler(pageToLoad, language);
            saxParser.parse(filename, saxHandler);
            defsData = saxHandler.GetDefsHandler();

            return defsData;
            
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        
        return defsData;
    }
}
