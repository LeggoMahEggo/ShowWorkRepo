package definitions;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import java.util.*;

public class SAXHandler extends DefaultHandler
{
    private String currentLocation = ""; // Which page the parser is on
    private DefsHandler defsHandler; // The definition handler
    
    // For parsing through the file
    private boolean bPage = false;
    private boolean bWebElement = false;
    private boolean bTextElement = false;
    private boolean doEnterPage = false; // Whether or not a page element can be entered
    
    // When loading a single page
    private String pageToLoad = "";
    private String lang = "";
    
    // Load all pages in xml file
    public SAXHandler() {
        this.defsHandler = new DefsHandler();
    }
    // Load a single page from the xml file
    public SAXHandler(String pageToLoad, String lang) {
        this.defsHandler = new DefsHandler();
        this.pageToLoad = pageToLoad;
        this.lang = lang;
    }
    
    // Returns the handler data
    public DefsHandler GetDefsHandler() {
        return this.defsHandler;
    }
    
    /*
     * What to do when encountering an element
     */
    // When an element is first encountered (overriden to implement custom logic)
    // (String) qName: the name of the element
    // (Attributes) attributes: the element's attributes
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        // bPage, bWebElement, and bTextElement are used to determine if the particular element has been entered/exited
        // doEnterPage is for determining if a page element has been passed, and we are within its child-elements
        
        // When encountering a page tag
        if (qName.equalsIgnoreCase("page")) {
            String pageName = attributes.getValue("name"); // Name of the page element
            String pageLang = attributes.getValue("lang"); // Language of the page element ('heb' or 'eng')
            String pageLink = attributes.getValue("link"); // Url to the page (not used)
            String pageCheck = pageName + "|" + pageLang; // Checking if the page has already been added to the definitions handler
            
            // If loading every pages' elements
            if (this.pageToLoad.equals("") && !this.defsHandler.pageChecks.contains(pageCheck)) {
                bPage = true;
                doEnterPage = true;
                this.defsHandler.AddPage(pageName, pageLang, pageLink);
                this.currentLocation = pageCheck;
            
            } 
            // If loading a single page's elements
            else if (pageName.equals(this.pageToLoad) && pageLang.equals(this.lang) && !this.defsHandler.pageChecks.contains(pageCheck)) {
                bPage = true;
                doEnterPage = true;
                this.defsHandler.AddPage(pageName, pageLang, pageLink);
                this.currentLocation = pageCheck;
            }
        } 
        // When encountering a webelement tag, and it is a child-element of a page element
        else if (qName.equalsIgnoreCase("webelement") && doEnterPage == true) {
            bWebElement = true;
            String pageName = this.currentLocation.split("\\|", 2)[0];
            String pageLang = this.currentLocation.split("\\|", 2)[1];
            
            String name = attributes.getValue("name"); // name of the webelement element
            String xpath = attributes.getValue("xpath"); // xpath value
            String id = attributes.getValue("id"); // id value

            this.defsHandler.AddWETagToPage(pageName, pageLang, name, xpath, id);
        } 
        // When encountering a text tag, and it is a child-element of a page element
        else if (qName.equalsIgnoreCase("text") && doEnterPage == true) {
            bTextElement = true;
            String pageName = this.currentLocation.split("\\|", 2)[0];
            String pageLang = this.currentLocation.split("\\|", 2)[1];
            
            String textName = attributes.getValue("name"); // name of the text element
            String textValue = attributes.getValue("value"); // text itself
            
            this.defsHandler.AddTTagToPage(pageName, pageLang, textName, textValue);
        }
    }
    
    // What to do when encountering the end of an element (not necessarily the closing tag)
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (bPage)
            bPage = false;
        else if (bWebElement)
            bWebElement = false;
        else if (bTextElement)
            bTextElement = false;
        
        // When encountering the closing tag of a page element
        if (qName.equalsIgnoreCase("page") && doEnterPage)
            doEnterPage = false;
    }

}
