import definitions.*;
import org.openqa.selenium.*;
import org.openqa.selenium.Keys;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException; 
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Class for managing features/methods/other related to Talmud Search Mobile tests
public class TSM_Manager
{
    private Browser tsmBrowser; // Browser for the Talmud Search Mobile tests
    private DefsHandler xmlDefs; // Handles loading of elements from an xml file (contains xpath/id values)
    private HashSet<Tag> tags; // List of elements ('tags') loaded from said xml file (should be 'definitions.xml')
    private String language; // Which language the Bible Search is operating under
    
    public TSM_Manager() {
    }
    
    // Enums for various webelements
    // Enum for shas seders
    enum FilterBook {
        ZERAIM,
        MOED,
        NASHIM,
        NEZIKIN,
        KADSHIM,
        TAHAROT
    }
    // Enum for the various nikud stylings
    enum NikudStyle {
        NO,
        YES
    }
    // Enum for how many results to show per page
    enum ResultsPerPage {
        TEN,
        FIFTY,
        HUNDRED
    }
    // Enum for file types available to download search results as
    enum FileType {
        HTML,
        TXT,
        CSV,
        WORD
    }
    
    // Sets starting values for the tests
    // (String) browserType: which browser to use ('chrome', 'firefox', or 'edge')
    // (String) langauge: which language to use ('heb' or 'eng')
    public void Setup(String browserType, String language) {
        System.out.println(String.format("Browser being tested: '%s'", browserType));
        
        String pageToLoad = "test_talnach_search_mobile";
        System.out.println(String.format("Page Elements: '%s'\nLanguage: '%s'\n", pageToLoad, language));
        this.language = language;
        
        // Load definition file
        System.out.println("Loading definitions file");
        // All elements are the same for both the hebrew and english versions
        this.xmlDefs = SAXLoader.LoadXMLFile("definitions.xml", pageToLoad, "heb");
        this.tags = this.xmlDefs.GetPageElements(pageToLoad, "heb");
        
        System.out.println(String.format("Loaded %d page(s)", xmlDefs.GetDefinitionData().size()));
        
        // Start web driver
        BrowserDriver browserDriver = new BrowserDriver(browserType);
        WebDriver driver = browserDriver.GetWebDriver();
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        
        // Go to start page
        String testUrl = "http://talmudsearch.dicta.org.il/";
        this.tsmBrowser = new Browser(driver);
        this.tsmBrowser.SetWindowSize(558, 688); // Mobile version of site depends on the window's size
        this.tsmBrowser.Startup(testUrl, this.tags);
        
        System.out.println("");
    }
    
    // Gets the browser
    public Browser GetBrowser() {
        return this.tsmBrowser;
    }
    
    // Gets the handler for the xml file's tags
    public DefsHandler GetDefs() {
        return this.xmlDefs;
    }
    
    /*
     * Misc methods for various tasks
     */
    
    // Updates page list for Firefox browser (due to its quirk of opening new tabs *after* a link has been clicked, bypassing the listener completely)
    public void UpdateFirefoxPages() {
        int pageCountBefore = this.GetBrowser().GetDriver().getWindowHandles().size();
        this.GetBrowser().UpdatePages();
        int pageCountAfter = this.GetBrowser().GetDriver().getWindowHandles().size();
        
        if (pageCountBefore < pageCountAfter) {
            System.out.println(String.format("Updating pages (previous: %d, current: %d)", pageCountBefore, pageCountAfter));
        }
    }
    
    // Takes a list of WebElements, and returns their text concatenated into a single sentence string
    // (List<WebElement>) webElements: a list of WebElements to get text from
    public String GetTextFromElements(List<WebElement> webElements) {
        if (webElements == null)
            return "";
        
        String textToGet = "";
        int counter = 0;
        
        for (WebElement webElement : webElements) {            
            textToGet += webElement.getText();
            counter++;
            
            // Don't add a space if the text just added is the last word
            if (counter < webElements.size()) {
                textToGet += " ";
            }
        }
        
        return textToGet;
    }
    
    // Not written by me. Returns text copied to clipboard
    public String GetClipboardText() {
        try {
            return (String)Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (HeadlessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();            
        } catch (UnsupportedFlavorException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();            
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return "";
    }
    
    // Takes a url-encoded string, and returns it decoded (charset is utf-8). 'whatToRemove' is removed from the url first
    // (String) url: the url to decode
    // (String) toRemove: a string to remove from the url before decodingremoves the 
    public String ConvertUrlToPlainText(String url, String toRemove) throws UnsupportedEncodingException {
        String editedUrl = url.replace(toRemove, "");
        return URLDecoder.decode(editedUrl, "UTF-8");
    }
    
    
    /* 
     * Methods for interacting with the page
     */
    
    // Scrolls to the bottom of the page
    public void ScrollToBottom() {
        JavascriptExecutor je = (JavascriptExecutor)this.tsmBrowser.GetDriver();
        je.executeScript("window.scrollTo(0, document.body.scrollHeight)");
    }
    
    // Clicks the 'Back to Top' element
    public void ClickBackToTop() {
        this.tsmBrowser.Elements().GetElementByDef("back_to_top").click();
    }
    
    // Puts text into the searchbar on the initial page
    // (String) textToFill: the text to put into the searchbar
    public void FillInitialSearchBar(String textToFill) {
        this.tsmBrowser.Elements().GetElementByDef("search_textbox_initial").sendKeys(textToFill);
    }
    
    // Clicks the search button on the initial page
    public void ClickInitialSearchButton() {
        this.tsmBrowser.Elements().GetElementByDef("search_button_initial").click();
    }
    
    // Switches the display langauge used with the Bible/Talmud Search
    // (String) langToSwitchTo: the language to switch to ('heb' or 'eng')
    public void SwitchToLanguage(String langToSwitchTo) {
        WebElement element = this.tsmBrowser.Elements().GetElementByDef("switch_language");
        String currentLang = (element.getText().contains("English")) ? "heb" : "eng"; // It's opposite on the site, if the div has English, the page
        //     is in Hebrew
        
        // Only switch if the language to switch to is not the current display language
        if (!currentLang.equals(langToSwitchTo)) {
            this.tsmBrowser.Elements().GetElementByDef("switch_language").click();
            System.out.println(String.format("Switched browser page-language from '%s' to '%s'", currentLang, langToSwitchTo));
        }
    }
    
    // Retrieves the actual results found on the results page (amount found is defined by the 'Result Pages' display option)
    public List<WebElement> GetResultsOnPageFromSearch() {
        return this.tsmBrowser.Elements().GetElementsByDef("results_list", false);
    }
    
    // Retrieves a result from the search results
    // (int) resultNum: which result to return, 1-based. Unfortunately due to the way the site is front-ended, an additional xpath must be used here
    public WebElement GetAResultFromCurrentPage(int resultNum) {
        if (resultNum < 1)
            resultNum = 1;
        
        List<WebElement> elements = this.GetResultsOnPageFromSearch();
        String versePartXPath = this.xmlDefs.GetWebElementByName(this.tags, "results_text_part").GetXPath();
        
        return elements.get(resultNum - 1).findElement(By.xpath("." + versePartXPath));
    }
    
    // Retrieves the source of a result found on the results page
    // (int) resultNum: which result of the source to get, 1-based. Due to the way the site is front-ended, an additional xpath must be used here
    public WebElement GetASourceOfResultFromCurrentPage(int resultNum) {
        if (resultNum < 1)
            resultNum = 1;
        
        List<WebElement> elements = this.GetResultsOnPageFromSearch();
        String sourcePartXPath = this.xmlDefs.GetWebElementByName(this.tags, "results_source_part").GetXPath();
        
        return elements.get(resultNum - 1).findElement(By.xpath("." + sourcePartXPath));
    }
    
    // Returns all text of a particular result concatenated into a single string
    // (int) resultNum: which result of the source to get, 1-based. Due to the way the site is front-ended, an additional xpath must be used here
    public String GetFullTextOfResult(int resultNum) { 
        if (resultNum < 1)
            resultNum = 1;
        
        List<WebElement> elements = this.GetResultsOnPageFromSearch();
        String versePartXPath = this.xmlDefs.GetWebElementByName(this.tags, "results_text_part").GetXPath();
        
        List<WebElement> resultElements = elements.get(resultNum - 1).findElements(By.xpath("." + versePartXPath));
        
        return GetTextFromElements(resultElements);
    }
    
    /*
     * Misc methods
     */
    
    // Clicks the link that leads back to the search page
    public void ClickToSearchPageLink() {
        this.tsmBrowser.Elements().GetElementByDef("to_search_page").click();
    }
    
    // Clicks the link to the sefaria page of a particular source
    public void ClickLinkToSefariaSource() {
        this.tsmBrowser.Elements().GetElementByDef("to_sefaria_source").click();
    }
    
    // Puts text into the searchbar in the results page
    // (String) textToPut: what text to put in the searchbar
    public void FillSearchbar(String textToPut) {
        WebElement txtBox = this.tsmBrowser.Elements().GetElementByDef("search_textbox");
        txtBox.clear();
        txtBox.sendKeys(textToPut);
    }
    
    // Clicks the search button in the results page
    public void ClickSearchButton() {
        this.tsmBrowser.Elements().GetElementByDef("search_button").click();
    }
    
    
    /* 
     * Sidebar 
     */
    
    // Clicks the button that opens the sidebar for filtering results
    public void ClickSidebarButton() {
        this.tsmBrowser.Elements().GetElementByDef("sidebar_button").click();
    }
    
    // Clicks the button to exit sub-menus in the sidebar
    public void ClickBackButton() {
        this.tsmBrowser.Elements().GetElementByDef("back_button").click();
    }
    
    // Applies the options chosen in the sidebar by clicking the 'done' button
    public void ApplySidebarOptions() {
        //this.tsmBrowser.Elements().GetElementByDef("apply_selections_button").click();
        String applySelectionsButtonXPath = this.xmlDefs.GetWebElementByName(this.tags, "apply_selections_button").GetXPath();
        WebElement element = this.tsmBrowser.GetDriver().findElement(By.xpath(applySelectionsButtonXPath));
        
        ((JavascriptExecutor)this.tsmBrowser.GetDriver()).executeScript("arguments[0].click();", element);
    }
    
    /* 
     * Sidebar - Sorting
     */
    // Opens the menu by which you can choose your sorting options
    public void ClickSortByButton() {
        this.tsmBrowser.Elements().GetElementByDef("sorting_button").click();
    }
    
    // Click the radio button for sorting by talmud order ('sort by relevance' is selected by default)
    public void ClickSortByTalmudOrder() {
        this.tsmBrowser.Elements().GetElementByDef("talnach_order_radio_button").click();
    }
    
    
    
    /* 
     * Sidebar - Filtering
     */
    
    // Wordforms
    // Clicks the button that can filter out results from forms of a particular word
    public void ClickFilterByWordformsButton() {
        this.tsmBrowser.Elements().GetElementByDef(String.format("filter_wordforms_button_%s", this.language)).click();
    }
    
    // Clicks the dropdown list for wordforms of a word (position-based, 1-based)
    // (int) dropdownPos: which wordform dropdown to click
    public void ClickWordformDropdown(int dropdownPos) {
        if (dropdownPos < 1)
            dropdownPos = 1;
        
        List<WebElement> elements = this.tsmBrowser.Elements().GetElementsByDef("filter_wordforms_dropdowns", false);
        elements.get(dropdownPos - 1).click();
    }
    
    // Selects/deselects a particular wordform (1 is 'all wordforms for that word'). All are selected by default, so calling this first will deselect one
    // (int) wordformPos: which wordform to select/deselect
    public void ClickWordformFromOpenDropdown(int wordformPos) {
        if (wordformPos < 1)
            wordformPos = 1;
        
        List<WebElement> elements = this.tsmBrowser.Elements().GetElementsByDef("filter_wordforms_options", false);
        elements.get(wordformPos - 1).click();
    }
    
    
    // Books
    // Clicks the button that can filter out results from particular shas sedarim
    public void ClickFilterByBooksButton() {
        this.tsmBrowser.Elements().GetElementByDef(String.format("filter_books_button_%s", this.language)).click();
    }
    
    // Clicks the dropdown list for the appropriate shas seder
    // (FilterBook) filter: which shas seder dropdown to click ('ZERAIM', 'MOED', 'NASHIM', 'NEZIKIN', 'KADSHIM', or 'TAHAROT')
    public void ClickFilterBookDropdown(FilterBook filter) {
        List<WebElement> elements = this.tsmBrowser.Elements().GetElementsByDef("filter_books_dropdowns", false);
        
        if (filter.equals(FilterBook.TAHAROT))
            elements.get(elements.size() - 1).click();
        else
            elements.get(filter.ordinal()).click();
    }
    
    // Selects/deselects a particular masechet checkbox from an open dropdown (everything is selected by default)
    // (int) bookPos: which book to select/deselect (1 is 'select all')
    public void ClickFilterBookFromOpenDropdown(int bookPos) {
        if (bookPos < 1)
            bookPos = 1;
        
        List<WebElement> elements = this.tsmBrowser.Elements().GetElementsByDef("filter_books_options", false);
        elements.get(bookPos - 1).click();
    }
    
    // Selects or deselects *all* shas sedarim (all are selected by default)
    // (boolean) openDropdowns: whether the shas sedarim dropdowns are already opened. Explained more below
    // NOTE: the front-end makes no differentiation between which books' dropdowns are opened, so this is the best way to deselect/reselect the sedarim.
    public void DeReselectAllFilterBooks(boolean openDropdowns) throws InterruptedException {
        // Open all dropdowns (they stay opened after clicking, so only need to do it once)
        if (openDropdowns) {
            List<WebElement> ddElements = this.tsmBrowser.Elements().GetElementsByDef("filter_books_dropdowns", false);
            for (WebElement element : ddElements) {
                element.click();
            }
        }
        
        // Click the 'select all' element of each one
        String xpathToSelectAlls = this.xmlDefs.GetWebElementByName(this.tags, "filter_books_options").GetXPath() + "[1]"; //1 is 'select all'
        List<WebElement> listElements = this.tsmBrowser.Elements().GetElementsByXPath(xpathToSelectAlls, false);
        
        for (WebElement element: listElements) {
            element.click();
            Thread.sleep(1000);
        }
    }
    
    
    
    /*
     * Display
     */
    
    // Clicks the button that opens the display options submenu in the sidebar
    public void ClickDisplayButton() {
        this.tsmBrowser.Elements().GetElementByDef(String.format("display_options_button_%s", this.language)).click();
    }
    
    // Clicks the appropriate nikud-styling option (none: NO, nikud: YES)
    // (NikudStyle) style: which style to apply
    public void ClickNikudOption(NikudStyle style) {
        List<WebElement> elements = this.tsmBrowser.Elements().GetElementsByDef("display_nikud_styles", false);
        elements.get(style.ordinal()).click();
    }
    
    // Returns the font size of a result (format is '#px')
    // (int) resultNum: which result to get the font size of
    public String GetFontSizeOfResult(int resultNum) {
        WebElement element = this.GetAResultFromCurrentPage(resultNum);
        return element.findElement(By.xpath(".//span")).getCssValue("font-size");
    }
    
    public void ClickIncreaseFontSizeButton() {
        this.tsmBrowser.Elements().GetElementByDef("display_font_size_increase").click();
    }
    
    public void ClickDecreaseFontSizeButton() {
        this.tsmBrowser.Elements().GetElementByDef("display_font_size_decrease").click();
    }
    
    // Clicks the appropriate button that changes how many results per page are shown
    // (ResultsPerPage) rpp: how many results to show per page ('TEN', 'FIFTY', or 'HUNDRED')
    public void ClickResultsPerPageButton(ResultsPerPage rpp) {
        List<WebElement> elements = this.tsmBrowser.Elements().GetElementsByDef("display_results_per_page", false);
        elements.get(rpp.ordinal()).click();
    }
    
    
    
    /*
     * Downloads
     */
    
    // Clicks the button that leads to the Downloads submenu
    public void ClickDownloadAsFileButton() {
        this.tsmBrowser.Elements().GetElementByDef(String.format("download_options_button_%s", this.language)).click();
    }
    
    // Clicks a radio option of which filetype to save the search results to
    // (FileType) type: what filetype to save the results as  ('HTML', 'TXT', 'CSV', or 'WORD')
    public void ClickFileTypeOption(FileType type) {
        List<WebElement> elements = this.tsmBrowser.Elements().GetElementsByDef("file_types_options", false);
        elements.get(type.ordinal()).click();
    }
    
    public void ClickDownloadButton() {
        this.tsmBrowser.Elements().GetElementByDef("actual_download_button").click();
    }
    
    // Note: the checkbox is deselected by default
    public void ClickNoHolyNamesCheckbox() {
        this.tsmBrowser.Elements().GetElementByDef("no_holy_names_checkbox").click();
    }
    
    // Used for debugging problems when verifying saved search results are correct
    public void CompareStringsAndShowFork(String longerLine, String shorterLine) {
        for (int i = 0; i < longerLine.length(); i++) {
            char longerChar = longerLine.charAt(i);
            char shorterChar = shorterLine.charAt(i);
                    
            if (longerChar != shorterChar) {
                System.out.println("#####");
                System.out.println(String.format("i: %d\nlongerChar: %s\nshorterChar: %s", i, longerChar, shorterChar));
                System.out.println("#####");
                break;
            }
        }
    }
    
    // For checking that the search results in the file downloaded are correct. Lines that are equivalent are retained
    // (FileType) type: what filetype to save the results as  ('HTML', 'TXT', 'CSV', or 'WORD')
    // (String) fullPathToFile: the path to the downloaded file + the filename of the downloaded file
    // (List<String>) linesToCheckAgainst: the hard-coded search results to compare against
    public int VerifyDownloadedFileLines(FileType type, String fullPathToFile, List<String> linesToCheckAgainst) throws InvalidFormatException {
        
        // Load file into memory, retain lines in the 'linesToCheckAgainst' equivalent to the loaded, and send the size of said list
        switch(type) {
            case TXT:
            case CSV: 
                List<String> textFileLines = FileLoader.LoadTextFile(fullPathToFile, true);
                linesToCheckAgainst.retainAll(textFileLines);
                break;
            
            
            case HTML:
                List<String> htmlTextLines = FileLoader.LoadHtmlFile(fullPathToFile, false);
                //this.CompareStringsAndShowFork(htmlTextLines.get(35), linesToCheckAgainst.get(0));
                linesToCheckAgainst.retainAll(htmlTextLines);
                break;
            
            
            case WORD:
                List<String> wordTextLines = FileLoader.LoadWordFile(fullPathToFile, false);
                linesToCheckAgainst.retainAll(wordTextLines);
                break;
                
            default: return 9001;
        }
        
        System.out.println("\nLines retained\n--------------");
        for (String line : linesToCheckAgainst)
            System.out.println(line);
        System.out.println("\n");
        
        return linesToCheckAgainst.size();
    }
    
    
    
    /*
     * Pagination
     */
    
    // Clicks the right arrow for page results
    public void ClickNextPageResultsButton() {
        this.tsmBrowser.Elements().GetElementByDef("results_next_button").click();
    }
    
    // Clicks the left arrow for page results
    public void ClickPreviousPageResultsButton() {
        this.tsmBrowser.Elements().GetElementByDef("results_previous_button").click();
    }
    
    // Gets the current page of results you are on
    public int GetCurrentResultsPage() {
        String currentPageText = this.tsmBrowser.Elements().GetElementByDef("amud_text").getText();
        return Integer.parseInt(currentPageText.replace(" ", "").replace("עמוד", "").replace("Page", "").split("/", 2)[0]);
    }
    
    // Returns the total pages found from the search
    public int GetTotalPages() {
        String currentPageText = this.tsmBrowser.Elements().GetElementByDef("amud_text").getText();
        return Integer.parseInt(currentPageText.replace(" ", "").replace("עמוד", "").replace("Page", "").split("/", 2)[1]);
    }
    
    // Returns the total results found by the search+additonal filters
    public int GetTotalResults() {
        
        // Try to return results
        try {
            return Integer.parseInt(this.tsmBrowser.Elements().GetElementByDef("total_results").getText());
        } catch (Exception e) {
            System.out.println(String.format("Error getting results: %s", e.getMessage()));
        }
        
        WebElement element = this.tsmBrowser.Elements().GetElementByDef("no_results");
        return 0;
    }
}