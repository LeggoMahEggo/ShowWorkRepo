package definitions;
import java.util.*;

// Handler for the 'definitions.xml' file
public class DefsHandler
{
    // Holds tag data
    private HashSet<PTag> pages; // Unique list of pages loaded
    private HashMap<PTag,HashSet<Tag>> pageData; // For each page, a unique list of tags
    protected HashSet<String> pageChecks; // To make sure that duplicate pages aren't added
    
    // Constructor
    public DefsHandler() {
        this.pages = new HashSet<PTag>();
        this.pageData = new HashMap<PTag,HashSet<Tag>>();
        this.pageChecks = new HashSet<String>();
    }
    
    // Returns all data loaded
    public HashMap<PTag, HashSet<Tag>> GetDefinitionData() {
        return pageData;
    }
    
    // Returns a particular page's web-elements
    public HashSet<Tag> GetPageElements(String pageName, String pageLang) {
        PTag pageTag = GetPageByCheck(pageName, pageLang);
        return pageData.get(pageTag);
    }
    
    // Returns a particular tag by its name
    // (HashSet<Tag>) tags: a unique list of tags
    // (String) name: the name of the tag to get
    private Tag GetTagByName(HashSet<Tag> tags, String name) {
        Tag tagToGet = null;
        
        for (Tag tag : tags) {
            String tagName = tag.GetName();
            
            if (tagName.equals(name)) {
                tagToGet = tag;
                break;
            }
        }
        
        if (tagToGet == null) {
            String message = String.format("Could not find requested tag! (name: '%s')\n", name);
            System.out.println(message);
            throw new NullPointerException(message);
        }

        return tagToGet;
    }
    
    // Returns a webelement tag by name
    public WETag GetWebElementByName(HashSet<Tag> tags, String name) {
        return (WETag)GetTagByName(tags, name);
    }
    // Returns a text tag by name
    public TTag GetTextTagByName(HashSet<Tag> tags, String name) {
        return (TTag)GetTagByName(tags, name);
    }    
    
    // Returns a page tag by its name and language
    // (String) pageName: the name of the page
    // (String) pageLang: the language of the page
    public PTag GetPageByCheck(String pageName, String pageLang) {
        String pageCheck = pageName + "|" + pageLang;
        PTag pageTag = null;

        for (PTag pTag : pages) {
            String pCheck = pTag.GetName() + "|" + pTag.GetLang();
            
            if (pCheck.equals(pageCheck)) {
                pageTag = pTag;
                break;
            }
        }
        
        if (pageTag == null) {
            String message = String.format("Could not find page tag! (name: '%s', lang: '%s')\n", pageName, pageLang);
            System.out.println(message);
            throw new NullPointerException(message);
        }
        
        return pageTag;
    }
    
    // Adds an empty 'page' (tag)
    // (String) pageName: the name of the page
    // (String) pageLang: the language of the page
    // (String) pageLink: the url to the page (not used much at the moment)
    protected void AddPage(String pageName, String pageLang, String pageLink) {
        String pageCheck = pageName + "|" + pageLang;
        
        if (!this.pageChecks.contains(pageCheck)) {
            this.pageChecks.add(pageCheck);
            
            PTag pageTag = new PTag(pageName, pageLang, pageLink);
            this.pages.add(pageTag);
            this.pageData.put(pageTag, new HashSet<Tag>());
        }
    }
    
    // Adds a web-element tag class to a specific page
    // (String) pageName: the name of the page the webelement tag goes to
    // (String) pageLang: the language of the page the webelement tag goes to
    // (String) name: the name of the webelement tag
    // (String) xpath: the xpath to the WebElement of the tag
    // (String) id: the id to the WebElement of the tag
    protected void AddWETagToPage(String pageName, String pageLang, String name, String xpath, String id) {
        String pageCheck = pageName + "|" + pageLang;
        
        if (this.pageChecks.contains(pageCheck)) {
            PTag pageTag = this.GetPageByCheck(pageName, pageLang);
            HashSet<Tag> tags = this.pageData.get(pageTag);
            WETag webTag = new WETag(name, xpath, id);
            
            if (!tags.contains(webTag)) {
                tags.add(webTag);
                this.pageData.put(pageTag, tags);
            }
        }
    }
    
    // Adds a text tag class to a specific page
    // (String) pageName: the name of the page the text tag goes to
    // (String) pageLang: the language of the page the text tag goes to
    // (String) name: the name of the text tag
    // (String) value: the text stored in the text tag
    protected void AddTTagToPage(String pageName, String pageLang, String name, String value) {
        String pageCheck = pageName + "|" + pageLang;
        
        // Can only add a text tag if there is a page to add it to
        if (this.pageChecks.contains(pageCheck)) {
            PTag pageTag = this.GetPageByCheck(pageName, pageLang);
            HashSet<Tag> tags = this.pageData.get(pageTag);
            TTag textTag = new TTag(name, value);
            
            // Can only add once
            if (!tags.contains(textTag)) {
                tags.add(textTag);
                this.pageData.put(pageTag, tags);
            }
        }
    }
}
