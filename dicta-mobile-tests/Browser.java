import org.openqa.selenium.*;
import java.util.*;
import java.util.stream.Collectors;
import org.openqa.selenium.support.events.EventFiringWebDriver;
import definitions.*;

// Class for handling the WebDriver's browsing portion
public class Browser
{
    private List<Page> webPages; // Tracks pages open in the browser
    private Page focusedPage; // Current page the browser is focused on
    private ElementsHandler eleHandler; // For interacting with elements on the page
    private EventFiringWebDriver driver; // A special class for WebDriver that can register events to listen for
    private BrowserListener eventListener; // A listener object that listens for events in the browser (specifically here, a change in the open page count)
    
    public Browser(WebDriver driver) {
        // Setup the event listener
        this.driver = new EventFiringWebDriver(driver);
        this.eventListener = new BrowserListener(this);
        this.driver.register(eventListener);
    }
    
    // Startup function, should be executed before any tests are run
    // (String) homeUrl: the url to which the browser will intially navigate to
    // (HashSet<Tag>) tags: a unique list of tags loaded by the definitions file (used to locate WebElements on the page)
    public void Startup(String homeUrl, HashSet<Tag> tags) {
        
        // Setup and move to the 'home' page
        this.driver.navigate().to(homeUrl);
        String homeTitle = this.driver.getTitle();
        String homeID = this.driver.getWindowHandle();
        this.focusedPage = new Page(homeUrl, homeTitle, homeID);
        
        // Setup a list to hold open pages
        this.webPages = new ArrayList();
        this.webPages.add(this.focusedPage);
        
        // Setup the elements handler
        this.eleHandler = new ElementsHandler(this.driver, tags);
    }
    
    // Get the current page
    public Page GetFocusedPage() {
        return this.focusedPage;
    }
    
    // Get methods to interact with web elements
    public ElementsHandler Elements() {
        return this.eleHandler;
    }
    
    // Get the web driver
    public EventFiringWebDriver GetDriver() {
        return this.driver;
    }
    
    // Makes sure that the current page (ie tab) in the browser is the same one (ie the focused one) in the Browser class
    public void UpdateCurrentPage() {
        Page currentPage = new Page(this.driver.getCurrentUrl(), this.driver.getTitle(), this.driver.getWindowHandle());
        
        if (!currentPage.equals(this.focusedPage)) {
            this.webPages.remove(this.focusedPage);
            this.focusedPage = currentPage;
            this.webPages.add(this.focusedPage);
        } else {
            System.out.println("Current page is up-to-date");
        }
    }
    
    // Updates the list of open pages (ie tabs)
    // Only tracks addition of pages to the WebDriver
    public void UpdatePages() {
        Set<String> pageIDs = this.driver.getWindowHandles();
        List<String> webPageIDs = this.webPages.stream().map(Page::GetID).collect(Collectors.toList()); // Get the id portion of open pages

        if (pageIDs.size() > this.webPages.size()) {
            for (String pageID : pageIDs) {
                
                if (!webPageIDs.contains(pageID)) {
                    try {
                        this.driver.switchTo().window(pageID); // Move to the new page (can't get their specific data otherwise)
                        Page newPage = new Page(this.driver.getCurrentUrl(), this.driver.getTitle(), pageID);
                        this.webPages.add(newPage);
                    } catch (Exception e) {
                        System.out.println(String.format("Could not create new page! (Error: %s)", e.getMessage()));
                    }
                }
            }
            this.driver.switchTo().window(this.focusedPage.GetID());
        }
    }
    
    // Refreshes the data about all open pages (ie tabs)
    public void RefreshPageList() {
        
        for (Page webPage : this.webPages) {
            try {
                this.driver.switchTo().window(webPage.GetID());
                webPage.SetTitle(this.driver.getTitle());
                webPage.SetUrl(this.driver.getCurrentUrl());
            } catch (Exception e) {
                System.out.println(String.format("Could not refresh page! (Error: %s)", e.getMessage()));
            }
        }
        
        this.driver.switchTo().window(this.focusedPage.GetID());
    }
   
    // Changes the window's size
    // (int) width: new window-size's width
    // (int) height: new window-size's height
    public void SetWindowSize(int width, int height) {
        this.driver.manage().window().setSize(new Dimension(width, height));
    }
    
    // Navigate to a specific url
    // (String) url: the url to navigate to
    public void GoTo(String url) {
        try {
           this.driver.navigate().to(url);
           
           // Handle alerts when leaving the page
           Alert alert = driver.switchTo().alert();
           if (alert != null) {
               alert.accept();
           }
           //this.driver.switchTo().window(this.focusedPage.GetID());
           
           //this.focusedPage = new Page(url, this.driver.getTitle(), this.focusedPage.GetID());
           this.UpdateCurrentPage();
           System.out.println(String.format("Navigated to %s", this.driver.getTitle()));
       } catch (Exception e) {
           System.out.println(e.getMessage());
       }
    }
    
    // Return Page object by its title
    // (String) pageTitle: the title of a page (eg 'נקדן אוטומטי - חינמי מבית דיקטה')
    public Page GetPageByTitle(String pageTitle) {
        for (Page webPage : this.webPages) {
            if (webPage.GetTitle().equals(pageTitle)) {
                return webPage;
            }
        }
        
        return null;
    }
    
    // Get all open pages
    public List<Page> GetAllPages() {
        return this.webPages;
    }
    
    // Returns the page after the specified page
    // (Page) startFromPage: from which page to start, to get the page after it
    private Page GetNextPage(Page startFromPage) {
        int pageCount = this.webPages.size();
        
        if (pageCount == 1) {
            return this.webPages.get(0);
        }
        
        //boolean isFirstPage = this.webPages.get(0).GetTitle().equals(currentPageTitle);
        boolean isLastPage = this.webPages.get(pageCount - 1).equals(startFromPage);
        
        if (isLastPage) {
            return this.webPages.get(0);
        } else {
            return this.webPages.get(this.webPages.indexOf(startFromPage) + 1);
        }

    }
    
    // Switches current page to one with a particular title
    // (String) pageTitle: the title of a page (eg 'נקדן אוטומטי - חינמי מבית דיקטה')
    public void SwitchToPage(String pageTitle) {
        boolean foundPage = false;
        
        // Do not switch pages if the page to switch to is the current page
        if (focusedPage.GetTitle().equals(pageTitle)) {
            System.out.println(String.format("Attempted to switch page to '%s' - said page is currently in focus", pageTitle));
            return;
        }
        
        // Loop over web pages, switch to it if found
        for (Page webPage : this.webPages) {
            if (webPage.GetTitle().equals(pageTitle)) {
                foundPage = true;
                
                this.focusedPage = webPage;
                this.driver.switchTo().window(webPage.GetID());
                System.out.println(String.format("Switched to '%s'", pageTitle));
                break;
            }
        }
        
        if (!foundPage)
            System.out.println(String.format("Attempted to switch page to '%s'; could not find it", pageTitle));
    }
    
    // Switches to the first page (ie tab)
    public void SwitchToFirstPage() {
        Page firstPage = this.webPages.get(0);
        
        if (firstPage.equals(this.focusedPage)) {
            System.out.println(String.format("Attempted to switch to first page (ie tab) - first page is the current page", firstPage.GetTitle()));
            return;
        }
        
        this.driver.switchTo().window(firstPage.GetID());
        this.focusedPage = firstPage;
        System.out.println(String.format("Switched to last page (ie tab) - ('%s')", firstPage.GetTitle()));
   }
    
    // Switches to the last page (ie tab)
    public void SwitchToLastPage() {
        Page lastPage = this.webPages.get(this.webPages.size() - 1);
        
        if (lastPage.equals(this.focusedPage)) {
            System.out.println(String.format("Attempted to switch to last page (ie tab) - last page is the current page", lastPage.GetTitle()));
            return;
        }
        
        this.driver.switchTo().window(lastPage.GetID());
        this.focusedPage = lastPage;
        System.out.println(String.format("Switched to last page (ie tab) - ('%s')", lastPage.GetTitle()));
   }
   
   // Closes a page (ie tab)
   // (String) pageTitle: the title of a page (eg 'נקדן אוטומטי - חינמי מבית דיקטה')
   public void ClosePage(String pageTitle) {
       boolean foundPage = false;
       
       for (Page webPage : this.webPages) {
           if (webPage.GetTitle().equals(pageTitle)) {
               foundPage = true;
               this.ClosePageHelper(webPage); // Helper method for closing pages
               System.out.println(String.format("Closed '%s'", webPage.GetTitle()));
               break;
           }
       }
       
       if (!foundPage) {
           System.out.println(String.format("'%s' could not be closed (was not found)", pageTitle));
       }
   }
   
   // Helper method for 'ClosePage'
   // (Page) pageToClose: the Page object of the page being closed
   private void ClosePageHelper(Page pageToClose) {
       boolean pageIsFocus = this.focusedPage.equals(pageToClose);
       
       // Exit if the page to close is the only one
       if (this.webPages.size() == 1 && pageIsFocus) {
           Quit();
           return;
       }
       
       // Close page, and if the page being closed is the current one, move to the next page in the list
       if (pageIsFocus) {
           Page nextPage = GetNextPage(this.focusedPage);
           this.webPages.remove(this.focusedPage);
           this.driver.close();
           this.focusedPage = nextPage;
           this.driver.switchTo().window(this.focusedPage.GetID());
       } else {
           Page nextPage = GetNextPage(pageToClose);
           this.driver.switchTo().window(pageToClose.GetID());
           this.driver.close();
           this.webPages.remove(pageToClose);
           this.driver.switchTo().window(nextPage.GetID());
       }
   }
   
   // Closes everything
   public void Quit() {
       this.driver.quit();
   }
}
