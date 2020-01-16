import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.util.*;
import definitions.*;

// Class for getting/interacting with elements in the currently focused page of the browser
public class ElementsHandler
{
    private WebDriver driver; // WebDriver started at the begining of the test(s)
    private int waitTime = 10; // Explicit wait time - 10 seconds
    private HashSet<Tag> tags; // Tags loaded from the definitions file
    
    public ElementsHandler(WebDriver driver, HashSet<Tag> tags) {
        this.driver = driver;
        this.tags = tags;
    }
    // Overloaded - for setting the explicit wait time
    public ElementsHandler(WebDriver driver, HashSet<Tag> tags, int waitTime) {
        this.driver = driver;
        this.tags = tags;
        this.waitTime = waitTime;
    }
    
    // Returns a WebElement by id/xpath by searching through tags loaded through the defintions file (
    // (String) tagName: the name of the tag to search for
    // Note: the method first checks if the tag has an id, then xpath
    public WebElement GetElementByDef(String tagName) {
        WebElement element = null;
        
        for (Tag tag : this.tags) {
            if (tag.GetName().equals(tagName)) {
                WETag webTag = (WETag)tag;
                
                if (!webTag.GetID().equals("")) {
                    element = this.GetElementByID(webTag.GetID());
                } else {
                    element = this.GetElementByXPath(webTag.GetXPath());
                }
            }   
        }
        
        return element;
    }
    
    // Returns a list of WebElements by id/xpath by searching through tags loaded through the defintions file (
    // (String) tagName: the name of the tag to search for
    // (boolean) nulIfNotFound: suppresses end-of-execution if the desired WebElements have not been found (error message is still printed)
    // Note: the method first checks if the tag has an id, then xpath
    public List<WebElement> GetElementsByDef(String tagName, boolean nullIfNotFound) {
        List<WebElement> elements = null;
        
        for (Tag tag : this.tags) {
            if (tag.GetName().equals(tagName)) {
                WETag webTag = (WETag)tag;
                
                if (!webTag.GetID().equals("")) {
                    elements = this.GetElementsByID(webTag.GetID(), nullIfNotFound);
                } else {
                    elements = this.GetElementsByXPath(webTag.GetXPath(), nullIfNotFound);
                }
            }   
        }
        
        return elements;
    }
    
    // Find an element in a page through their id
    // (String) id: the id of the WebElement to get
    public WebElement GetElementByID(String id) {
        WebElement element = null;
       
        try {
            WebDriverWait wait = new WebDriverWait(this.driver, this.waitTime);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(id)));
            element = this.driver.findElement(By.id(id));
           
        } catch (Exception e) { 
            System.out.println(e.getMessage());
        }
       
        return element;
    }
    // Overloaded - lets you set the time for explicit wait
    public WebElement GetElementByID(String id, int waitTime) {
        WebElement element = null;
       
        try {
            WebDriverWait wait = new WebDriverWait(this.driver, waitTime);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(id)));
            element = this.driver.findElement(By.id(id));
            
        } catch (Exception e) { 
            System.out.println(e.getMessage());
        }
       
        return element;
    }
   
   
    // Finds an element in a page through an xpath string
    // (String) xpath: the xpath of the WebElement to get
    public WebElement GetElementByXPath(String xpath) {
        WebElement element = null;
       
        try {
            WebDriverWait wait = new WebDriverWait(this.driver, this.waitTime);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
            element = this.driver.findElement(By.xpath(xpath));
           
        } catch (Exception e) { 
            System.out.println(e.getMessage());
        }
       
        return element;
    }
    // Overloaded - lets you set the time for explicit wait
    public WebElement GetElementByXPath(String xpath, int waitTime) {
        WebElement element = null;
       
        try {
            WebDriverWait wait = new WebDriverWait(this.driver, waitTime);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
            element = this.driver.findElement(By.xpath(xpath));
           
        } catch (Exception e) { 
            System.out.println(e.getMessage());
        }
       
        return element;
    }
   
   
    // Finds a list of elements from an xpath string
    // (String) xpath: the xpath of the WebElement to get
    // (boolean) nulIfNotFound: suppresses end-of-execution if the desired WebElements have not been found (error message is still printed)
    public List<WebElement> GetElementsByXPath(String xpath, boolean nullIfNotFound) {
        List<WebElement> elements = null;
       
        try {
            WebDriverWait wait = new WebDriverWait(this.driver, this.waitTime);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
            elements = this.driver.findElements(By.xpath(xpath));
        } catch (Exception e) {
           
            if (!nullIfNotFound)
                System.out.println(e.getMessage());
            else {
                System.out.println(String.format("Error, continuing execution (message: %s)", e.getMessage()));
                return null;
            }
        }
       
        return elements;
    }
    // Overloaded for explicit wait
    public List<WebElement> GetElementsByXPath(String xpath, boolean nullIfNotFound, int waitTime) {
        List<WebElement> elements = null;
       
        try {
            WebDriverWait wait = new WebDriverWait(this.driver, waitTime);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
            elements = this.driver.findElements(By.xpath(xpath));
        } catch (Exception e) {
           
            if (!nullIfNotFound)
                System.out.println(e.getMessage());
            else {
                System.out.println(String.format("Error, continuing execution (message: %s)", e.getMessage()));
                return null;
            }
        } 
       
        return elements;
    }
   
   // Returns a list of WebElements gotten from an id string
   // (String) id: the id of the WebElements to get
   // (boolean) nulIfNotFound: suppresses end-of-execution if the desired WebElements have not been found (error message is still printed)
    public List<WebElement> GetElementsByID(String id, boolean nullIfNotFound) {
        List<WebElement> elements = null;
        
        try {
            WebDriverWait wait = new WebDriverWait(this.driver, this.waitTime);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(id)));
            elements = this.driver.findElements(By.id(id));
        } catch (Exception e) {
            
            if (!nullIfNotFound)
                System.out.println(e.getMessage());
            else {
                System.out.println(String.format("Error, continuing execution (message: %s)", e.getMessage()));
                return null;
            }
        }
       
        return elements;
    }
    // Overloaded for explicit wait
    public List<WebElement> GetElementsByID(String id, boolean nullIfNotFound, int waitTime) {
        List<WebElement> elements = null;
       
        try {
            WebDriverWait wait = new WebDriverWait(this.driver, waitTime);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(id)));
            elements = this.driver.findElements(By.id(id));
        } catch (Exception e) {
            
            if (!nullIfNotFound)
                System.out.println(e.getMessage());
            else {
                System.out.println(String.format("Error, continuing execution (message: %s)", e.getMessage()));
                return null;
            }
        }
       
        return elements;
    }
}
