import org.junit.Before;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.Rule;
import org.junit.rules.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.openqa.selenium.*;
import definitions.*;
import java.awt.Robot;
import java.awt.AWTException;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.text.SimpleDateFormat;
import java.io.*;

public class BibleSearchMobileTests
{
    private static String browserType; // Which browser type is being tested with
    private static String testUrl; // The base url to start a test from
    private static String langToUse; // Which langauge to perform the test with (currently only 'heb' or 'eng')
    private static BSM_Manager bsmManager; // The class which manages everything related to Bible Search stuff
    private static int testCount; // Which # test we are currently on
    
    // Whether or not we are checking a downloaded file's extension, or that the data it contains is correct
    enum DownloadActionType {
        CHK_EXT,
        CHK_FILE
    }
    
    public BibleSearchMobileTests() {
    }
    
    // Gets the date and time of the local machine
    public static String GetSystemDateTime() {
        SimpleDateFormat formatter= new SimpleDateFormat("dd-MM-yyyy HH:mm:ss z");
        Date date = new Date(System.currentTimeMillis());
        return formatter.format(date);
    }
    
    @BeforeClass
    public static void Setup() {
        System.out.println("Bible Search Mobile Tests\n--------------------------");
        
        testUrl = "https://use-dicta-components-2--cranky-banach-377068.netlify.com/";
        
        browserType = "chrome";
        langToUse = "heb";
        
        // Setup the webdriver and load webelement data
        bsmManager = new BSM_Manager();
        bsmManager.Setup(browserType, langToUse);
        
        testCount = 0;
        System.out.println(String.format("Starting execution (%s)\n------------------", GetSystemDateTime()));
    }
    
    @AfterClass
    public static void Cleanup() throws IOException {
        System.out.println("Cleanup\n-------");
        
        // Delete downloaded files
        System.out.println("Deleting all downloaded files");
        DeleteAllDownloadedFiles();
        System.out.println("");
        
        // Prints total tests executed
        String mainMessage = String.format("Ending execution (performed %d test[s])\n", testCount);
        String toAdd = "";
        for (int i = 0; i < mainMessage.length() - 1; i++)
            toAdd += "-";
        mainMessage += toAdd + "\n\n";
        System.out.println(mainMessage);
        
        // Close down the webdriver
        bsmManager.GetBrowser().Quit();
    }
    
    @Before
    public void DoBeforeATest() {
        testCount++;
    }
    
    @After
    public void DoSpace() {
        System.out.println(""); // To allow for readability, in the event the test fails
    }
    
    // To avoid repeat code - enters search text and searches
    // (String) testIntro: the intro to the test printed in the console
    // (String) searchText: the text to search for in the searchbar
    public void JustSearch(String testIntro, String searchText) throws InterruptedException {
        System.out.println(String.format("%s (test #%d)", testIntro, testCount));
        
        // Switch to bible search page
        bsmManager.GetBrowser().GoTo(testUrl);
        Thread.sleep(1000);
        bsmManager.SwitchToLanguage(langToUse); // Make sure that the appropriate language page is being used
        
        // Enter text into the searchbar
        System.out.println(String.format("Filling searchbar with sample text ('%s')", searchText));
        bsmManager.FillInitialSearchBar(searchText);
        
        // Click search button
        System.out.println("Clicking search button");
        bsmManager.ClickInitialSearchButton();
        Thread.sleep(3500); // Sleep as you get taken to a new page
        bsmManager.GetBrowser().UpdateCurrentPage();
    }
    
    // To avoid repeat code - enters search text, searches, and then opens the sidebar
    // (String) testIntro: the intro to the test printed in the console
    // (String) searchText: the text to search for in the searchbar
    public void SearchAndOpenSidebar(String testIntro, String searchText) throws InterruptedException {
        JustSearch(testIntro, searchText);
        
        // Click sidebar button
        System.out.println("Clicking the sidebar button");
        bsmManager.ClickSidebarButton();
        
        if (browserType.equals("firefox"))
            Thread.sleep(1000);
    }
    
    // To avoid repeat code - enters search text, searches, opens the sidebar, and downloads the search results to a file
    public void SearchAndDownloadFile(String testIntro, BSM_Manager.FileType fileType, String searchText, 
        boolean noHolyNames) throws InterruptedException {
        SearchAndOpenSidebar(testIntro, searchText);
        
        // Clicking the download button
        System.out.println("Clicking the 'download as file' button");
        bsmManager.ClickDownloadAsFileButton();
        
        // Clicking the html radio option
        System.out.println(String.format("Clicking the '%s' radio option", fileType.name()));
        bsmManager.ClickFileTypeOption(fileType);
        
        // Select the 'download without shemot kedoshim' option
        if (noHolyNames) {
            System.out.println("Selecting the 'Do not include the שמות קדושים' option");
            bsmManager.ClickNoHolyNamesCheckbox();
        }
        
        // Clicking the download button
        System.out.println("Clicking the download button");
        bsmManager.ClickDownloadButton();
        Thread.sleep(3000); // Wait until file is downloaded
    }
    
    // To avoid repeat code - clicks the back button of an open submenu, and applies the option(s) selected therein to the results
    public void MoveBackAndApplyOptions() throws InterruptedException {
        // Click back button
        System.out.println("Clicking the back button");
        bsmManager.ClickBackButton();
        
        // Apply sidebar options
        System.out.println("Applying sidebar options");
        bsmManager.ApplySidebarOptions();
        Thread.sleep((browserType.equals("firefox")) ? 1000 : 500); // Sleep to ensure new results are loaded
    }
    
    public String BrowseDownloadsAndCheckFile(DownloadActionType daType, BSM_Manager.FileType fileType,
        List<String> linesToCheckAgainst) throws InvalidFormatException {
        // Get extension to use when looking for the appropriate downloaded search results file
        String filetypeToCompare = "";
        switch(fileType) {
            case HTML:
                filetypeToCompare = ".html";
                break;
            case TXT:
                filetypeToCompare = ".txt";
                break;
            case CSV:
                filetypeToCompare = ".csv";
                break;
            case WORD:
                filetypeToCompare = ".docx";
                break;
        }
        
        // Loop over the downloads folder, and take the appropriate action
        String valueToReturn = "0";
        File dir = new File(BrowserDriver.downloadPath);
        File[] dirContents = dir.listFiles();
        for (File fileInDownloads : dirContents) {
            if (fileInDownloads.getName().contains(filetypeToCompare)) {
                
                // Take appropriate action
                switch (daType) {
                    case CHK_EXT:
                        valueToReturn = fileInDownloads.getName();
                        break;
                    case CHK_FILE:
                        valueToReturn = Integer.toString(bsmManager.VerifyDownloadedFileLines(fileType, 
                            BrowserDriver.downloadPath + "\\" + fileInDownloads.getName(), linesToCheckAgainst));
                        break;
                }
                
                fileInDownloads.delete(); // Remove file when done
                break;
            }
        }
        
        return valueToReturn;
    }
    
    // Removes all files in the 'downloads' folder
    public static void DeleteAllDownloadedFiles() {
        File dir = new File(BrowserDriver.downloadPath); // Directory to 'downloads' folder
        File[] dirContents = dir.listFiles(); // All files in 'downloads' folder
        
        for (File fileInDownloads : dirContents) {
            System.out.println("Deleting " + fileInDownloads.getName());
            fileInDownloads.delete();
        }
    }
    
    /*
     * Tests for simple searches
     */
    @Test
    public void TestBasicSearch() throws InterruptedException {
        JustSearch("Testing Basic Bible Search", "כבד את אביך ואת אמך");
        
        // Get list of results
        System.out.println("Retrieving search results");
        int resultsCount = bsmManager.GetResultsOnPageFromSearch().size();
        int expectedCount = 2;
        
        Asserting.DoAssertEquals(resultsCount, expectedCount);
    }
    
    @Test
    public void TestSearchAfterSearch() throws InterruptedException {
        JustSearch("Testing Search After Search Performed", "אלהי מסכה");
        
        // Putting new search text into the searchbar
        String newSearchText = "כבד את אביך ואת אמך";
        System.out.println(String.format("Putting new search text into the searchbar ('%s')", newSearchText));
        bsmManager.FillSearchbar(newSearchText);
        
        // Clicking search button
        System.out.println("Clicking search button");
        bsmManager.ClickSearchButton();
        Thread.sleep(3000);
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 2;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestAlternateSpellingSearch() throws InterruptedException {
        JustSearch("Testing Alternate Spelling Search", "\"" + "איש אימו ואביו תראו" + "\"");
        
        // Get list of results
        System.out.println("Retrieving search results");
        int resultsCount = bsmManager.GetResultsOnPageFromSearch().size();
        int expectedCount = 1;
        
        Asserting.DoAssertEquals(resultsCount, expectedCount);
    }
    
    /*
     * Tests for sorting by relevance/tanach order
     */
    
    @Test
    public void TestSortByRelevance() throws InterruptedException {
        JustSearch("Testing Sort By Relevance", "ויהי בעת ההיא");
        
        // Get first result
        System.out.println("Retrieving first search result's text");
        String firstResult = bsmManager.GetAResultFromCurrentPage(1).getText();
        
        String expectedResult = "וַֽיְהִי֙ בָּעֵ֣ת הַהִ֔וא וַיֵּ֥רֶד יְהוּדָ֖ה מֵאֵ֣ת אֶחָ֑יו וַיֵּ֛ט עַד־אִ֥ישׁ עֲדֻלָּמִ֖י וּשְׁמֹ֥ו חִירָֽה׃";
        Asserting.DoAssertEquals(firstResult, expectedResult);
    }
    
    @Test
    public void TestSortByTanachOrder() throws InterruptedException {
        SearchAndOpenSidebar("Testing Sort By Tanach Order", "חוקים ומשפטים");
        
        // Click sort by button
        System.out.println("Clicking the sort-by button");
        bsmManager.ClickSortByButton();
        
        // Click the 'tanach order' radio button
        System.out.println("Clicking the 'Tanach Order' radio button");
        bsmManager.ClickSortByTanachOrder();
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Get list of results
        System.out.println("Retrieving search results");
        List<WebElement> resultsList = bsmManager.GetResultsOnPageFromSearch();
        
        // Add results sources' texts to a list
        List<String> sourcesList = new ArrayList();
        for (int i = 0; i < resultsList.size(); i++) {
            String sourceText = bsmManager.GetASourceOfResultFromCurrentPage(i + 1).getText();
            sourcesList.add(sourceText);
        }
        
        List<String> expectedList = Arrays.asList("דברים ד, ה", "דברים ד, ח", "דברים ד, יד", "יחזקאל כ, כה", "מלאכי ג, כב");
        Asserting.DoAssertEquals(sourcesList, expectedList);
    }
    
    /*
     * Tests for deselection of books
     */
    
    @Test
    public void TestDeselectTorah() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting of Torah Results", "אלהי מסכה");
        
        // Click filter by book button
        System.out.println("Clicking the 'Books' button under the 'Filter' heading");
        bsmManager.ClickFilterByBooksButton();
        
        // Click appropriate filter book dropdown
        System.out.println("Clicking Torah dropdown");
        bsmManager.ClickFilterBookDropdown(BSM_Manager.FilterBook.TORAH);
        
        // Deselect the torah options
        System.out.println("Deselecting Torah books");
        bsmManager.ClickFilterBookFromOpenDropdown(1);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 8;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestDeselectNeviim() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting of Neviim Results", "אלהי מסכה");
        
        // Click filter by book button
        System.out.println("Clicking the 'Books' button under the 'Filter' heading");
        bsmManager.ClickFilterByBooksButton();
        
        // Click appropriate filter book dropdown
        System.out.println("Clicking Neviim dropdown");
        bsmManager.ClickFilterBookDropdown(BSM_Manager.FilterBook.NEVIIM);

        // Deselect the neviim options
        System.out.println("Deselecting Neviim books");
        bsmManager.ClickFilterBookFromOpenDropdown(1);

        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 7;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestDeselectKetuvim() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting of Ketuvim Results", "אלהי מסכה");
        
        // Click filter by book button
        System.out.println("Clicking the 'Books' button under the 'Filter' heading");
        bsmManager.ClickFilterByBooksButton();
        
        // Click appropriate filter book dropdown
        System.out.println("Clicking Ketuvim dropdown");
        bsmManager.ClickFilterBookDropdown(BSM_Manager.FilterBook.KETUVIM);
        
        // Deselect the ketuvim options
        System.out.println("Deselecting Ketuvim books");
        bsmManager.ClickFilterBookFromOpenDropdown(1);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 9;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestReselectAllAfterDeselectAllTanach() throws InterruptedException {
        SearchAndOpenSidebar("Testing Results of Reselection of All Tanach Books After Deselection", "אלהי מסכה");
        
        // DESELECTING
        // Click filter by book button
        System.out.println("Clicking the 'Books' button under the 'Filter' heading");
        bsmManager.ClickFilterByBooksButton();
        
        // Deselect all books
        System.out.println("Deselecting all books");
        bsmManager.DeReselectAllFilterBooks(true);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // RESELECTING
        // Click sidebar button - again
        System.out.println("Clicking the sidebar button - again");
        bsmManager.ClickSidebarButton();
        
        // Click filter by book button - again
        System.out.println("Clicking the 'Books' button under the 'Filter' heading - again");
        bsmManager.ClickFilterByBooksButton();
        
        // Relick all books
        System.out.println("Reselecting all books");
        bsmManager.DeReselectAllFilterBooks(false);
        Thread.sleep(1000); // Sleep to wait for the books to be reselected
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 12;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    /*
     * Tests for deselection of wordforms
     */
    
    @Test
    public void TestDeselectSingleWordform() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting A Single Wordform", "אלהי מסכה");

        // Click wordforms button
        System.out.println("Clicking the wordforms button");
        bsmManager.ClickFilterByWordformsButton();
        
        // Click a wordform dropdown
        System.out.println("Clicking the first wordform dropdown");
        bsmManager.ClickWordformDropdown(1);
        
        // Deselect a single wordform
        System.out.println("Deselecting 2nd wordform");
        bsmManager.ClickWordformFromOpenDropdown(3);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 10;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestDeselectEntireWordform() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting An Entire Wordform", "אלהי מסכה");

        // Click wordforms button
        System.out.println("Clicking the wordforms button");
        bsmManager.ClickFilterByWordformsButton();
        
        // Click a wordform dropdown
        System.out.println("Clicking the first wordform dropdown");
        bsmManager.ClickWordformDropdown(1);
        
        // Deselect an entire wordform
        System.out.println("Deselecting entire wordform");
        bsmManager.ClickWordformFromOpenDropdown(1); // 1 is 'select all'
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 0;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestReselectAfterDeselectEntireWordform() throws InterruptedException {
        SearchAndOpenSidebar("Testing Results of Reselection of A Wordform After Deselection", "אלהי מסכה");
        
        // DESELECTING
        // Click wordforms button
        System.out.println("Clicking the wordforms button");
        bsmManager.ClickFilterByWordformsButton();
        
        // Click a wordform dropdown
        System.out.println("Clicking the first wordform dropdown");
        bsmManager.ClickWordformDropdown(1);
        
        // Deselect an entire wordform
        System.out.println("Deselecting entire wordform");
        bsmManager.ClickWordformFromOpenDropdown(1); // 1 is 'select all'
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // RESELECTING
        // Click sidebar button - again
        System.out.println("Clicking the sidebar button - again");
        bsmManager.ClickSidebarButton();
        
        // Click wordforms button - again
        System.out.println("Clicking the wordforms button - again");
        bsmManager.ClickFilterByWordformsButton();
        
        // Click a wordform dropdown - again
        //System.out.println("Clicking the first wordform dropdown - again");
        //bsmManager.ClickWordformDropdown(1);
        
        // Reselect an entire wordform
        System.out.println("Reselecting entire wordform");
        bsmManager.ClickWordformFromOpenDropdown(1); // 1 is 'select all'
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 12;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    /*
     * Tests for deselection of word meanings+synonyms
     */
    
    @Test
    public void TestDeselectWordMeaning() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting A Word Meaning", "אלהי מסכה");
        
        // Click meanings button
        System.out.println("Clicking the meanings button");
        bsmManager.ClickFilterByMeaningsButton();
        Thread.sleep(1000);
        
        // Click a meaing dropdown
        System.out.println("Clicking the meaning dropdown of the second word");
        bsmManager.ClickMeaningDropdown(2);
        
        // Deselect a single meaning (enabled by default)
        System.out.println("Deselecting the first meaning");
        bsmManager.ClickWordMeaningFromOpenDropdown(2); // 1 is 'select all'
        Thread.sleep(1000);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 2;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestDeselectAllWordMeanings() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting All Word Meanings", "אלהי מסכה");
        
        // Click meanings button
        System.out.println("Clicking the meanings button");
        bsmManager.ClickFilterByMeaningsButton();
        Thread.sleep(1000);
        
        // Click a meaing dropdown
        System.out.println("Clicking the meaning dropdown of the second word");
        bsmManager.ClickMeaningDropdown(2);
        
        // Deselect all meanings for the word (enabled by default)
        System.out.println("Deselecting all meanings");
        bsmManager.ClickWordMeaningFromOpenDropdown(1); // 1 is 'select all'
        Thread.sleep(1000);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 0;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestReselectAfterDeselectAllWordMeanings() throws InterruptedException {
        SearchAndOpenSidebar("Testing Reselecting After Deselecting All Word Meanings", "אלהי מסכה");
        
        // DESELECTING
        // Click meanings button
        System.out.println("Clicking the meanings button");
        bsmManager.ClickFilterByMeaningsButton();
        Thread.sleep(1000);
        
        // Click a meaing dropdown
        System.out.println("Clicking the meaning dropdown of the second word");
        bsmManager.ClickMeaningDropdown(2);
        
        // Deselect all meanings for the word (enabled by default)
        System.out.println("Deselecting all meanings");
        bsmManager.ClickWordMeaningFromOpenDropdown(1); // 1 is 'select all'
        Thread.sleep(1000);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // RESELECTING
        // Click sidebar button - again
        System.out.println("Clicking the sidebar button - again");
        bsmManager.ClickSidebarButton();
        
        // Click meanings button - again
        System.out.println("Clicking the meanings button - again");
        bsmManager.ClickFilterByMeaningsButton();
        Thread.sleep(1000);
        
        // Deselect all meanings for the word (enabled by default) - again
        System.out.println("Deselecting all meanings - again");
        bsmManager.ClickWordMeaningFromOpenDropdown(1); // 1 is 'select all'
        Thread.sleep(1000);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 12;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestEnableSynonym() throws InterruptedException {
        SearchAndOpenSidebar("Testing Enabling Synonym", "אלהי מסכה");

        // Click meanings button
        System.out.println("Clicking the meanings button");
        bsmManager.ClickFilterByMeaningsButton();
        Thread.sleep(1000);
        
        // Click a meaning dropdown
        System.out.println("Clicking the meaning dropdown of the second word");
        bsmManager.ClickMeaningDropdown(2);
        
        // Click the synonym dropdown of the first meaning
        System.out.println("Clicking the first synonym dropdown");
        bsmManager.ClickSynonymDropdown(2);
        
        // Enables the first synonym of the word
        System.out.println("Enabling the first synonym in the list");
        bsmManager.ClickSynonymSwitch(1);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 15;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestDisableSynonymAfterEnable() throws InterruptedException {
        SearchAndOpenSidebar("Testing Disabling Synonym After Being Enabled", "אלהי מסכה");
        
        // ENABLING
        // Click meanings button
        System.out.println("Clicking the meanings button");
        bsmManager.ClickFilterByMeaningsButton();
        Thread.sleep(1000);
        
        // Click a meaning dropdown
        System.out.println("Clicking the meaning dropdown of the second word");
        bsmManager.ClickMeaningDropdown(2);
        
        // Click the synonym dropdown of the first meaning
        System.out.println("Clicking the first synonym dropdown");
        bsmManager.ClickSynonymDropdown(2);
        
        // Enables the first synonym of the word
        System.out.println("Enabling the first synonym in the list");
        bsmManager.ClickSynonymSwitch(1);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // DISABLING
        // Click sidebar button
        System.out.println("Clicking the sidebar button");
        bsmManager.ClickSidebarButton();
        
        // Click meanings button
        System.out.println("Clicking the meanings button");
        bsmManager.ClickFilterByMeaningsButton();
        Thread.sleep(1000);
        
        // Meaning dropdown is still open
        
        // Click the synonym dropdown of the first meaning
        System.out.println("Clicking the first synonym dropdown");
        bsmManager.ClickSynonymDropdown(2);
        
        // Enables the first synonym of the word
        System.out.println("Disabling the first synonym in the list");
        bsmManager.ClickSynonymSwitch(1);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = bsmManager.GetTotalResults();
        int expectedResults = 12;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    /*
     * Tests for display options
     */
    @Test
    public void TestDisplayNoNikud() throws InterruptedException {
        SearchAndOpenSidebar("Testing No Nikud Display Option", "אלהי מסכה");
        
        // Click the display button
        System.out.println("Clicking the display button");
        bsmManager.ClickDisplayButton();
        
        // Click the 'no nikud' style option
        System.out.println("Clicking the 'no nikud' style option");
        bsmManager.ClickNikudOption(BSM_Manager.NikudStyle.NO);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving first search result's text");
        String firstResult = bsmManager.GetAResultFromCurrentPage(1).getText();
        String expectedText = "אלהי מסכה לא תעשה לך";
        
        Asserting.DoAssertEquals(firstResult, expectedText);
    }
    
    @Test
    public void TestDisplayJustNikud() throws InterruptedException {
        SearchAndOpenSidebar("Testing Just Nikud Display Option", "אלהי מסכה");
        
        // Click the display button
        System.out.println("Clicking the display button");
        bsmManager.ClickDisplayButton();
        
        // Click the 'just nikud' style option
        System.out.println("Clicking the 'just nikud' style option");
        bsmManager.ClickNikudOption(BSM_Manager.NikudStyle.YES);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving first search result's text");
        String firstResult = bsmManager.GetAResultFromCurrentPage(1).getText();
        String expectedText = "אֱלֹהֵי מַסֵּכָה לֹא תַעֲשֶׂה לָּךְ";
        
        Asserting.DoAssertEquals(firstResult, expectedText);
    }
    
    @Test
    public void TestDisplayNikudPlusTaamim() throws InterruptedException {
        SearchAndOpenSidebar("Testing Nikud+Taamim Display Option", "אלהי מסכה");
        
        // Click the display button
        System.out.println("Clicking the display button");
        bsmManager.ClickDisplayButton();
        
        // Click the 'nikud+taamim' style option
        System.out.println("Clicking the 'nikud+taamim' style option");
        bsmManager.ClickNikudOption(BSM_Manager.NikudStyle.TAAMIM);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving first search result's text");
        String firstResult = bsmManager.GetAResultFromCurrentPage(1).getText();
        String expectedText = "אֱלֹהֵ֥י מַסֵּכָ֖ה לֹ֥א תַעֲשֶׂה־לָּֽךְ׃";
        
        Asserting.DoAssertEquals(firstResult, expectedText);
    }
    
    @Test
    public void TestIncreaseFontSize() throws InterruptedException {
        SearchAndOpenSidebar("Testing Increasing Font Size", "אלהי מסכה");
        
        // Click the display button
        System.out.println("Clicking the display button");
        bsmManager.ClickDisplayButton();
        
        // Click the 'increase font size' button 5 times
        System.out.println("Clicking the 'increase font size' button 5 times");
        for (int i = 0; i < 5; i++)
            bsmManager.ClickIncreaseFontSizeButton();
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve font size
        System.out.println("Retrieving font size of first search result");
        String fontSize = bsmManager.GetFontSizeOfResult(1);
        String expectedSize = "30px";
        
        Asserting.DoAssertEquals(fontSize, expectedSize);
    }
    
    @Test
    public void TestDecreaseFontSize() throws InterruptedException {
        SearchAndOpenSidebar("Testing Decreasing Font Size", "אלהי מסכה");
        
        // Click the display button
        System.out.println("Clicking the display button");
        bsmManager.ClickDisplayButton();
        
        // Click the 'decrease font size' button 4 times (min font size is 12 pixels)
        System.out.println("Clicking the 'decrease font size' button 4 times");
        for (int i = 0; i < 4; i++)
            bsmManager.ClickDecreaseFontSizeButton();
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve font size
        System.out.println("Retrieving font size of first search result");
        String fontSize = bsmManager.GetFontSizeOfResult(1);
        String expectedSize = "12px";
        
        Asserting.DoAssertEquals(fontSize, expectedSize);
    }
    
    @Test
    public void TestShow10ResultsOnPage() throws InterruptedException {
        SearchAndOpenSidebar("Testing Showing 10 Results On Page", "שלום");
        
        // Click the display button
        System.out.println("Clicking the display button");
        bsmManager.ClickDisplayButton();
        
        // Click the '10 results' option
        System.out.println("Clicking the '10 results per page' option");
        bsmManager.ClickResultsPerPageButton(BSM_Manager.ResultsPerPage.TEN);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Get the number of results on the page
        int resultsOnPage = bsmManager.GetResultsOnPageFromSearch().size();
        int expectedCount = 10;
        
        Asserting.DoAssertEquals(resultsOnPage, expectedCount);
    }
    
    @Test
    public void TestShow50ResultsOnPage() throws InterruptedException {
        SearchAndOpenSidebar("Testing Showing 50 Results On Page", "שלום");
        
        // Click the display button
        System.out.println("Clicking the display button");
        bsmManager.ClickDisplayButton();
        
        // Click the '50 results' option
        System.out.println("Clicking the '50 results per page' option");
        bsmManager.ClickResultsPerPageButton(BSM_Manager.ResultsPerPage.FIFTY);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Get the number of results on the page
        int resultsOnPage = bsmManager.GetResultsOnPageFromSearch().size();
        int expectedCount = 50;
        
        Asserting.DoAssertEquals(resultsOnPage, expectedCount);
    }
    
    @Test
    public void TestShow100ResultsOnPage() throws InterruptedException {
        SearchAndOpenSidebar("Testing Showing 100 Results On Page", "שלום");
        
        // Click the display button
        System.out.println("Clicking the display button");
        bsmManager.ClickDisplayButton();
        
        // Click the '100 results' option
        System.out.println("Clicking the '100 results per page' option");
        bsmManager.ClickResultsPerPageButton(BSM_Manager.ResultsPerPage.HUNDRED);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Get the number of results on the page
        int resultsOnPage = bsmManager.GetResultsOnPageFromSearch().size();
        int expectedCount = 100;
        
        Asserting.DoAssertEquals(resultsOnPage, expectedCount);
    }
    
    /*
     * Downloads
     */
    @Test
    public void TestDownloadResultsAsHtml() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloading Search Results As An Html File", BSM_Manager.FileType.HTML, "אלהי מסכה", false);
        
        // Get filename of file downloaded
        System.out.println("Downloaded file; retrieving filename");
        String downloadedFilename = BrowseDownloadsAndCheckFile(DownloadActionType.CHK_EXT, BSM_Manager.FileType.HTML, null);
        
        String expectedFilename = "searchResults.html";
        Asserting.DoAssertEquals(downloadedFilename, expectedFilename);
    }
    
    @Test
    public void TestDownloadedHtmlFileIsCorrect() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing The Downloaded Html File Is Correct", BSM_Manager.FileType.HTML, "\"" + "בדרך חרבו שלופה בידו" + "\"", false);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList(
            "S", "תנ\"ך/תורה/ספר במדבר/פרק כב/פסוק לא", "וַיְגַל יְהוָה אֶת עֵינֵי בִלְעָם וַיַּרְא אֶת מַלְאַךְ יְהוָה נִצָּב בַּדֶּרֶךְ וְחַרְבֹּו שְׁלֻפָה בְּיָדֹו וַיִּקֹּד וַיִּשְׁתַּחוּ לְאַפָּיו",
            "S", "תנ\"ך/תורה/ספר במדבר/פרק כב/פסוק כג", "וַתֵּרֶא הָאָתֹון אֶת מַלְאַךְ יְהוָה נִצָּב בַּדֶּרֶךְ וְחַרְבֹּו שְׁלוּפָה בְּיָדֹו וַתֵּט הָאָתֹון מִן הַדֶּרֶךְ וַתֵּלֶךְ בַּשָּׂדֶה וַיַּךְ בִּלְעָם אֶת הָאָתֹון לְהַטֹּתָהּ הַדָּרֶךְ"));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, BSM_Manager.FileType.HTML, linesToCheckAgainst));    
        
        int expectedLines = 4;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestNoHolyNamesInHtmlFile() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloaded Html File For No Holy Names", BSM_Manager.FileType.HTML, "אנכי ה' כאשר צווך", true);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList(
            "S", "תנ\"ך/תורה/ספר דברים/פרק ו",
            "S", "(ג) וְשָׁמַעְתָּ יִשְׂרָאֵל וְשָׁמַרְתָּ לַעֲשֹׂות אֲשֶׁר יִיטַב לְךָ וַאֲשֶׁר תִּרְבּוּן מְאֹד כַּאֲשֶׁר דִּבֶּר יְ־וָה אֱ־ֹהֵי אֲבֹתֶיךָ לָךְ אֶרֶץ זָבַת חָלָב וּדְבָשׁ",
            "S", "(ד) שְׁמַע יִשְׂרָאֵל יְ־וָה אֱ־ֹהֵינוּ יְ־וָה אֶחָד",
            "S", "(ה) וְאָהַבְתָּ אֵת יְ־וָה אֱ־ֹהֶיךָ בְּכׇל לְבָבְךָ וּבְכׇל נַפְשְׁךָ וּבְכׇל מְאֹדֶךָ",
            "S", "(יד) לֹא תֵלְכוּן אַחֲרֵי אֱ־ֹהִים אֲחֵרִים מֵאֱ־ֹהֵי הָעַמִּים אֲשֶׁר סְבִיבֹותֵיכֶם",
            "S", "(טו) כִּי אֵ־ קַנָּא יְ־וָה אֱ־ֹהֶיךָ בְּקִרְבֶּךָ פֶּן יֶחֱרֶה אַף יְ־וָה אֱ־ֹהֶיךָ בָּךְ וְהִשְׁמִידְךָ מֵעַל פְּנֵי הָאֲדָמָה",
            "S", "(יז) שָׁמֹור תִּשְׁמְרוּן אֶת מִצְוֹת יְ־וָה אֱ־ֹהֵיכֶם וְעֵדֹתָיו וְחֻקָּיו אֲשֶׁר צִוָּךְ"
            ));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, BSM_Manager.FileType.HTML, linesToCheckAgainst));    
        
        int expectedLines = 7;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestDownloadResultsAsTxt() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloading Search Results As A Txt File", BSM_Manager.FileType.TXT, "אלהי מסכה", false);
        
        // Get filename of file downloaded
        System.out.println("Downloaded file; retrieving filename");
        String downloadedFilename = BrowseDownloadsAndCheckFile(DownloadActionType.CHK_EXT, BSM_Manager.FileType.TXT, null);;
        
        String expectedFilename = "searchResults.txt";
        Asserting.DoAssertEquals(downloadedFilename, expectedFilename);
    }
    
    @Test
    public void TestDownloadedTxtFileIsCorrect() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing The Downloaded Text File Is Correct", BSM_Manager.FileType.TXT, "\"" + "בדרך חרבו שלופה בידו" + "\"", false);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList("Search Results",
            "S", "תנ\"ך/תורה/ספר במדבר/פרק כב/פסוק לא", "וַיְגַל יְהוָה אֶת עֵינֵי בִלְעָם וַיַּרְא אֶת מַלְאַךְ יְהוָה נִצָּב *בַּדֶּרֶךְ* *וְחַרְבֹּו* *שְׁלֻפָה* *בְּיָדֹו* וַיִּקֹּד וַיִּשְׁתַּחוּ לְאַפָּיו",
            "S", "תנ\"ך/תורה/ספר במדבר/פרק כב/פסוק כג", "וַתֵּרֶא הָאָתֹון אֶת מַלְאַךְ יְהוָה נִצָּב *בַּדֶּרֶךְ* *וְחַרְבֹּו* *שְׁלוּפָה* *בְּיָדֹו* וַתֵּט הָאָתֹון מִן הַדֶּרֶךְ וַתֵּלֶךְ בַּשָּׂדֶה וַיַּךְ בִּלְעָם אֶת הָאָתֹון לְהַטֹּתָהּ הַדָּרֶךְ"));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, BSM_Manager.FileType.TXT, linesToCheckAgainst));    
        
        int expectedLines = 5;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestNoHolyNamesInTxtFile() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloaded Txt File For No Holy Names", BSM_Manager.FileType.TXT, "אנכי ה' כאשר צווך", true);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList("Search Results",
            "S", "תנ\"ך/תורה/ספר דברים/פרק ו",
            "S", "(ג) וְשָׁמַעְתָּ יִשְׂרָאֵל וְשָׁמַרְתָּ לַעֲשֹׂות אֲשֶׁר יִיטַב לְךָ וַאֲשֶׁר תִּרְבּוּן מְאֹד *כַּאֲשֶׁר* דִּבֶּר *יְ־וָה* אֱ־ֹהֵי אֲבֹתֶיךָ לָךְ אֶרֶץ זָבַת חָלָב וּדְבָשׁ",
            "S", "(ד) שְׁמַע יִשְׂרָאֵל *יְ־וָה* אֱ־ֹהֵינוּ *יְ־וָה* אֶחָד",
            "S", "(ה) וְאָהַבְתָּ אֵת *יְ־וָה* אֱ־ֹהֶיךָ בְּכׇל לְבָבְךָ וּבְכׇל נַפְשְׁךָ וּבְכׇל מְאֹדֶךָ",
            "S", "(יד) לֹא תֵלְכוּן אַחֲרֵי אֱ־ֹהִים אֲחֵרִים מֵאֱ־ֹהֵי הָעַמִּים אֲשֶׁר סְבִיבֹותֵיכֶם",
            "S", "(טו) כִּי אֵ־ קַנָּא *יְ־וָה* אֱ־ֹהֶיךָ בְּקִרְבֶּךָ פֶּן יֶחֱרֶה אַף *יְ־וָה* אֱ־ֹהֶיךָ בָּךְ וְהִשְׁמִידְךָ מֵעַל פְּנֵי הָאֲדָמָה",
            "S", "(יז) שָׁמֹור תִּשְׁמְרוּן אֶת מִצְוֹת *יְ־וָה* אֱ־ֹהֵיכֶם וְעֵדֹתָיו וְחֻקָּיו אֲשֶׁר *צִוָּךְ*"
            ));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, BSM_Manager.FileType.TXT, linesToCheckAgainst));    
        
        int expectedLines = 8;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestDownloadResultsAsCsv() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloading Search Results As A Csv File", BSM_Manager.FileType.CSV, "אלהי מסכה", false);
        
        // Get filename of file downloaded
        System.out.println("Downloaded file; retrieving filename");
        String downloadedFilename = BrowseDownloadsAndCheckFile(DownloadActionType.CHK_EXT, BSM_Manager.FileType.CSV, null);;
        
        String expectedFilename = "searchResults.csv";
        Asserting.DoAssertEquals(downloadedFilename, expectedFilename);
    }
    
    @Test
    public void TestDownloadedCsvFileIsCorrect() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing The Downloaded Csv File Is Correct", BSM_Manager.FileType.CSV, "\"" + "בדרך חרבו שלופה בידו" + "\"", false);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList(
            "S", "\"תנ\"\"ך/תורה/ספר במדבר/פרק כב/פסוק לא\",וַיְגַל יְהוָה אֶת עֵינֵי בִלְעָם וַיַּרְא אֶת מַלְאַךְ יְהוָה נִצָּב *בַּדֶּרֶךְ* *וְחַרְבֹּו* *שְׁלֻפָה* *בְּיָדֹו* וַיִּקֹּד וַיִּשְׁתַּחוּ לְאַפָּיו",
            "S", "\"תנ\"\"ך/תורה/ספר במדבר/פרק כב/פסוק כג\",וַתֵּרֶא הָאָתֹון אֶת מַלְאַךְ יְהוָה נִצָּב *בַּדֶּרֶךְ* *וְחַרְבֹּו* *שְׁלוּפָה* *בְּיָדֹו* וַתֵּט הָאָתֹון מִן הַדֶּרֶךְ וַתֵּלֶךְ בַּשָּׂדֶה וַיַּךְ בִּלְעָם אֶת הָאָתֹון לְהַטֹּתָהּ הַדָּרֶךְ"));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, BSM_Manager.FileType.CSV, linesToCheckAgainst));    
               
        int expectedLines = 2;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestNoHolyNamesInCsvFile() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloaded Csv File For No Holy Names", BSM_Manager.FileType.CSV, "אנכי ה' כאשר צווך", true);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList(
            "S", "\"תנ\"\"ך/תורה/ספר דברים/פרק ו\",\"(א) וְזֹאת הַמִּצְוָה הַחֻקִּים וְהַמִּשְׁפָּטִים אֲשֶׁר צִוָּה *יְ־וָה* אֱ־ֹהֵיכֶם לְלַמֵּד אֶתְכֶם לַעֲשֹׂות בָּאָרֶץ אֲשֶׁר אַתֶּם עֹבְרִים שָׁמָּה לְרִשְׁתָּהּ",
            "S", "(ג) וְשָׁמַעְתָּ יִשְׂרָאֵל וְשָׁמַרְתָּ לַעֲשֹׂות אֲשֶׁר יִיטַב לְךָ וַאֲשֶׁר תִּרְבּוּן מְאֹד *כַּאֲשֶׁר* דִּבֶּר *יְ־וָה* אֱ־ֹהֵי אֲבֹתֶיךָ לָךְ אֶרֶץ זָבַת חָלָב וּדְבָשׁ",
            "S", "(ד) שְׁמַע יִשְׂרָאֵל *יְ־וָה* אֱ־ֹהֵינוּ *יְ־וָה* אֶחָד",
            "S", "(ה) וְאָהַבְתָּ אֵת *יְ־וָה* אֱ־ֹהֶיךָ בְּכׇל לְבָבְךָ וּבְכׇל נַפְשְׁךָ וּבְכׇל מְאֹדֶךָ",
            "S", "(יד) לֹא תֵלְכוּן אַחֲרֵי אֱ־ֹהִים אֲחֵרִים מֵאֱ־ֹהֵי הָעַמִּים אֲשֶׁר סְבִיבֹותֵיכֶם",
            "S", "(טו) כִּי אֵ־ קַנָּא *יְ־וָה* אֱ־ֹהֶיךָ בְּקִרְבֶּךָ פֶּן יֶחֱרֶה אַף *יְ־וָה* אֱ־ֹהֶיךָ בָּךְ וְהִשְׁמִידְךָ מֵעַל פְּנֵי הָאֲדָמָה",
            "S", "(יז) שָׁמֹור תִּשְׁמְרוּן אֶת מִצְוֹת *יְ־וָה* אֱ־ֹהֵיכֶם וְעֵדֹתָיו וְחֻקָּיו אֲשֶׁר *צִוָּךְ*"
            ));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, BSM_Manager.FileType.CSV, linesToCheckAgainst));    
        
        int expectedLines = 7;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestDownloadResultsAsWord() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloading Search Results As A Word Document", BSM_Manager.FileType.WORD, "אלהי מסכה", false);
        
        // Get filename of file downloaded
        System.out.println("Downloaded file; retrieving filename");
        String downloadedFilename = BrowseDownloadsAndCheckFile(DownloadActionType.CHK_EXT, BSM_Manager.FileType.WORD, null);
        
        String expectedFilename = "searchResults.docx";
        Asserting.DoAssertEquals(downloadedFilename, expectedFilename);
    }
    
    @Test
    public void TestDownloadedWordFileIsCorrect() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing The Downloaded Word File Is Correct", BSM_Manager.FileType.WORD, "\"" + "בדרך חרבו שלופה בידו" + "\"", false);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList(
            "S", "תנ\"ך/תורה/ספר במדבר/פרק כב/פסוק לא", "וַיְגַל יְהוָה אֶת עֵינֵי בִלְעָם וַיַּרְא אֶת מַלְאַךְ יְהוָה נִצָּב בַּדֶּרֶךְ וְחַרְבֹּו שְׁלֻפָה בְּיָדֹו וַיִּקֹּד וַיִּשְׁתַּחוּ לְאַפָּיו",
            "S", "תנ\"ך/תורה/ספר במדבר/פרק כב/פסוק כג", "וַתֵּרֶא הָאָתֹון אֶת מַלְאַךְ יְהוָה נִצָּב בַּדֶּרֶךְ וְחַרְבֹּו שְׁלוּפָה בְּיָדֹו וַתֵּט הָאָתֹון מִן הַדֶּרֶךְ וַתֵּלֶךְ בַּשָּׂדֶה וַיַּךְ בִּלְעָם אֶת הָאָתֹון לְהַטֹּתָהּ הַדָּרֶךְ"));
            
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, BSM_Manager.FileType.WORD, linesToCheckAgainst));

        int expectedLines = 4;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestNoHolyNamesInWordFile() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloaded Word File For No Holy Names", BSM_Manager.FileType.WORD, "אנכי ה' כאשר צווך", true);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList(
            "S", "תנ\"ך/תורה/ספר דברים/פרק ו",
            "S", "(ג) וְשָׁמַעְתָּ יִשְׂרָאֵל וְשָׁמַרְתָּ לַעֲשֹׂות אֲשֶׁר יִיטַב לְךָ וַאֲשֶׁר תִּרְבּוּן מְאֹד כַּאֲשֶׁר דִּבֶּר יְ־וָה אֱ־ֹהֵי אֲבֹתֶיךָ לָךְ אֶרֶץ זָבַת חָלָב וּדְבָשׁ",
            "S", "(ד) שְׁמַע יִשְׂרָאֵל יְ־וָה אֱ־ֹהֵינוּ יְ־וָה אֶחָד",
            "S", "(ה) וְאָהַבְתָּ אֵת יְ־וָה אֱ־ֹהֶיךָ בְּכׇל לְבָבְךָ וּבְכׇל נַפְשְׁךָ וּבְכׇל מְאֹדֶךָ",
            "S", "(יד) לֹא תֵלְכוּן אַחֲרֵי אֱ־ֹהִים אֲחֵרִים מֵאֱ־ֹהֵי הָעַמִּים אֲשֶׁר סְבִיבֹותֵיכֶם",
            "S", "(טו) כִּי אֵ־ קַנָּא יְ־וָה אֱ־ֹהֶיךָ בְּקִרְבֶּךָ פֶּן יֶחֱרֶה אַף יְ־וָה אֱ־ֹהֶיךָ בָּךְ וְהִשְׁמִידְךָ מֵעַל פְּנֵי הָאֲדָמָה",
            "S", "(יז) שָׁמֹור תִּשְׁמְרוּן אֶת מִצְוֹת יְ־וָה אֱ־ֹהֵיכֶם וְעֵדֹתָיו וְחֻקָּיו אֲשֶׁר צִוָּךְ"
            ));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, BSM_Manager.FileType.WORD, linesToCheckAgainst));    
        
        int expectedLines = 7;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    /*
     * Pagination
     */
    @Test
    public void TestGoToNextPageResults() throws InterruptedException {
        JustSearch("Testing Moving To Next Pages Of Results", "שלום");
        
        // Move to the 5th page of results
        System.out.println("Moving to the 5th page of results");
        for (int i = 1; i < 5; i++)
            bsmManager.ClickNextPageResultsButton();
            Thread.sleep(1000);
        
        // Retrieve the current results' page
        System.out.println("Retrieving the current results' page");
        int currentPage = bsmManager.GetCurrentResultsPage();
        
        int expectedPage = 5;    
        Asserting.DoAssertEquals(currentPage, expectedPage);
    }
    
    @Test
    public void TestGoToPreviousPageResults() throws InterruptedException {
        JustSearch("Testing Moving To Next Pages Of Results", "שלום");
        
        // Move to the 5th page of results
        System.out.println("Moving to the 5th page of results");
        for (int i = 1; i < 5; i++)
            bsmManager.ClickNextPageResultsButton();
            Thread.sleep(1000);
        
        // Move back to the 2nd page of results
        System.out.println("Moving back to the 2nd page of results");
        for (int i = 5; i > 2; i--)
            bsmManager.ClickPreviousPageResultsButton();
            Thread.sleep(1000);
            
        // Retrieve the current results' page
        System.out.println("Retrieving the current results' page");
        int currentPage = bsmManager.GetCurrentResultsPage();
        
        int expectedPage = 2;    
        Asserting.DoAssertEquals(currentPage, expectedPage);
    }
    
    @Test
    public void TestTotalPageCount() throws InterruptedException {
        JustSearch("Testing Total Page Count", "שלום");
           
        // Retrieve the total amount of pages
        System.out.println("Retrieving the total amount of pages");
        int retrievedPages = bsmManager.GetTotalPages();
        
        int expectedPages = 24;    
        Asserting.DoAssertEquals(retrievedPages, expectedPages);
    }
    
    /*
     * Extra options for each verse in a result returned
     */
    @Test
    public void TestSourceIsCorrect() throws InterruptedException {
        JustSearch("Testing Source Listed In Extra Options", "אלהי מסכה");
        
        // Clicking extra options button ('...')
        System.out.println("Clicking extra options button ('...')");
        bsmManager.ClickExtraOptionsForPasuk(1, 1);
        
        // Clicking the down arrow ('v') to open the dropdown to show the source
        System.out.println("Clicking the down arrow ('v')");
        bsmManager.ClickSourceDropdown();
        
        // Getting the pasuk's source
        System.out.println("Getting the pasuk's source");
        String resultSource = bsmManager.GetSourceOfPasukFromDropdown();
        
        String expectedSource = "שמות לד, יז";
        Asserting.DoAssertEquals(resultSource, expectedSource);
    }
    
    @Test
    public void TestMorphologyBreakdown() throws InterruptedException {
        JustSearch("Testing Morphology Breakdown", "אלהי מסכה");
        
        // Clicking extra options button ('...')
        System.out.println("Clicking extra options button ('...')");
        bsmManager.ClickExtraOptionsForPasuk(1, 1);
        
        // Clicking the morphology breakdown button
        System.out.println("Clicking the morphology breakdown button");
        bsmManager.ClickMorphologyBreakdownOfResult();
        
        // Retrieve every breakdown row, and put them into 2 separate lists
        System.out.println("Retrieving breakdown row data");
        List<String> retrievedPasukBreakdowns = bsmManager.GetListOfBreakdownsBySide(true);
        List<String> retrievedMorphologyBreakdowns = bsmManager.GetListOfBreakdownsBySide(false);
        
        // Comparing breakdown row data retreived to expected data
        System.out.println("Comparing breakdown row data retreived to expected data");
        List<String> pasukHebBreakdowns = new LinkedList<String>(Arrays.asList("אֱלֹהֵ֥י֙", "מַסֵּכָ֖ה֙", "לֹ֥א֙", "תַעֲשֶׂה־֙", "לָּֽךְ׃֙"));
        List<String> morphologyBreakdowns = new LinkedList<String>(Arrays.asList("mas, plural, noun, const", "fem, sing, noun, abs", "neg", 
            "paal, mas, sing, per1, verb, fut", "prep"));
        
        // Retain only common elements.
        pasukHebBreakdowns.retainAll(retrievedPasukBreakdowns);
        morphologyBreakdowns.retainAll(retrievedMorphologyBreakdowns);
        
        int totalCount = pasukHebBreakdowns.size() + morphologyBreakdowns.size();
        int expectedCount = 10;
        Asserting.DoAssertEquals(totalCount, expectedCount);
    }
    
    @Test
    public void TestMeaningsBreakdown() throws InterruptedException {
        JustSearch("Testing Meanings Breakdown", "אלהי מסכה");
        
        // Clicking extra options button ('...')
        System.out.println("Clicking extra options button ('...')");
        bsmManager.ClickExtraOptionsForPasuk(1, 1);
        
        // Clicking the morphology breakdown button
        System.out.println("Clicking the meaings breakdown button");
        bsmManager.ClickMeaningsBreakdownOfResult();
        
        // Retrieve every breakdown row, and put them into 2 separate lists
        System.out.println("Retrieving breakdown row data");
        List<String> retrievedPasukBreakdowns = bsmManager.GetListOfBreakdownsBySide(true);
        List<String> retrievedMeaningsBreakdowns = bsmManager.GetListOfBreakdownsBySide(false);
        
        // Comparing breakdown row data retreived to expected data
        System.out.println("Comparing breakdown row data retreived to expected data");
        List<String> pasukHebBreakdowns = new LinkedList<String>(Arrays.asList("אֱלֹהֵ֥י֙", "מַסֵּכָ֖ה֙", "לֹ֥א֙", "תַעֲשֶׂה־֙", "לָּֽךְ׃֙"));
        List<String> meaningsBreakdowns = new LinkedList<String>(Arrays.asList("אֱלֹהִים (Noun) god(s)", "מַסֵּכָה (Noun) molten image", "לֹא (Negative) not", "עשׂה (Verb) make", "לְ (Preposition) to"));
        
        // Retain only common elements.
        pasukHebBreakdowns.retainAll(retrievedPasukBreakdowns);
        meaningsBreakdowns.retainAll(retrievedMeaningsBreakdowns);
        
        int totalCount = pasukHebBreakdowns.size() + meaningsBreakdowns.size();
        int expectedCount = 10;
        Asserting.DoAssertEquals(totalCount, expectedCount);
    }
    
    /*
     * Other
     */
    @Test
    public void TestToSearchPageLink() throws InterruptedException {
        JustSearch("Testing Link To Search Page", "אלהי מסכה");
        
        // Clicking link to search page
        System.out.println("Clicking link to search page");
        bsmManager.ClickToSearchPageLink();
        Thread.sleep(3000); // Sleep and wait until page is loaded
        bsmManager.GetBrowser().UpdateCurrentPage();
        
        String currentUrl = bsmManager.GetBrowser().GetFocusedPage().GetUrl();
        Asserting.DoAssertEquals(currentUrl, testUrl);
    }
    
    @Test
    public void TestSefariaLinkOfSource() throws InterruptedException {
        JustSearch("Testing Sefaria Link To Source Of 1st Result", "אלהי מסכה");
        
        // Clicking source of first result
        System.out.println("Clicking source of first result");
        bsmManager.GetASourceOfResultFromCurrentPage(1).click();
        Thread.sleep(1000);
        
        // Needed to make the test work with firefox
        if (browserType.equals("firefox"))
            Thread.sleep(3000);
            
        // Clicking the link to the sefaria page        
        System.out.println("Clicking the link to the appropriate sefaria page");
        bsmManager.ClickLinkToSefariaSource();
        Thread.sleep((browserType.equals("firefox")) ? 15000 : 3000); // Needed to make the test work with firefox
        
        // Needed to make the test work with firefox
        if (browserType.equals("firefox")) {
            String titleToSwitchFrom = "חיפוש בתנ\"ך - חינמי מבית דיקטה";
            
            for (String handle : bsmManager.GetBrowser().GetDriver().getWindowHandles()) {
                bsmManager.GetBrowser().GetDriver().switchTo().window(handle);
            
                if (bsmManager.GetBrowser().GetDriver().getTitle().equals(titleToSwitchFrom))
                    continue;
                else
                    break;
            }
        } else if (browserType.equals("chrome"))
            bsmManager.GetBrowser().SwitchToPage("שמות ל״ד");
        
        bsmManager.GetBrowser().UpdateCurrentPage();
        String currentUrl = bsmManager.GetBrowser().GetFocusedPage().GetUrl();
        
        if (browserType.equals("chrome"))
            bsmManager.GetBrowser().ClosePage(bsmManager.GetBrowser().GetFocusedPage().GetTitle());
        
        String expectedUrl = "https://www.sefaria.org.il/Exodus.34?lang=he&aliyot=0";
        Asserting.DoAssertEquals(currentUrl, expectedUrl);
    }
    
    @Test
    public void TestReturnToTopOfPage() throws InterruptedException {
        JustSearch("Testing the 'Return to Top of Page' function", "אלהי מסכה");
        
        // Scroll to the bottom of the page
        System.out.println("Scrolling to the bottom of the page");
        bsmManager.ScrollToBottom();
        Thread.sleep(1000);
        
        // Clicking the 'to top of page' button
        System.out.println("Clicking the 'back to top' element");
        bsmManager.ClickBackToTop();
        Thread.sleep(1000);
        
        // Retrieving the page's y offset
        JavascriptExecutor je = (JavascriptExecutor)bsmManager.GetBrowser().GetDriver();
        Long offSetValue = (Long)je.executeScript("return window.pageYOffset;");
        
        Long expectedValue = 0L;
        Asserting.DoAssertEquals(offSetValue, expectedValue);
    }
}
