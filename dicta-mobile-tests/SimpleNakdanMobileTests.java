import definitions.*;
import org.junit.Before;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runner.Description;
import org.junit.runners.MethodSorters;
import org.junit.rules.TestWatcher;
import org.openqa.selenium.*;
import org.apache.commons.lang3.*;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SimpleNakdanMobileTests
{
    private static String browserType;
    private static String testUrl;
    private static String nikudStyle;
    private static DefsHandler xmlDefs;
    private static SNM_Manager snmManager;
    private static int testCount;
    
    public SimpleNakdanMobileTests() {
    }
    
    // Gets the current date and time of the local machine
    public static String GetSystemDateTime() {
        SimpleDateFormat formatter= new SimpleDateFormat("dd-MM-yyyy HH:mm:ss z");
        Date date = new Date(System.currentTimeMillis());
        return formatter.format(date);
    }
      
    @BeforeClass
    public static void Setup() {
        System.out.println("Simple Nakdan Mobile Tests\n--------------------------");
        
        testUrl = "https://nakdan.dicta.org.il/";
        //testUrl = "https://mobile-dev--amazing-lamarr-1c4635.netlify.com/";
        //testUrl = "https://english-nakdan-dev--amazing-lamarr-1c4635.netlify.com/";
        
        browserType = "chrome";
        String language = "heb";
        
        // Setup the webdriver and load webelement data
        snmManager = new SNM_Manager();
        snmManager.Setup(browserType, language);
        
        testCount = 0;
        System.out.println(String.format("Starting execution (%s)\n------------------", GetSystemDateTime()));
    }
    
    @AfterClass
    public static void Cleanup() {
        // Prints total tests executed
        String mainMessage = String.format("Ending execution (performed %d test[s])\n", testCount);
        String toAdd = "";
        for (int i = 0; i < mainMessage.length() - 1; i++)
            toAdd += "-";
        mainMessage += toAdd + "\n\n";
        System.out.println(mainMessage);
        
        // Close down the webdriver
        snmManager.GetBrowser().Quit();
    }
    
    @Before
    public void DoBeforeATest() {
        testCount++;
    }
    
    @After
    public void DoSpace() {
        System.out.println(""); // To allow for readability, in the event the test fails
    }
    
    public void SetNikudStyle(String nikudStyle) {
        // Click style button
        System.out.println("Clicking style button");
        snmManager.ClickStyleButton();
        
        // Click appropriate style
        System.out.println(String.format("Selecting %s style", nikudStyle));
        snmManager.ClickOnStyle(nikudStyle);
        
        // Apply style
        System.out.println("Applying style");
        snmManager.ApplyStyle();
    }
    
    public void JustDoNikud(String testIntro, String sampleText) throws InterruptedException {
        System.out.println(String.format("%s (test #%d)", testIntro, testCount));
        
        // Switch to nakdan page
        snmManager.GetBrowser().GoTo(testUrl);
        Thread.sleep(1000);
        
        // Set 'modern' nikud style (used for most tests)
        SetNikudStyle("modern");
        
        // Enter text into the nakdan
        System.out.println(String.format("Filling textbox with sample text ('%s')", sampleText));
        snmManager.FillTextAreaWithText(sampleText);
        
        // Click nikud button to put nikud on text
        System.out.println("Clicking nikud button");
        snmManager.ClickNikudButton();
        Thread.sleep(3000); // Sleep as you get taken to a new page
        snmManager.GetBrowser().UpdateCurrentPage();
    }

    
    public void SetStyleAndDoNikud(String testIntro, String nikudStyle) throws InterruptedException {
        System.out.println(String.format("%s (test #%d)", testIntro, testCount));
        
        // Switch to nakdan page
        snmManager.GetBrowser().GoTo(testUrl);
        Thread.sleep(1000);
        
        // Set 'modern' nikud style (used for most tests)
        SetNikudStyle(nikudStyle);
        
        // Fill nakdan texbox with sample style text
        System.out.println(String.format("Filling textbox with sample %s text", nikudStyle));
        snmManager.FillTextAreaWithSampleText(nikudStyle);
        
        // Click nikud button to put nikud on text
        System.out.println("Clicking nikud button");
        snmManager.ClickNikudButton();
        Thread.sleep(3000); // Sleep as you get taken to a new page
        snmManager.GetBrowser().UpdateCurrentPage();
    }
    
    // Tests simple nakdan mobile, with modern nikud 
    @Test
    public void TestModernNikud() throws InterruptedException {
        SetStyleAndDoNikud("Testing Modern Nikud Placement", "modern");
        
        // Get text that has been put on
        System.out.println("Getting nikuded text");
        String nikudedText = snmManager.GetTextFromTextArea(false);
        
        // Sleep to make sure it goes through, then assert
        Thread.sleep(1000);
        String expectedText = snmManager.GetExpectedTextByStyle("modern");
        Asserting.DoAssertEquals(nikudedText, expectedText);
    }
    
    // Tests simple nakdan mobile, with rabinic nikud (currently *always* fails)
    @Test
    public void TestRabinicNikud() throws InterruptedException {
        SetStyleAndDoNikud("Testing Rabinic Nikud Placement", "rabinic");
        
        // Get text that has been put on
        System.out.println("Getting nikuded text");
        String nikudedText = snmManager.GetTextFromTextArea(false);
        
        // Sleep to make sure it goes through, then assert
        Thread.sleep(1000);
        String expectedText = snmManager.GetExpectedTextByStyle("rabinic");
        Asserting.DoAssertEquals(nikudedText, expectedText);
    }
    
    // Tests simple nakdan mobile, with poetry nikud 
    @Test
    public void TestPoetryNikud() throws InterruptedException {
        SetStyleAndDoNikud("Testing Poetry Nikud Placement", "poetry");
        
        // Get text that has been put on
        System.out.println("Getting nikuded text");
        String nikudedText = snmManager.GetTextFromTextArea(false);
        
        // Sleep to make sure it goes through, then assert
        Thread.sleep(1000);
        String expectedText = snmManager.GetExpectedTextByStyle("poetry");
        Asserting.DoAssertEquals(nikudedText, expectedText);
    }
    
    @Test
    public void TestChangeToWord() throws InterruptedException {
        JustDoNikud("Testing Change To A Word", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");
        
        // Click first word - ויאמר
        System.out.println("Clicking first word");
        snmManager.ClickWord(1);
        Thread.sleep(500);
        
        // Click the third word option - וַיֹּאמַר
        System.out.println("Clicking third word option");
        snmManager.ClickWordOption(3);
        
        // Saving changes
        System.out.println("Saving changes");
        snmManager.SaveWordOption();
        Thread.sleep(500);
        
        // Get changed word and compare
        System.out.println("Getting changed word");
        snmManager.ClickWord(4); // Change the selected word, otherwise it can't be recognized from an xpath since the class is different
        String expectedWord = "וַיֹּאמַר";
        String changedWord = snmManager.GetChangedWord();
        
        Asserting.DoAssertEquals(changedWord, expectedWord);
    }
    
    @Test
    public void TestMorphologyDescription() throws InterruptedException {
        JustDoNikud("Testing Morphological Detail", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");
        
        // Click first word - ויאמר
        System.out.println("Clicking first word");
        snmManager.ClickWord(1);
        Thread.sleep(500);
        
        // Open the toolbar
        System.out.println("Clicking the option toolbar");
        snmManager.ClickWordOptionToolbar();
        
        // Enable morphology descriptions
        System.out.println("Enabling morphology descriptions");
        snmManager.ClickMorphologyButton();
        
        // Check 3rd word ('וַיֹּאמַר') is 'מקראי'
        System.out.println("Checking morphology description of 'וַיֹּאמַר'");
        String retrievedDescription = snmManager.GetMorphologyDescription(3);
        
        Asserting.DoAssertEquals(retrievedDescription, "מקראי");
    }
    
    @Test
    public void TestManualNikud() throws InterruptedException {
        JustDoNikud("Testing Manual Nikud", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");
        
        // Click first word - ויאמר
        System.out.println("Clicking first word");
        snmManager.ClickWord(1);
        Thread.sleep(500);
        
        // Click manual nikud option
        System.out.println("Clicking manual nikud option");
        snmManager.ClickManualNikudOption();
        
        // Selecting a letter to add nikud to - מ
        System.out.println("Selecting letter to edit");
        snmManager.SelectLetterToEdit(4);
        
        // Add a dagesh to the selected letter
        System.out.println("Adding a dagesh to selected letter");
        snmManager.ClickAddDageshButton();
        
        // Clicking a different nikud option for the letter selected
        System.out.println("Changing letter nikud");
        snmManager.ClickLetterOption(4); //מַ
        
        // Saving changes
        System.out.println("Saving changes");
        snmManager.SaveWordOption();
        Thread.sleep(500);
        
        // Checking first word is 'וַיֹּאמַּר'
        snmManager.ClickWord(4); // Change the selected word, otherwise it can't be recognized from an xpath since the class is different
        String editedWord = snmManager.GetChangedWord();
        
        Asserting.DoAssertEquals(editedWord, "וַיֹּאמַּר");
    }
    
    @Test
    public void TestCopyText() throws InterruptedException {
        JustDoNikud("Testing Copy Text Function", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");

        // Click 'copy text' button
        System.out.println("Clicking 'copy text' button");
        snmManager.ClickCopyResultsButton();
        Thread.sleep(500); // To let the popup show oncscreen
        
        // Saving copied text
        System.out.println("Saving copied text from clipboard");
        String clipboardText = snmManager.GetClipboardText();
        System.out.println(String.format("Copied text: '%s'", clipboardText));
        
        // Check that copied text is correct
        String expectedText = "וַיֹּאמֶר ה' אֶל מֹשֶׁה וְאֶל אֶלְעָזָר בֶּן אַהֲרֹן הַכֹּהֵן לֵאמֹר";
        
        Asserting.DoAssertEquals(clipboardText, expectedText);
    }
    
    @Test
    public void TestLoadNewText() throws InterruptedException {
        JustDoNikud("Testing Load New Text", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");
        
        // Click 'load new text' button (back arrow)
        System.out.println("Clicking 'load new text' button");
        snmManager.ClickLoadNewTextButton();
        
        // Click button that takes us back to nakdan page with text cleared
        System.out.println("Clicking button to go back to empty nakdan page");
        snmManager.ClickGotoNewTextButton();
        Thread.sleep(3000); // Sleep as you get taken to a new page
        
        // Get text from textarea
        System.out.println("Getting text from textarea");
        String expectedText = ""; // Text should be empty
        String emptyText = snmManager.GetTextFromTextArea(true);
        
        Asserting.DoAssertEquals(emptyText, expectedText);
    }
    
    @Test
    public void TestShareOverWhatsapp() throws InterruptedException,UnsupportedEncodingException {
        JustDoNikud("Testing Whatsapp Share Feature", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");
        
        // Click share button
        System.out.println("Clicking share button");
        snmManager.ClickShareButton();
        
        // Click whatsapp button
        System.out.println("Clicking Whatsapp button");
        snmManager.ClickWhatsappButton();
        Thread.sleep((browserType.equals("firefox")) ? 3000 : 1000); // Sleep, as a new tab is opened
        
        // If browser is Firefox, update tab list (it's weird like that)
        if (browserType.equals("firefox"))
            snmManager.UpdateFirefoxPages();

        // Switch to new tab and get url
        System.out.println("Retrieving results text from url");
        snmManager.GetBrowser().SwitchToLastPage();
        String toRemove = "https://api.whatsapp.com/send?text=";
        String resultsTextFromUrl = snmManager.ConvertUrlToPlainText(snmManager.GetBrowser().GetFocusedPage().GetUrl(), toRemove);
        snmManager.GetBrowser().ClosePage(snmManager.GetBrowser().GetFocusedPage().GetTitle());
        
        String expectedResults = "וַיֹּאמֶר ה' אֶל מֹשֶׁה וְאֶל אֶלְעָזָר בֶּן אַהֲרֹן הַכֹּהֵן לֵאמֹר";
        Asserting.DoAssertEquals(resultsTextFromUrl, expectedResults);
    }
    
    @Test
    public void TestShareOverFacebook() throws InterruptedException,UnsupportedEncodingException {
        JustDoNikud("Testing Facebook Share Feature", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");

        // Click share button
        System.out.println("Clicking share button");
        snmManager.ClickShareButton();
        
        // Click whatsapp button
        System.out.println("Clicking Facebook button");
        snmManager.ClickFacebookButton();
        Thread.sleep(1000); // Sleep, as a new tab is opened
        
        // If browser is Firefox, update tab list (it's weird like that)
        if (browserType.equals("firefox"))
            snmManager.UpdateFirefoxPages();
        
        // Switch to new tab
        System.out.println("Switching to opened tab");
        snmManager.GetBrowser().SwitchToLastPage();
        
        // Login to Facebook account (test account)
        System.out.println("Logging into Facebook account");
        System.out.println("Entering email");
        snmManager.FillEmailTextbox();
        System.out.println("Entering password");
        snmManager.FillPasswordTextbox();
        System.out.println("Clicking login button");
        snmManager.ClickLoginButton();
        Thread.sleep(3000); // Wait for page to load account
        
        // Get results from the url
        System.out.println("Retrieving results text from url");
        snmManager.GetBrowser().UpdateCurrentPage();
        String toRemove = "https://www.facebook.com/sharer/sharer.php?u=http%3A%2F%2Fnakdan.dicta.org.il%2F&quote=";
        String resultsTextFromUrl = snmManager.ConvertUrlToPlainText(snmManager.GetBrowser().GetFocusedPage().GetUrl(), toRemove);
        resultsTextFromUrl = resultsTextFromUrl.substring(0, resultsTextFromUrl.indexOf("&ext="));
        snmManager.GetBrowser().ClosePage(snmManager.GetBrowser().GetFocusedPage().GetTitle());
        
        String expectedResults = "וַיֹּאמֶר ה' אֶל מֹשֶׁה וְאֶל אֶלְעָזָר בֶּן אַהֲרֹן הַכֹּהֵן לֵאמֹר";
        Asserting.DoAssertEquals(resultsTextFromUrl, expectedResults);
    }
    
    @Test
    public void TestShareOverEmail() throws InterruptedException,UnsupportedEncodingException {
        JustDoNikud("Testing Email Share Feature", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");

        // Click share button
        System.out.println("Clicking share button");
        snmManager.ClickShareButton();
        
        // Get text from email button (it's in the href attribute)
        System.out.println("Getting text from Email button");
        String emailText = snmManager.GetTextFromEmailShare();        
        String expectedText = "וַיֹּאמֶר ה' אֶל מֹשֶׁה וְאֶל אֶלְעָזָר בֶּן אַהֲרֹן הַכֹּהֵן לֵאמֹר";

        Asserting.DoAssertEquals(emailText, expectedText);
    }
    
    @Test
    public void TestIncreaseFont() throws InterruptedException {
        JustDoNikud("Testing Font Increase", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");

        // Click options button
        System.out.println("Clicking options button");
        snmManager.ClickOptionsButton();
        
        // Click font increase button
        System.out.println("Clicking font increase button (10 times)");
        for (int i = 0; i < 10; i++)
            snmManager.ClickPlusFontButton();
        
        // Save font size increase
        System.out.println("Clicking save changes button");
        snmManager.ClickSaveOptionsButton();
        
        // Get text font
        WebElement resultsTextArea = snmManager.GetElementFromDefinitions("textarea_results");
        String expectedSize = "35px";
        String resultsSize = resultsTextArea.getCssValue("font-size");
        
        Asserting.DoAssertEquals(resultsSize, expectedSize);
    }
    
    @Test
    public void TestDecreaseFont() throws InterruptedException {
        JustDoNikud("Testing Font Decrease", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");

        // Click options button
        System.out.println("Clicking options button");
        snmManager.ClickOptionsButton();
        
        // Click font increase button
        System.out.println("Clicking font decrease button (10 times)");
        for (int i = 0; i < 10; i++)
            snmManager.ClickMinusFontButton();
        
        // Save font size increase
        System.out.println("Clicking save changes button");
        snmManager.ClickSaveOptionsButton();
        
        // Get text font
        WebElement resultsTextArea = snmManager.GetElementFromDefinitions("textarea_results");
        String expectedSize = "15px";
        String resultsSize = resultsTextArea.getCssValue("font-size");
        
        Asserting.DoAssertEquals(resultsSize, expectedSize);
    }
    
    @Test
    public void TestShowingEimKriah() throws InterruptedException {
        JustDoNikud("Testing Showing Of Eim Kriah", "מעניין");

        // Click options button
        System.out.println("Clicking options button");
        snmManager.ClickOptionsButton();
        
        // Click 'show eim kriah' checkbox
        System.out.println("Clicking 'show eim kriah' button");
        snmManager.ClickShowEimKriahCheckbox();

        // Save 'show eim kriah' option
        System.out.println("Clicking save changes button");
        snmManager.ClickSaveOptionsButton();
        
        String expectedText = "מְעַנְיֵין";
        String resultsText = snmManager.GetTextFromTextArea(false);
        
        Asserting.DoAssertEquals(resultsText, expectedText);
    }
    
    @Test
    public void TestLittleThings() throws InterruptedException {
        JustDoNikud("Testing Little Things", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");

        // Clicking nikud options bar
        System.out.println("Clicking nikud options bar");
        snmManager.ClickNikudOptionBar();
        
        // Clicking the 'x' button to close the options bar
        System.out.println("Clicking the 'x' button to close the options bar");
        snmManager.ClickXInNikudOptionBar();
        
        // Clicking the back button to close the extra options sidebar
        System.out.println("Clicking options button");
        snmManager.ClickOptionsButton();
        System.out.println("Clicking the back arrow to close the extra options sidebar");
        snmManager.ClickBackArrow();
        
        Asserting.DoAssertEquals(true, true);
    }
    
    @Test
    public void TestLoadNewTextWithNoMorePopup() throws InterruptedException {
        JustDoNikud("Testing No More Popup When Loading New Text", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");
        
        // Click 'load new text' button (back arrow)
        System.out.println("Clicking 'load new text' button");
        snmManager.ClickLoadNewTextButton();
        
        // Click 'do not show again' button
        System.out.println("Clicking 'do not show again' button");
        snmManager.ClickNoMorePopupCheckbox();
        
        // Click button that takes us back to nakdan page with text cleared
        System.out.println("Clicking button to go back to empty nakdan page");
        snmManager.ClickGotoNewTextButton();
        Thread.sleep(3000); // Sleep as you get taken to a new page
        
        // Do nikud again
        System.out.println("Doing nikud again");
        JustDoNikud("", "ויאמר ה' אל משה ואל אלעזר בן אהרן הכהן לאמר");
        
        // Click button that takes us back to nakdan page with text cleared, again
        System.out.println("Clicking button to go back to empty nakdan page - again");
        snmManager.ClickLoadNewTextButton();
        snmManager.GetBrowser().UpdateCurrentPage();
        
        String currentUrl = snmManager.GetBrowser().GetFocusedPage().GetUrl();
        String expectedUrl = "https://nakdan.dicta.org.il/";
        Asserting.DoAssertEquals(currentUrl, expectedUrl);
    }
    
    /*@Test
    public void TestSetLetterAsEimKriah() throws InterruptedException {
        System.out.println("Testing Setting Letter As Eim Kriah");

        // Switch to nakdan page
        snmManager.GetBrowser().GoTo(testUrl);
        Thread.sleep(1000);
        
        // Enter text into the nakdan
        String sampleText = "כיוון";
        System.out.println(String.format("Filling textbox with sample text ('%s')", sampleText));
        snmManager.FillTextAreaWithText(sampleText);
        
        // Click nikud button to put nikud on text
        System.out.println("Clicking nikud button");
        snmManager.ClickNikudButton();
        Thread.sleep(3000); // Sleep as you get taken to a new page
        
        // Click only word - כִּוּוּן
        System.out.println("Clicking word");
        snmManager.ClickWord(1);
        Thread.sleep(500);
        
        // Click manual nikud option
        System.out.println("Clicking manual nikud option");
        snmManager.ClickManualNikudOption();
        
        // Selecting a letter to set as eim kriah - ּו
        System.out.println("Selecting letter to edit");
        snmManager.SelectLetterToEdit(3);
        
        // Setting selected letter as eim kriah
        System.out.println("Setting selected letter as an eim kriah");
        snmManager.ClickSetAsEimKriahButton();
        
        // Saving changes
        System.out.println("Saving changes");
        snmManager.SaveWordOption();
        Thread.sleep(500);
        
        // Checking first word is 'כִּיוּון'
        //snmManager.ClickWord(4); // Change the selected word, otherwise it can't be recognized from an xpath since the class is different
        String expectedWord = "כִּיוּון";
        String editedWord = snmManager.GetTextFromTextArea(false);
        
        DoAssertEquals(editedWord, expectedWord);
    }*/
}
