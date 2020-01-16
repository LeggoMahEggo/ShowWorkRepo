import definitions.*;
import org.openqa.selenium.*;
import org.openqa.selenium.Keys;
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

// Class for managing features/methods/other related to Simple Nakdan Mobile tests
public class SNM_Manager
{
    private Browser snmBrowser; // Browser for the Simple Nakdan Mobile tests
    private DefsHandler xmlDefs; // Handles loading of elements from an xml file (contains xpath values)
    private HashSet<Tag> tags; // List of elements ('tags') loaded from said xml file (should be 'definitions.xml')
    
    public SNM_Manager() {
    }
    
    // Sets starting values for the tests
    // (String) browserType: which browser to use ('chrome', 'firefox', or 'edge')
    // (String) langauge: which language to use ('heb' or 'eng')
    public void Setup(String browserType, String language) {
        System.out.println(String.format("Browser being tested: '%s'", browserType));
        
        String pageToLoad = "test_nakdan_simple_mobile";
        System.out.println(String.format("Page Elements: '%s'\nLanguage: '%s'\n", pageToLoad, language));
        
        // Load definition file
        System.out.println("Loading definitions file");
        this.xmlDefs = SAXLoader.LoadXMLFile("definitions.xml", pageToLoad, language);
        this.tags = this.xmlDefs.GetPageElements(pageToLoad, language);
        
        System.out.println(String.format("Loaded %d page(s)", xmlDefs.GetDefinitionData().size()));
        
        // Start web driver
        BrowserDriver browserDriver = new BrowserDriver(browserType);
        WebDriver driver = browserDriver.GetWebDriver();
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        
        // Go to start page
        String testUrl = "https://nakdan.dicta.org.il/";
        this.snmBrowser = new Browser(driver);
        this.snmBrowser.SetWindowSize(638, 688); // Mobile version of site depends on the window's size
        this.snmBrowser.Startup(testUrl, this.tags);
        
        System.out.println("");
    }
    
    // Gets the browser object
    public Browser GetBrowser() {
        return this.snmBrowser;
    }
    
    // Gets the handler for the xml file's tags
    public DefsHandler GetDefs() {
        return this.xmlDefs;
    }
    
    // Misc methods for various tasks
    
    // Updates page list for Firefox browser (due to its quirk of opening new tabs *after* a link has been clicked, bypassing the listener completely)
    public void UpdateFirefoxPages() {
        int pageCountBefore = this.GetBrowser().GetDriver().getWindowHandles().size();
        this.GetBrowser().UpdatePages();
        int pageCountAfter = this.GetBrowser().GetDriver().getWindowHandles().size();
        
        if (pageCountBefore < pageCountAfter) {
            System.out.println(String.format("Updating pages (previous: %d, current: %d)", pageCountBefore, pageCountAfter));
        } else
            System.out.println("No updates to pages required");
    }
    
    // Returns a WebElement based on the XPath of a particular xml tag
    // (String tagName): the name of the tag to load from the definitions file
    public WebElement GetElementFromDefinitions(String tagName) {
        String elementXPath = this.xmlDefs.GetWebElementByName(this.tags, tagName).GetXPath();
        return this.snmBrowser.Elements().GetElementByXPath(elementXPath);
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
     * Methods for interacting with web elements on the simple nakdan mobile page
     */
    
    // Clicks on the 'סגנון/style' button
    public void ClickStyleButton() {
        this.snmBrowser.Elements().GetElementByDef("style_select").click();
    }
    
    // Clicks on a specific radio button of the available nikud styles 
    // (String) style: which style of nikud to click on ('modern', 'rabinic', or 'poetry')
    public void ClickOnStyle(String style) {
        this.snmBrowser.Elements().GetElementByDef(String.format("style_%s", style)).click();
    }
    
    // Clicks the button that applys the nikud style
    public void ApplyStyle() {
        this.snmBrowser.Elements().GetElementByDef("style_confirm").click();
    }
    
    // Puts text into the textarea on the 'enter text here' page
    // (String) textToFill: the text to put in the textarea
    public void FillTextAreaWithText(String textToFill) {
        this.snmBrowser.Elements().GetElementByDef("textarea_insert").sendKeys(textToFill);
    }
    
    // Puts sample text from the definitions file into the textarea on the 'enter text here' page
    // (String) style: the 'style' to get the sample text of ('modern', 'rabinic', or 'poetry')
    public void FillTextAreaWithSampleText(String style) {
        String sampleText = xmlDefs.GetTextTagByName(tags, String.format("test_text_%s", style)).GetValue();
        this.snmBrowser.Elements().GetElementByDef("textarea_insert").sendKeys(sampleText);
    }
    
    // Clicks the 'nikud' button for putting nikud on the text
    public void ClickNikudButton() {
        this.snmBrowser.Elements().GetElementByDef("nikud_button").click();
    }
    
    // Returns the text from the results' textarea.
    // (boolean) continueIfElementsNotPresent: allows the continuation of execution should the textarea be empty (necessary for a specific test)
    public String GetTextFromTextArea(boolean continueIfElementsNotPresent) {
        List<WebElement> elementList = this.snmBrowser.Elements().GetElementsByDef("textarea_texts", continueIfElementsNotPresent);
        return GetTextFromElements(elementList);
    }
    
    // Returns what the expected result should be for a particualar nikud style
    // (String) style: the 'style' to get the sample text of ('modern', 'rabinic', or 'poetry')
    public String GetExpectedTextByStyle(String style) {
        return this.xmlDefs.GetTextTagByName(this.tags, String.format("expected_text_%s", style)).GetValue();
    }
    
    // Clicks on a nikuded word in the results textarea
    // (int) position: which position the word is in in the textarea (1-based)
    public void ClickWord(int position) {
        if (position < 1)
            position = 1;
        
        List<WebElement> elementList = this.snmBrowser.Elements().GetElementsByDef("textarea_texts", false);
        elementList.get(position - 1).click();
        
    }
    
    // Clicks the bar at the bottom of the results, for opening the 'nikud edit' options menu
    public void ClickNikudOptionBar() {
        this.snmBrowser.Elements().GetElementByDef("nikud_optionbar").click();
    }
    
    // Clicks the little 'x' button that closes the nikud edit menu
    public void ClickXInNikudOptionBar() {
        this.snmBrowser.Elements().GetElementByDef("nikud_optionbar_x").click();
    }
    
    // Clicks on an alternative nikud choice, of a previously clicked word
    // (int) position: which position in the list the alternative nikud choice is located (1-based)
    public void ClickWordOption(int position) {
        if (position < 1)
            position = 1;
        
        List<WebElement> elementList = this.snmBrowser.Elements().GetElementsByDef("word_option_list", false);
        elementList.get(position - 1).click();
    }
    
    // Clicks the button that saves the selected alternate nikud option
    public void SaveWordOption() {
        this.snmBrowser.Elements().GetElementByDef("apply_word_option").click();
    }
    
    // Returns the text of a changed word in the results textarea
    public String GetChangedWord() {
        return this.snmBrowser.Elements().GetElementByDef("changed_word").getText();
    }
    
    // Clicks the ':' button in the nikud edit menu (currently only an option to enable morphological descriptions shows up there)
    public void ClickWordOptionToolbar() {
        this.snmBrowser.Elements().GetElementByDef("toolbar_button").click();
    }
    
    // Clicks the button that enables the morphological descriptions of alternate nikud words to show up
    public void ClickMorphologyButton() {
        this.snmBrowser.Elements().GetElementByDef("morphology_button").click();
    }
    
    // Returns the mophological description of an alternate nikud choice
    // (int) position: where in the alternate nikud list to get the description (1-based)
    public String GetMorphologyDescription(int position) {
        if (position < 1)
            position = 1;
        
        List<WebElement> elementList = this.snmBrowser.Elements().GetElementsByDef("morphology_description_list", false);
        return elementList.get(position - 1).getText();
    }
    
    // Clicks the 'manual nikud' option in the edit nikud menu
    public void ClickManualNikudOption() {
        this.snmBrowser.Elements().GetElementByDef("manual_option").click();
    }
    
    // Clicks a specific letter of the chosen word in the manual nikud menu
    // (int) letterToSelect: which letter of the word to select (1-based)
    public void SelectLetterToEdit(int letterToSelect) {
        if (letterToSelect < 1)
            letterToSelect = 1;
        
        List<WebElement> elementList = this.snmBrowser.Elements().GetElementsByDef("letter_selections", false);
        elementList.get(letterToSelect - 1).click();
    }
    
    // Clicks a nikud option for a particular letter of the chosen word in the manual nikud menu
    // (int) letterOption: which nikud option of the letter to select (1-based)
    public void ClickLetterOption(int letterOption) {
        if (letterOption < 1)
            letterOption = 1;
        
        List<WebElement> elementList = this.snmBrowser.Elements().GetElementsByDef("letter_options", false);
        elementList.get(letterOption - 1).click();
    }
    
    // Clicks the button which adds a dagesh to the currently selected letter of the chosen word in the manual nikud menu
    public void ClickAddDageshButton() {
        this.snmBrowser.Elements().GetElementByDef("add_dagesh").click();
    }
    
    // Clicks the button which turns the currently selected letter into one that is used as an eim kriah
    public void ClickSetAsEimKriahButton() {
        this.snmBrowser.Elements().GetElementByDef("set_eim_kriah").click();
    }
    
    // Clicks the arrow button which loads new text into the nakdan for nikuding
    public void ClickLoadNewTextButton() {
        this.snmBrowser.Elements().GetElementByDef("load_new_text").click();
    }
    
    public void ClickNoMorePopupCheckbox() {
        String noPopupCheckboxXPath = this.xmlDefs.GetWebElementByName(this.tags, "nomore_popup_checkbox").GetXPath();
        WebElement element = this.snmBrowser.GetDriver().findElement(By.xpath(noPopupCheckboxXPath));
        
        ((JavascriptExecutor)this.snmBrowser.GetDriver()).executeScript("arguments[0].click();", element);
    }
    
    public boolean DoesPopupPopup() {
        String popupID = this.xmlDefs.GetWebElementByName(this.tags, "popup_new_text").GetID();
        return this.snmBrowser.GetDriver().findElements(By.id(popupID)).size() > 0;
    }
    
    // Clicks the button which actually allows the loading of new text (it appears on a popup)
    public void ClickGotoNewTextButton() {
        this.snmBrowser.Elements().GetElementByDef("goto_new_text").click();
    }
    
    // Clicks the button that copies the nikud results to the clipboard
    public void ClickCopyResultsButton() {
        this.snmBrowser.Elements().GetElementByDef("copy_results").click();
    }
    
    // Clicks the button that allows the sharing of the nikud results to Whatsapp, Facebook, and Email
    public void ClickShareButton() {
        this.snmBrowser.Elements().GetElementByDef("share_button").click();
    }
    
    // Clicks the button that shares the results over Whatsapp
    public void ClickWhatsappButton() {
        this.snmBrowser.Elements().GetElementByDef("whatsapp_button").click();
    }
    
    // Clicks the button that shares the results over Facebook
    public void ClickFacebookButton() {
        this.snmBrowser.Elements().GetElementByDef("facebook_button").click();
    }
    
    // Facebook-only methods for logging into a test account (since the browser loads in private/incognito mode)
    // Puts the test email into the email textbox
    public void FillEmailTextbox() {
        String email = this.xmlDefs.GetTextTagByName(this.tags, "email").GetValue();
        this.snmBrowser.Elements().GetElementByDef("email_textbox").sendKeys(email);
    }
    // Puts the test password into the password textbox
    public void FillPasswordTextbox() {
        String password = this.xmlDefs.GetTextTagByName(this.tags, "password").GetValue();
        this.snmBrowser.Elements().GetElementByDef("password_textbox").sendKeys(password);
    }
    // Clicks the button for logging in
    public void ClickLoginButton() {
        this.snmBrowser.Elements().GetElementByDef("login_button").click();
    }
    
    /*public void ClickEmailButton() {
        String emailButtonXPath = this.xmlDefs.GetWebElementByName(this.tags, "email_button").GetXPath();
        //((JavascriptExecutor)this.snmBrowser.GetDriver()).executeScript("navigator.registerProtocolHandler('mailto','https://mail.google.com/mail/?extsrc=mailto&url=%s','Gmail'); ");
        this.snmBrowser.Elements().ClickElementByXPath(emailButtonXPath);
    }*/
    
    // Returns the nikuded text from the email share button (as the link is a mailto protocol, not an actual link)
    public String GetTextFromEmailShare() throws UnsupportedEncodingException {
        String linkText = this.snmBrowser.Elements().GetElementByDef("email_button").getAttribute("href");
        String emailBodyText = "";
        
        String patternToUse = "(?<=&body=)(.*)";
        Pattern p = Pattern.compile(patternToUse);
        Matcher m = p.matcher(linkText);
        
        if (m.find()) {
            emailBodyText = m.group(0);
        }
        
        return this.ConvertUrlToPlainText(emailBodyText, ""); // Text results are encoded, so it must be decoded
    }
    
    // Clicks on the ':' button that opens the options menu (font size and eim-kriah related)
    public void ClickOptionsButton() {
        this.snmBrowser.Elements().GetElementByDef("options_button").click();
    }
    
    public void ClickPlusFontButton() {
        this.snmBrowser.Elements().GetElementByDef("plus_font_size").click();
    }
    
    public void ClickMinusFontButton() {
        this.snmBrowser.Elements().GetElementByDef("minus_font_size").click();
    }
    
    // Clicks the button which closes the 'extra options' sidebar (opened by clicking the ':' button)
    public void ClickBackArrow() {
        this.snmBrowser.Elements().GetElementByDef("back_arrow").click();
    }
    
    // Clicks a checkbox, responsible for showing eim kriah letters
    // NOTE: for some reason, the setup below is the only way to find and click the eim kriah checkbox
    public void ClickShowEimKriahCheckbox() {
        String eimKriahButtonXPath = this.xmlDefs.GetWebElementByName(this.tags, "eim_kriah_checkbox").GetXPath();
        WebElement element = this.snmBrowser.GetDriver().findElement(By.xpath(eimKriahButtonXPath));
        
        ((JavascriptExecutor)this.snmBrowser.GetDriver()).executeScript("arguments[0].click();", element);
    }
    
    // Clicks the button that saves the changes made in the options menu
    public void ClickSaveOptionsButton() {
        this.snmBrowser.Elements().GetElementByDef("save_options").click();
    }
    
}
