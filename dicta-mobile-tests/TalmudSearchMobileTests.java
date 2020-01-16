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

public class TalmudSearchMobileTests
{
    private static String browserType; // Which browser type is being tested with
    private static String testUrl; // The base url to start a test from
    private static String langToUse; // Which langauge to perform the test with (currently only 'heb' or 'eng')
    private static TSM_Manager tsmManager; // The class which manages everything related to Talmud Search stuff
    private static int testCount; // Which # test we are currently on
    
    // Whether or not we are checking a downloaded file's extension, or that the data it contains is correct
    enum DownloadActionType {
        CHK_EXT,
        CHK_FILE
    }
    
    public TalmudSearchMobileTests() {
    }
    
    // Gets the date and time of the local machine
    public static String GetSystemDateTime() {
        SimpleDateFormat formatter= new SimpleDateFormat("dd-MM-yyyy HH:mm:ss z");
        Date date = new Date(System.currentTimeMillis());
        return formatter.format(date);
    }
    
    @BeforeClass
    public static void Setup() {
        System.out.println("Talmud Search Mobile Tests\n--------------------------");
        
        testUrl = "https://use-dicta-components-2--tender-hamilton-5d028e.netlify.com/";
        
        browserType = "chrome";
        langToUse = "eng";
        
        // Setup the webdriver and load webelement data
        tsmManager = new TSM_Manager();
        tsmManager.Setup(browserType, langToUse);
        
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
        tsmManager.GetBrowser().Quit();
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
        
        // Switch to talmud search page
        tsmManager.GetBrowser().GoTo(testUrl);
        Thread.sleep(1000);
        tsmManager.SwitchToLanguage(langToUse); // Make sure that the appropriate language page is being used
        
        // Enter text into the searchbar
        System.out.println(String.format("Filling searchbar with sample text ('%s')", searchText));
        tsmManager.FillInitialSearchBar(searchText);
        
        // Click search button
        System.out.println("Clicking search button");
        tsmManager.ClickInitialSearchButton();
        Thread.sleep(3500); // Sleep as you get taken to a new page
        tsmManager.GetBrowser().UpdateCurrentPage();
    }
    
    // To avoid repeat code - enters search text, searches, and then opens the sidebar
    // (String) testIntro: the intro to the test printed in the console
    // (String) searchText: the text to search for in the searchbar
    public void SearchAndOpenSidebar(String testIntro, String searchText) throws InterruptedException {
        JustSearch(testIntro, searchText);
        
        // Click sidebar button
        System.out.println("Clicking the sidebar button");
        tsmManager.ClickSidebarButton();
        
        if (browserType.equals("firefox"))
            Thread.sleep(1000);
    }
    
    // To avoid repeat code - enters search text, searches, opens the sidebar, and downloads the search results to a file
    public void SearchAndDownloadFile(String testIntro, TSM_Manager.FileType fileType, String searchText, 
        boolean noHolyNames) throws InterruptedException {
        SearchAndOpenSidebar(testIntro, searchText);
        
        // Clicking the download button
        System.out.println("Clicking the 'download as file' button");
        tsmManager.ClickDownloadAsFileButton();
        
        // Clicking the html radio option
        System.out.println(String.format("Clicking the '%s' radio option", fileType.name()));
        tsmManager.ClickFileTypeOption(fileType);
        
        // Select the 'download without shemot kedoshim' option
        if (noHolyNames) {
            System.out.println("Selecting the 'Do not include the שמות קדושים' option");
            tsmManager.ClickNoHolyNamesCheckbox();
        }
        
        // Clicking the download button
        System.out.println("Clicking the download button");
        tsmManager.ClickDownloadButton();
        Thread.sleep(3000); // Wait until file is downloaded
    }
    
    // To avoid repeat code - clicks the back button of an open submenu, and applies the option(s) selected therein to the results
    public void MoveBackAndApplyOptions() throws InterruptedException {
        // Click back button
        System.out.println("Clicking the back button");
        tsmManager.ClickBackButton();
        
        // Apply sidebar options
        System.out.println("Applying sidebar options");
        tsmManager.ApplySidebarOptions();
        Thread.sleep((browserType.equals("firefox")) ? 1000 : 500); // Sleep to ensure new results are loaded
    }
    
    public String BrowseDownloadsAndCheckFile(DownloadActionType daType, TSM_Manager.FileType fileType,
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
                        valueToReturn = Integer.toString(tsmManager.VerifyDownloadedFileLines(fileType, 
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
        JustSearch("Testing Basic Talmud Search", "כבד את אביך ואת אמך");
        
        // Get list of results
        System.out.println("Retrieving search results on page");
        int resultsCount = tsmManager.GetResultsOnPageFromSearch().size();
        int expectedCount = 7;
        
        Asserting.DoAssertEquals(resultsCount, expectedCount);
    }
    
    @Test
    public void TestSearchAfterSearch() throws InterruptedException {
        JustSearch("Testing Search After Search Performed", "כבד את אביך ואת אמך");
        
        // Putting new search text into the searchbar
        String newSearchText = "כאשר צוה ה'";
        System.out.println(String.format("Putting new search text into the searchbar ('%s')", newSearchText));
        tsmManager.FillSearchbar(newSearchText);
        
        // Clicking search button
        System.out.println("Clicking search button");
        tsmManager.ClickSearchButton();
        Thread.sleep(3000);
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = tsmManager.GetTotalResults();
        int expectedResults = 35;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestAlternateSpellingSearch() throws InterruptedException {
        JustSearch("Testing Alternate Spelling Search", "אימתי קוראין ואת השמע");
        
        // Get list of results
        System.out.println("Retrieving search results");
        int resultsCount = tsmManager.GetResultsOnPageFromSearch().size();
        int expectedCount = 4;
        
        Asserting.DoAssertEquals(resultsCount, expectedCount);
    }
    
    /*
     * Tests for sorting by relevance/talmud order
     */
    
    @Test
    public void TestSortByRelevance() throws InterruptedException {
        JustSearch("Testing Sort By Relevance", "מאימתי קורין את שמע");
        
        // Get first result
        System.out.println("Retrieving first search result's text");
        String firstResult = tsmManager.GetFullTextOfResult(1);
        
        String expectedResult = "מֵאֵימָתַי קוֹרִין אֶת שְׁמַע בְּשַׁחֲרִית מִשֶּׁיַּכִּיר בֵּין תְּכֵלֶת לְלָבָן רַבִּי אֱלִיעֶזֶר אוֹמֵר בֵּין תְּכֵלֶת לְכָרָתֵי וְגוֹמְרָהּ עַד הָנֵץ הַחַמָּה רַבִּי יְהוֹשֻׁעַ אוֹמֵר עַד שָׁלֹשׁ שָׁעוֹת שֶׁכֵּן דֶּרֶךְ";
        expectedResult += " " + "מְלָכִים לַעֲמוֹד בְּשָׁלֹשׁ שָׁעוֹת הַקּוֹרֵא מִכָּאן וְאֵילָךְ לָא הִפְסִיד כְּאָדָם הַקּוֹרֵא בְּתוֹרָהּ:";
        Asserting.DoAssertEquals(firstResult, expectedResult);
    }
    
    @Test
    public void TestSortByTalmudOrder() throws InterruptedException {
        SearchAndOpenSidebar("Testing Sort By Talmud Order", "כבד את אביך ואת אמך");
        
        // Click sort by button
        System.out.println("Clicking the sort-by button");
        tsmManager.ClickSortByButton();
        
        // Click the 'tanach order' radio button
        System.out.println("Clicking the 'Talmud Order' radio button");
        tsmManager.ClickSortByTalmudOrder();
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Get list of results
        System.out.println("Retrieving search results");
        List<WebElement> resultsList = tsmManager.GetResultsOnPageFromSearch();
        
        // Add results sources' texts to a list
        List<String> sourcesList = new ArrayList();
        for (int i = 0; i < resultsList.size(); i++) {
            String sourceText = tsmManager.GetASourceOfResultFromCurrentPage(i + 1).getText();
            sourcesList.add(sourceText);
        }
        
        List<String> expectedList = new LinkedList<String>(Arrays.asList(
            "S", "בבלי ומשנה / סדר מועד / מסכת שבת / פרק ט (אמר רבי עקיבא) / משנה מדף פו,א עד דף פט,ב / סוגיא המתחילה בדף פח,ב",
            "S", "בבלי ומשנה / סדר נשים / מסכת כתובות / פרק יב (הנושא) / משנה מדף קג,א עד דף קד,א / סוגיא בדף קג,א",
            "S", "בבלי ומשנה / סדר נשים / מסכת קידושין / פרק א (האשה נקנית) / משנה מדף כט,א עד דף לב,א / סוגיא בדף ל,ב",
            "S", "בבלי ומשנה / סדר נשים / מסכת קידושין / פרק א (האשה נקנית) / משנה מדף כט,א עד דף לב,א / סוגיא בדף לא,א",
            "S", "בבלי ומשנה / סדר נשים / מסכת קידושין / פרק א (האשה נקנית) / משנה מדף כט,א עד דף לב,א / סוגיא בדף לב,א",
            "S", "בבלי ומשנה / סדר נזיקין / מסכת בבא מציעא / פרק ב (אלו מציאות) / משנה מדף לב,א עד דף לג,א / סוגיא בדף לב,א"
            ));
        
        for (int i = 0; i < expectedList.size(); i++)
            expectedList.remove("S");
        
        Asserting.DoAssertEquals(sourcesList, expectedList);
    }
    
    /*
     * Tests for deselection of shas sedarim
     */
    
    @Test
    public void TestDeselectSederZeraim() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting of Seder Zeraim Results", "\"" + "אמר רב משום" + "\"");
        
        // Click filter by book button
        System.out.println("Clicking the 'Books' button under the 'Filter' heading");
        tsmManager.ClickFilterByBooksButton();
        
        // Click appropriate filter book dropdown
        System.out.println("Clicking Seder Zeraim dropdown");
        tsmManager.ClickFilterBookDropdown(TSM_Manager.FilterBook.ZERAIM);
        
        // Deselect seder zeraim options
        System.out.println("Deselecting Seder Zeraim books");
        tsmManager.ClickFilterBookFromOpenDropdown(1);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = tsmManager.GetTotalResults();
        int expectedResults = 33;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestDeselectSederMoed() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting of Seder Moed Results", "\"" + "אמר רב משום" + "\"");
        
        // Click filter by book button
        System.out.println("Clicking the 'Books' button under the 'Filter' heading");
        tsmManager.ClickFilterByBooksButton();
        
        // Click appropriate filter book dropdown
        System.out.println("Clicking Seder Moed dropdown");
        tsmManager.ClickFilterBookDropdown(TSM_Manager.FilterBook.MOED);
        
        // Deselect seder moed options
        System.out.println("Deselecting Seder Moed books");
        tsmManager.ClickFilterBookFromOpenDropdown(1);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        Thread.sleep(2000);
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = tsmManager.GetTotalResults();
        int expectedResults = 18;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestDeselectSederNashim() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting of Seder Nashim Results", "\"" + "אמר רב משום" + "\"");
        
        // Click filter by book button
        System.out.println("Clicking the 'Books' button under the 'Filter' heading");
        tsmManager.ClickFilterByBooksButton();
        
        // Click appropriate filter book dropdown
        System.out.println("Clicking Seder Nashim dropdown");
        tsmManager.ClickFilterBookDropdown(TSM_Manager.FilterBook.NASHIM);
        
        // Deselect seder nashim options
        System.out.println("Deselecting Seder Nashim books");
        tsmManager.ClickFilterBookFromOpenDropdown(1);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        Thread.sleep(2000);
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = tsmManager.GetTotalResults();
        int expectedResults = 29;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestDeselectSederNezikin() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting of Seder Nezikin Results", "\"" + "אמר רב משום" + "\"");
        
        // Click filter by book button
        System.out.println("Clicking the 'Books' button under the 'Filter' heading");
        tsmManager.ClickFilterByBooksButton();
        
        // Click appropriate filter book dropdown
        System.out.println("Clicking Seder Nezikin dropdown");
        tsmManager.ClickFilterBookDropdown(TSM_Manager.FilterBook.NEZIKIN);
        
        // Deselect seder nezikin options
        System.out.println("Deselecting Seder Nezikin books");
        tsmManager.ClickFilterBookFromOpenDropdown(1);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = tsmManager.GetTotalResults();
        int expectedResults = 29;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestDeselectSederKadshim() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting of Seder Kadshim Results", "\"" + "אמר רב משום" + "\"");
        
        // Click filter by book button
        System.out.println("Clicking the 'Books' button under the 'Filter' heading");
        tsmManager.ClickFilterByBooksButton();
        
        // Click appropriate filter book dropdown
        System.out.println("Clicking Seder Kadshim dropdown");
        tsmManager.ClickFilterBookDropdown(TSM_Manager.FilterBook.KADSHIM);
        
        // Deselect seder kadshim options
        System.out.println("Deselecting Seder Kadshim books");
        tsmManager.ClickFilterBookFromOpenDropdown(1);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        Thread.sleep(2000);
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = tsmManager.GetTotalResults();
        int expectedResults = 32;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestDeselectSederTaharot() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting of Seder Taharot Results", "\"" + "אמר רב משום" + "\"");
        
        // Click filter by book button
        System.out.println("Clicking the 'Books' button under the 'Filter' heading");
        tsmManager.ClickFilterByBooksButton();
        
        // Click appropriate filter book dropdown
        System.out.println("Clicking Seder Taharot dropdown");
        Thread.sleep(10000); // Needed for when running tests in a suite
        tsmManager.ClickFilterBookDropdown(TSM_Manager.FilterBook.TAHAROT);
        
        // Deselect seder taharot options
        System.out.println("Deselecting Seder Taharot books");
        tsmManager.ClickFilterBookFromOpenDropdown(1);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = tsmManager.GetTotalResults();
        int expectedResults = 34;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestReselectAllAfterDeselectAllShas() throws InterruptedException {
        SearchAndOpenSidebar("Testing Results of Reselection of All Shas After Deselection", "\"" + "אמר רב משום" + "\"");
        
        // DESELECTING
        // Click filter by book button
        System.out.println("Clicking the 'Books' button under the 'Filter' heading");
        tsmManager.ClickFilterByBooksButton();
        
        // Deselect all sedarim
        System.out.println("Deselecting all sedarim");
        tsmManager.DeReselectAllFilterBooks(true);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // RESELECTING
        // Click sidebar button - again
        System.out.println("Clicking the sidebar button - again");
        tsmManager.ClickSidebarButton();
        
        // Click filter by book button - again
        System.out.println("Clicking the 'Books' button under the 'Filter' heading - again");
        tsmManager.ClickFilterByBooksButton();
        
        // Reselect all sedarim
        System.out.println("Reselecting all sedarim");
        tsmManager.DeReselectAllFilterBooks(false);
        Thread.sleep(1000); // Sleep to wait for the books to be reselected
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = tsmManager.GetTotalResults();
        int expectedResults = 35;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    /*
     * Tests for deselection of wordforms
     */
    
    @Test
    public void TestDeselectSingleWordform() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting A Single Wordform", "קורין את");

        // Click wordforms button
        System.out.println("Clicking the wordforms button");
        tsmManager.ClickFilterByWordformsButton();
        
        // Click a wordform dropdown
        System.out.println("Clicking the first wordform dropdown");
        tsmManager.ClickWordformDropdown(1);
        
        // Deselect a single wordform
        System.out.println("Deselecting 1st wordform");
        tsmManager.ClickWordformFromOpenDropdown(2);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        Thread.sleep(3000);
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = tsmManager.GetTotalResults();
        int expectedResults = 35;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestDeselectEntireWordform() throws InterruptedException {
        SearchAndOpenSidebar("Testing Deselecting An Entire Wordform", "קורין את");

        // Click wordforms button
        System.out.println("Clicking the wordforms button");
        tsmManager.ClickFilterByWordformsButton();
        
        // Click a wordform dropdown
        System.out.println("Clicking the first wordform dropdown");
        tsmManager.ClickWordformDropdown(1);
        
        // Deselect an entire wordform
        System.out.println("Deselecting entire wordform");
        tsmManager.ClickWordformFromOpenDropdown(1); // 1 is 'select all'
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = tsmManager.GetTotalResults();
        int expectedResults = 0;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    @Test
    public void TestReselectAfterDeselectEntireWordform() throws InterruptedException {
        SearchAndOpenSidebar("Testing Results of Reselection of A Wordform After Deselection", "קורין את");
        
        // DESELECTING
        // Click wordforms button
        System.out.println("Clicking the wordforms button");
        tsmManager.ClickFilterByWordformsButton();
        
        // Click a wordform dropdown
        System.out.println("Clicking the first wordform dropdown");
        tsmManager.ClickWordformDropdown(1);
        
        // Deselect an entire wordform
        System.out.println("Deselecting entire wordform");
        tsmManager.ClickWordformFromOpenDropdown(1); // 1 is 'select all'
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // RESELECTING
        // Click sidebar button - again
        System.out.println("Clicking the sidebar button - again");
        tsmManager.ClickSidebarButton();
        
        // Click wordforms button - again
        System.out.println("Clicking the wordforms button - again");
        tsmManager.ClickFilterByWordformsButton();
        
        // Click a wordform dropdown - again
        //System.out.println("Clicking the first wordform dropdown - again");
        //tsmManager.ClickWordformDropdown(1);
        
        // Reselect an entire wordform
        System.out.println("Reselecting entire wordform");
        tsmManager.ClickWordformFromOpenDropdown(1); // 1 is 'select all'
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving total results");
        int resultsTotal = tsmManager.GetTotalResults();
        int expectedResults = 156;
        
        Asserting.DoAssertEquals(resultsTotal, expectedResults);
    }
    
    
    /*
     * Tests for display options
     */
    @Test
    public void TestDisplayNoNikud() throws InterruptedException {
        SearchAndOpenSidebar("Testing No Nikud Display Option", "שער אשה ערוה");
        
        // Click the display button
        System.out.println("Clicking the display button");
        tsmManager.ClickDisplayButton();
        
        // Click the 'no nikud' style option
        System.out.println("Clicking the 'no nikud' style option");
        tsmManager.ClickNikudOption(TSM_Manager.NikudStyle.NO);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving first search result's text");
        String firstResult = tsmManager.GetFullTextOfResult(1);
        String expectedText = "אמר ר' יצחק טפח באשה ערוה למאי אילימא לאסתכולי בה והא אמר רב ששת למה מנה הכתוב תכשיטין";
        expectedText += " " + "שבחוץ עם תכשיטין שבפנים לומר לך כל המסתכל באצבע קטנה של אשה כאילו מסתכל במקום התורף";
        expectedText += " " + "אלא באשתו ולקריאת שמע אמר רב חסדא שוק באשה ערוה שנאמר (ישעיהו מז ב) גלי שוק עברי נהרות";
        expectedText += " " + "וכתיב (ישעיהו מז ג) תגל ערותך וגם תראה חרפתך אמר שמואל קול באשה ערוה שנאמר (שיר השירים ב יד)";
        expectedText += " " + "כי קולך ערב ומראך נאוה אמר רב ששת שער באשה ערוה שנאמר (שיר השירים ד א) שערך כעדר העזים:";
        Asserting.DoAssertEquals(firstResult, expectedText);
    }
    
    @Test
    public void TestDisplayJustNikud() throws InterruptedException {
        SearchAndOpenSidebar("Testing Just Nikud Display Option", "שער אשה ערוה");
        
        // Click the display button
        System.out.println("Clicking the display button");
        tsmManager.ClickDisplayButton();
        
        // Click the 'just nikud' style option
        System.out.println("Clicking the 'just nikud' style option");
        tsmManager.ClickNikudOption(TSM_Manager.NikudStyle.YES);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve results number
        System.out.println("Retrieving first search result's text");
        String firstResult = tsmManager.GetFullTextOfResult(1);
        String expectedText = "אָמַר ר' יִצְחָק טֶפַח בָּאִשָּׁה עֶרְוָה לְמַאי אִילֵּימָא לְאִסְתַּכּוֹלֵי בַּהּ וְהָא אָמַר רַב שֵׁשֶׁת לָמָּה מָנָה הַכָּתוּב תַּכְשִׁיטִין";
        expectedText += " " + "שֶׁבַּחוּץ עִם תַּכְשִׁיטִין שֶׁבִּפְנִים לוֹמַר לָךְ כׇּל הַמִּסְתַּכֵּל בְּאֶצְבַּע קְטַנָּה שֶׁל אִשָּׁה כְּאִילּוּ מִסְתַּכֵּל בִּמְקוֹם הַתּוֹרֶף";
        expectedText += " " + "אֶלָּא בְּאִשְׁתּוֹ וְלִקְרִיאַת שְׁמַע אָמַר רַב חִסְדָּא שׁוֹק בָּאִשָּׁה עֶרְוָה שֶׁנֶּאֱמַר (ישעיהו מז ב) גַּלִּי שׁוֹק עִבְרִי נְהָרוֹת";
        expectedText += " " + "וּכְתִיב (ישעיהו מז ג) תָּגֵל עֵרוּתְךָ וְגַם תִּרְאֶה חֶרְפָּתְךָ אָמַר שְׁמוּאֵל קוֹל בָּאִשָּׁה עֶרְוָה שֶׁנֶּאֱמַר (שיר השירים ב יד)";
        expectedText += " " + "כִּי קֻולָּךְ עֶרֶב וּמַרְאֵךְ נָאוָה אָמַר רַב שֵׁשֶׁת שֵׂעָר בָּאִשָּׁה עֶרְוָה שֶׁנֶּאֱמַר (שיר השירים ד א) שַׂעֲרֵךְ כְּעֵדֶר הָעִזִּים:";
        
        Asserting.DoAssertEquals(firstResult, expectedText);
    }
    
    @Test
    public void TestIncreaseFontSize() throws InterruptedException {
        SearchAndOpenSidebar("Testing Increasing Font Size", "כאשר צוה ה'");
        
        // Click the display button
        System.out.println("Clicking the display button");
        tsmManager.ClickDisplayButton();
        
        // Click the 'increase font size' button 5 times
        System.out.println("Clicking the 'increase font size' button 5 times");
        for (int i = 0; i < 5; i++)
            tsmManager.ClickIncreaseFontSizeButton();
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve font size
        System.out.println("Retrieving font size of first search result");
        String fontSize = tsmManager.GetFontSizeOfResult(1);
        String expectedSize = "30px";
        
        Asserting.DoAssertEquals(fontSize, expectedSize);
    }
    
    @Test
    public void TestDecreaseFontSize() throws InterruptedException {
        SearchAndOpenSidebar("Testing Decreasing Font Size", "כאשר צוה ה'");
        
        // Click the display button
        System.out.println("Clicking the display button");
        tsmManager.ClickDisplayButton();
        
        // Click the 'decrease font size' button 4 times (min font size is 12 pixels)
        System.out.println("Clicking the 'decrease font size' button 4 times");
        for (int i = 0; i < 4; i++)
            tsmManager.ClickDecreaseFontSizeButton();
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Retrieve font size
        System.out.println("Retrieving font size of first search result");
        String fontSize = tsmManager.GetFontSizeOfResult(1);
        String expectedSize = "12px";
        
        Asserting.DoAssertEquals(fontSize, expectedSize);
    }
    
    @Test
    public void TestShow10ResultsOnPage() throws InterruptedException {
        SearchAndOpenSidebar("Testing Showing 10 Results On Page", "שלום");
        
        // Click the display button
        System.out.println("Clicking the display button");
        tsmManager.ClickDisplayButton();
        
        // Click the '10 results' option
        System.out.println("Clicking the '10 results per page' option");
        tsmManager.ClickResultsPerPageButton(TSM_Manager.ResultsPerPage.TEN);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        
        // Get the number of results on the page
        int resultsOnPage = tsmManager.GetResultsOnPageFromSearch().size();
        int expectedCount = 10;
        
        Asserting.DoAssertEquals(resultsOnPage, expectedCount);
    }
    
    @Test
    public void TestShow50ResultsOnPage() throws InterruptedException {
        SearchAndOpenSidebar("Testing Showing 50 Results On Page", "כאשר צוה");
        
        // Click the display button
        System.out.println("Clicking the display button");
        tsmManager.ClickDisplayButton();
        
        // Click the '50 results' option
        System.out.println("Clicking the '50 results per page' option");
        tsmManager.ClickResultsPerPageButton(TSM_Manager.ResultsPerPage.FIFTY);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        Thread.sleep(3000);
        
        // Get the number of results on the page
        int resultsOnPage = tsmManager.GetResultsOnPageFromSearch().size();
        int expectedCount = 50;
        
        Asserting.DoAssertEquals(resultsOnPage, expectedCount);
    }
    
    @Test
    public void TestShow100ResultsOnPage() throws InterruptedException {
        SearchAndOpenSidebar("Testing Showing 100 Results On Page", "כאשר צוה");
        
        // Click the display button
        System.out.println("Clicking the display button");
        tsmManager.ClickDisplayButton();
        
        // Click the '100 results' option
        System.out.println("Clicking the '100 results per page' option");
        tsmManager.ClickResultsPerPageButton(TSM_Manager.ResultsPerPage.HUNDRED);
        
        // Click the back button, and apply the option(s) selected
        MoveBackAndApplyOptions();
        Thread.sleep(3000);
        
        // Get the number of results on the page
        int resultsOnPage = tsmManager.GetResultsOnPageFromSearch().size();
        int expectedCount = 100;
        
        Asserting.DoAssertEquals(resultsOnPage, expectedCount);
    }
    
    /*
     * Downloads
     */
    @Test
    public void TestDownloadResultsAsHtml() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloading Search Results As An Html File", TSM_Manager.FileType.HTML, "\"" + "אמר רבי יהודה וכו'" + "\"", false);
        
        // Get filename of file downloaded
        System.out.println("Downloaded file; retrieving filename");
        String downloadedFilename = BrowseDownloadsAndCheckFile(DownloadActionType.CHK_EXT, TSM_Manager.FileType.HTML, null);
        
        String expectedFilename = "searchResults.html";
        Asserting.DoAssertEquals(downloadedFilename, expectedFilename);
    }
    
    @Test
    public void TestDownloadedHtmlFileIsCorrect() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing The Downloaded Html File Is Correct", TSM_Manager.FileType.HTML, "\"" + "אמר רבי יהודה וכו'" + "\"", false);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList(
            "S", "בבלי ומשנה / סדר מועד / מסכת עירובין / פרק ג (בכל מערבין) / משנה מדף לט,א עד דף מא,ב / סוגיא בדף לט,ב",
            "S", "ועוד אמר רבי יהודה וכו':",
            "S", "וצריכא דאי אשמעינן ראש השנה בהא קאמר רבי יהודה משום דלא קעביד מידי אבל כלכלה דמיחזי כמתקן טיבלא אימא מודה להו לרבנן",
            "S", "ואי אשמעינן הני תרתי משום דליכא למיגזר עלייהו אבל ביצה דאיכא למיגזר בה משום פירות הנושרין ומשום משקין שזבו אימא מודה להו לרבנן צריכא:",
            "S", "בבלי ומשנה / סדר קדשים / מסכת חולין / פרק ה (אותו ואת בנו) / משנה בדף פג,א / סוגיא בדף פג,א",
            "S", "תנא אם לא הודיעו הולך ושוחט ואינו נמנע:",
            "S", "אמר רבי יהודה אימתי למה לי למיתני את האם לחתן ואת הבת לכלה מלתא אגב אורחיה קמשמע לן דאורח ארעא למטרח בי חתנא טפי מבי כלתא:",
            "S", "בבלי ומשנה / סדר נזיקין / מסכת סנהדרין / פרק א (דיני ממונות) / משנה מדף ב,א עד דף יד,ב / סוגיא בדף יב,ב",
            "S", "וליטעמיך תיקשי לך היא גופה רבי יהודה אומר מעברין ואמר רבי יהודה מעשה בחזקיה מלך יהודה שעיבר את השנה מפני הטומאה וביקש רחמים על עצמו אלא חסורי מחסרא והכי קתני אין מעברין את השנה מפני הטומאה ואם עיברוה מעוברת רבי יהודה אומר אינה מעוברת ואמר רבי יהודה וכו'",
            "S", "אי הכי רבי שמעון אומר אם מפני הטומאה עיברוה מעוברת היינו תנא קמא אמר רבא לכתחלה איכא בינייהו"));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, TSM_Manager.FileType.HTML, linesToCheckAgainst));    
        
        int expectedLines = 10;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestNoHolyNamesInHtmlFile() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloaded Html File For No Holy Names", TSM_Manager.FileType.HTML, "\"" + "אנכי ה'" +"\"", true);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList(
            "S", "בבלי ומשנה / סדר נשים / מסכת גיטין / פרק ה (הניזקין) / משנה מדף נה,ב עד דף נט,א / סוגיא בדף נז,ב",
            "S", "ורב יהודה אמר זו אשה ושבעה בניה אתיוהו קמא לקמיה דקיסר אמרו ליה פלח לעבודה זרה אמר להו כתוב בתורה (שמות כ ב) אנכי ה' א־היך אפקוהו וקטלוהו",
            "S", "ואתיוהו לאידך לקמיה דקיסר אמרו ליה פלח לעבודה זרה אמר להו כתוב בתורה (שמות כ ב) לא יהיה לך א־הים אחרים על פני אפקוהו וקטלוהו אתיוהו לאידך אמרו ליה פלח לעבודה זרה אמר להו כתוב בתורה (שמות כב יט) זובח לא־הים יחרם אפקוהו וקטלוהו"
            ));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, TSM_Manager.FileType.HTML, linesToCheckAgainst));    
        
        int expectedLines = 3;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestDownloadResultsAsTxt() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloading Search Results As A Txt File", TSM_Manager.FileType.TXT, "\"" + "אמר רבי יהודה וכו'" + "\"", false);
        
        // Get filename of file downloaded
        System.out.println("Downloaded file; retrieving filename");
        String downloadedFilename = BrowseDownloadsAndCheckFile(DownloadActionType.CHK_EXT, TSM_Manager.FileType.TXT, null);;
        
        String expectedFilename = "searchResults.txt";
        Asserting.DoAssertEquals(downloadedFilename, expectedFilename);
    }
    
    @Test
    public void TestDownloadedTxtFileIsCorrect() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing The Downloaded Text File Is Correct", TSM_Manager.FileType.TXT, "\"" + "אמר רבי יהודה וכו'" + "\"", false);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList("Search Results",
            "S", "בבלי ומשנה / סדר מועד / מסכת עירובין / פרק ג (בכל מערבין) / משנה מדף לט,א עד דף מא,ב / סוגיא בדף לט,ב",
            "S", "ועוד *אמר* *רבי* *יהודה* *וכו':*",
            "S", "וצריכא דאי אשמעינן ראש השנה בהא קאמר רבי יהודה משום דלא קעביד מידי אבל כלכלה דמיחזי כמתקן טיבלא אימא מודה להו לרבנן",
            "S", "ואי אשמעינן הני תרתי משום דליכא למיגזר עלייהו אבל ביצה דאיכא למיגזר בה משום פירות הנושרין ומשום משקין שזבו אימא מודה להו לרבנן צריכא:",
            "S", "בבלי ומשנה / סדר קדשים / מסכת חולין / פרק ה (אותו ואת בנו) / משנה בדף פג,א / סוגיא בדף פג,א",
            "S", "תנא אם לא הודיעו הולך ושוחט ואינו נמנע:",
            "S", "*אמר* *רבי* *יהודה* *אימתי* למה לי למיתני את האם לחתן ואת הבת לכלה מלתא אגב אורחיה קמשמע לן דאורח ארעא למטרח בי חתנא טפי מבי כלתא:",
            "S", "בבלי ומשנה / סדר נזיקין / מסכת סנהדרין / פרק א (דיני ממונות) / משנה מדף ב,א עד דף יד,ב / סוגיא בדף יב,ב",
            "S", "וליטעמיך תיקשי לך היא גופה רבי יהודה אומר מעברין ואמר רבי יהודה מעשה בחזקיה מלך יהודה שעיבר את השנה מפני הטומאה וביקש רחמים על עצמו אלא חסורי מחסרא והכי קתני אין מעברין את השנה מפני הטומאה ואם עיברוה מעוברת רבי יהודה אומר אינה מעוברת *ואמר* *רבי* *יהודה* *וכו'*",
            "S", "אי הכי רבי שמעון אומר אם מפני הטומאה עיברוה מעוברת היינו תנא קמא אמר רבא לכתחלה איכא בינייהו"));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, TSM_Manager.FileType.TXT, linesToCheckAgainst));    
        
        int expectedLines = 11;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestNoHolyNamesInTxtFile() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloaded Txt File For No Holy Names", TSM_Manager.FileType.TXT, "\"" + "אנכי ה'" +"\"", true);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList("Search Results",
            "S", "בבלי ומשנה / סדר נשים / מסכת גיטין / פרק ה (הניזקין) / משנה מדף נה,ב עד דף נט,א / סוגיא בדף נז,ב",
            "S", "ורב יהודה אמר זו אשה ושבעה בניה אתיוהו קמא לקמיה דקיסר אמרו ליה פלח לעבודה זרה אמר להו כתוב בתורה (שמות כ ב) *אנכי* *ה'* א־היך אפקוהו וקטלוהו",
            "S", "ואתיוהו לאידך לקמיה דקיסר אמרו ליה פלח לעבודה זרה אמר להו כתוב בתורה (שמות כ ב) לא יהיה לך א־הים אחרים על פני אפקוהו וקטלוהו אתיוהו לאידך אמרו ליה פלח לעבודה זרה אמר להו כתוב בתורה (שמות כב יט) זובח לא־הים יחרם אפקוהו וקטלוהו"
            ));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, TSM_Manager.FileType.TXT, linesToCheckAgainst));    
        
        int expectedLines = 4;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestDownloadResultsAsCsv() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloading Search Results As A Csv File", TSM_Manager.FileType.CSV, "\"" + "אמר רבי יהודה וכו'" + "\"", false);
        
        // Get filename of file downloaded
        System.out.println("Downloaded file; retrieving filename");
        String downloadedFilename = BrowseDownloadsAndCheckFile(DownloadActionType.CHK_EXT, TSM_Manager.FileType.CSV, null);;
        
        String expectedFilename = "searchResults.csv";
        Asserting.DoAssertEquals(downloadedFilename, expectedFilename);
    }
    
    @Test
    public void TestDownloadedCsvFileIsCorrect() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing The Downloaded Csv File Is Correct", TSM_Manager.FileType.CSV, "\"" + "אמר רבי יהודה וכו'" + "\"", false);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList(
            "S", "\"בבלי ומשנה / סדר מועד / מסכת עירובין / פרק ג (בכל מערבין) / משנה מדף לט,א עד דף מא,ב / סוגיא בדף לט,ב\",\"ועוד *אמר* *רבי* *יהודה* *וכו':*",
            "S", "וצריכא דאי אשמעינן ראש השנה בהא קאמר רבי יהודה משום דלא קעביד מידי אבל כלכלה דמיחזי כמתקן טיבלא אימא מודה להו לרבנן",
            "S", "ואי אשמעינן הני תרתי משום דליכא למיגזר עלייהו אבל ביצה דאיכא למיגזר בה משום פירות הנושרין ומשום משקין שזבו אימא מודה להו לרבנן צריכא:\"",
            "S", "\"בבלי ומשנה / סדר קדשים / מסכת חולין / פרק ה (אותו ואת בנו) / משנה בדף פג,א / סוגיא בדף פג,א\",\"תנא אם לא הודיעו הולך ושוחט ואינו נמנע:",
            "S", "*אמר* *רבי* *יהודה* *אימתי* למה לי למיתני את האם לחתן ואת הבת לכלה מלתא אגב אורחיה קמשמע לן דאורח ארעא למטרח בי חתנא טפי מבי כלתא:\"",
            "S", "\"בבלי ומשנה / סדר נזיקין / מסכת סנהדרין / פרק א (דיני ממונות) / משנה מדף ב,א עד דף יד,ב / סוגיא בדף יב,ב\",\"וליטעמיך תיקשי לך היא גופה רבי יהודה אומר מעברין ואמר רבי יהודה מעשה בחזקיה מלך יהודה שעיבר את השנה מפני הטומאה וביקש רחמים על עצמו אלא חסורי מחסרא והכי קתני אין מעברין את השנה מפני הטומאה ואם עיברוה מעוברת רבי יהודה אומר אינה מעוברת *ואמר* *רבי* *יהודה* *וכו'*",
            "S", "אי הכי רבי שמעון אומר אם מפני הטומאה עיברוה מעוברת היינו תנא קמא אמר רבא לכתחלה איכא בינייהו\""));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, TSM_Manager.FileType.CSV, linesToCheckAgainst));    
               
        int expectedLines = 7;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestNoHolyNamesInCsvFile() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloaded Csv File For No Holy Names", TSM_Manager.FileType.CSV, "\"" + "אנכי ה'" +"\"", true);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList(
            "S", "\"בבלי ומשנה / סדר נשים / מסכת גיטין / פרק ה (הניזקין) / משנה מדף נה,ב עד דף נט,א / סוגיא בדף נז,ב\",\"ורב יהודה אמר זו אשה ושבעה בניה אתיוהו קמא לקמיה דקיסר אמרו ליה פלח לעבודה זרה אמר להו כתוב בתורה (שמות כ ב) *אנכי* *ה'* א־היך אפקוהו וקטלוהו",
            "S", "ואתיוהו לאידך לקמיה דקיסר אמרו ליה פלח לעבודה זרה אמר להו כתוב בתורה (שמות כ ב) לא יהיה לך א־הים אחרים על פני אפקוהו וקטלוהו אתיוהו לאידך אמרו ליה פלח לעבודה זרה אמר להו כתוב בתורה (שמות כב יט) זובח לא־הים יחרם אפקוהו וקטלוהו\""
            ));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, TSM_Manager.FileType.CSV, linesToCheckAgainst));    
        
        int expectedLines = 2;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestDownloadResultsAsWord() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloading Search Results As A Word Document", TSM_Manager.FileType.WORD, "\"" + "אמר רבי יהודה וכו'" + "\"", false);
        
        // Get filename of file downloaded
        System.out.println("Downloaded file; retrieving filename");
        String downloadedFilename = BrowseDownloadsAndCheckFile(DownloadActionType.CHK_EXT, TSM_Manager.FileType.WORD, null);
        
        String expectedFilename = "searchResults.docx";
        Asserting.DoAssertEquals(downloadedFilename, expectedFilename);
    }
    
    @Test
    public void TestDownloadedWordFileIsCorrect() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing The Downloaded Word File Is Correct", TSM_Manager.FileType.WORD, "\"" + "אמר רבי יהודה וכו'" + "\"", false);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList(
            "S", "בבלי ומשנה / סדר מועד / מסכת עירובין / פרק ג (בכל מערבין) / משנה מדף לט,א עד דף מא,ב / סוגיא בדף לט,ב",
            "S", "ועוד אמר רבי יהודה וכו':",
            "S", "וצריכא דאי אשמעינן ראש השנה בהא קאמר רבי יהודה משום דלא קעביד מידי אבל כלכלה דמיחזי כמתקן טיבלא אימא מודה להו לרבנן",
            "S", "ואי אשמעינן הני תרתי משום דליכא למיגזר עלייהו אבל ביצה דאיכא למיגזר בה משום פירות הנושרין ומשום משקין שזבו אימא מודה להו לרבנן צריכא:",
            "S", "בבלי ומשנה / סדר קדשים / מסכת חולין / פרק ה (אותו ואת בנו) / משנה בדף פג,א / סוגיא בדף פג,א",
            "S", "תנא אם לא הודיעו הולך ושוחט ואינו נמנע:",
            "S", "אמר רבי יהודה אימתי למה לי למיתני את האם לחתן ואת הבת לכלה מלתא אגב אורחיה קמשמע לן דאורח ארעא למטרח בי חתנא טפי מבי כלתא:",
            "S", "בבלי ומשנה / סדר נזיקין / מסכת סנהדרין / פרק א (דיני ממונות) / משנה מדף ב,א עד דף יד,ב / סוגיא בדף יב,ב",
            "S", "וליטעמיך תיקשי לך היא גופה רבי יהודה אומר מעברין ואמר רבי יהודה מעשה בחזקיה מלך יהודה שעיבר את השנה מפני הטומאה וביקש רחמים על עצמו אלא חסורי מחסרא והכי קתני אין מעברין את השנה מפני הטומאה ואם עיברוה מעוברת רבי יהודה אומר אינה מעוברת ואמר רבי יהודה וכו'",
            "S", "אי הכי רבי שמעון אומר אם מפני הטומאה עיברוה מעוברת היינו תנא קמא אמר רבא לכתחלה איכא בינייהו"));
            
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, TSM_Manager.FileType.WORD, linesToCheckAgainst));

        int expectedLines = 10;
        Asserting.DoAssertEquals(fileLines, expectedLines);
    }
    
    @Test
    public void TestNoHolyNamesInWordFile() throws InterruptedException,AWTException,InvalidFormatException {
        SearchAndDownloadFile("Testing Downloaded Word File For No Holy Names", TSM_Manager.FileType.WORD, "\"" + "אנכי ה'" +"\"", true);
                
        // Get filename of file downloaded
        System.out.println("Downloaded file; loading file into memory and comparing lines");
        int fileLines = 0;
        List<String> linesToCheckAgainst = new LinkedList<String>(Arrays.asList(
            "S", "בבלי ומשנה / סדר נשים / מסכת גיטין / פרק ה (הניזקין) / משנה מדף נה,ב עד דף נט,א / סוגיא בדף נז,ב",
            "S", "ורב יהודה אמר זו אשה ושבעה בניה אתיוהו קמא לקמיה דקיסר אמרו ליה פלח לעבודה זרה אמר להו כתוב בתורה (שמות כ ב) אנכי ה' א־היך אפקוהו וקטלוהו",
            "S", "ואתיוהו לאידך לקמיה דקיסר אמרו ליה פלח לעבודה זרה אמר להו כתוב בתורה (שמות כ ב) לא יהיה לך א־הים אחרים על פני אפקוהו וקטלוהו אתיוהו לאידך אמרו ליה פלח לעבודה זרה אמר להו כתוב בתורה (שמות כב יט) זובח לא־הים יחרם אפקוהו וקטלוהו"
            ));
        
        // Get number of lines that appear in both 'linesToCheckAgainst' and the downloaded file    
        fileLines = Integer.parseInt(BrowseDownloadsAndCheckFile(DownloadActionType.CHK_FILE, TSM_Manager.FileType.WORD, linesToCheckAgainst));    
        
        int expectedLines = 3;
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
            tsmManager.ClickNextPageResultsButton();
            Thread.sleep(1000);
        
        // Retrieve the current results' page
        System.out.println("Retrieving the current results' page");
        int currentPage = tsmManager.GetCurrentResultsPage();
        
        int expectedPage = 5;    
        Asserting.DoAssertEquals(currentPage, expectedPage);
    }
    
    @Test
    public void TestGoToPreviousPageResults() throws InterruptedException {
        JustSearch("Testing Moving To Next Pages Of Results", "שלום");
        
        // Move to the 5th page of results
        System.out.println("Moving to the 5th page of results");
        for (int i = 1; i < 5; i++)
            tsmManager.ClickNextPageResultsButton();
            Thread.sleep(1000);
        
        // Move back to the 2nd page of results
        System.out.println("Moving back to the 2nd page of results");
        for (int i = 5; i > 2; i--)
            tsmManager.ClickPreviousPageResultsButton();
            Thread.sleep(1000);
            
        // Retrieve the current results' page
        System.out.println("Retrieving the current results' page");
        int currentPage = tsmManager.GetCurrentResultsPage();
        
        int expectedPage = 2;    
        Asserting.DoAssertEquals(currentPage, expectedPage);
    }
    
    @Test
    public void TestTotalPageCount() throws InterruptedException {
        JustSearch("Testing Total Page Count", "שלום");
           
        // Retrieve the total amount of pages
        System.out.println("Retrieving the total amount of pages");
        int retrievedPages = tsmManager.GetTotalPages();
        
        int expectedPages = 33;    
        Asserting.DoAssertEquals(retrievedPages, expectedPages);
    }
    
    /*
     * Extra options for each verse in a result returned
     */
    @Test
    public void TestTalmudSourceIsCorrect() throws InterruptedException {
        JustSearch("Testing Talmud Source Is Correct", "כאשר צווה ה'");
        
        // Getting the shas source
        System.out.println("Getting the pasuk's source");
        String resultSource = tsmManager.GetASourceOfResultFromCurrentPage(1).getText();
        
        String expectedSource = "בבלי ומשנה / סדר קדשים / מסכת זבחים / פרק יב (טבול יום) / משנה מדף צח,ב עד דף קב,ב / סוגיא בדף קא,א";
        Asserting.DoAssertEquals(resultSource, expectedSource);
    }
    
    /*
     * Other
     */
    @Test
    public void TestToSearchPageLink() throws InterruptedException {
        JustSearch("Testing Link To Search Page", "שלום");
        
        // Clicking link to search page
        System.out.println("Clicking link to search page");
        tsmManager.ClickToSearchPageLink();
        Thread.sleep(3000); // Sleep and wait until page is loaded
        tsmManager.GetBrowser().UpdateCurrentPage();
        
        String currentUrl = tsmManager.GetBrowser().GetFocusedPage().GetUrl();
        Asserting.DoAssertEquals(currentUrl, testUrl);
    }
    
    @Test
    public void TestSefariaLinkOfSource() throws InterruptedException {
        JustSearch("Testing Sefaria Link To Source Of 1st Result", "כאשר צווה ה'");
        
        // Clicking source of first result
        System.out.println("Clicking source of first result");
        tsmManager.GetASourceOfResultFromCurrentPage(1).click();
        Thread.sleep(1000);
        
        // Needed to make the test work with firefox
        if (browserType.equals("firefox"))
            Thread.sleep(3000);
            
        // Clicking the link to the sefaria page        
        System.out.println("Clicking the link to the appropriate sefaria page");
        tsmManager.ClickLinkToSefariaSource();
        Thread.sleep((browserType.equals("firefox")) ? 15000 : 3000); // Needed to make the test work with firefox
        
        // Needed to make the test work with firefox
        if (browserType.equals("firefox")) {
            String titleToSwitchFrom = "חיפוש בתנ\"ך - חינמי מבית דיקטה";
            
            for (String handle : tsmManager.GetBrowser().GetDriver().getWindowHandles()) {
                tsmManager.GetBrowser().GetDriver().switchTo().window(handle);
            
                if (tsmManager.GetBrowser().GetDriver().getTitle().equals(titleToSwitchFrom))
                    continue;
                else
                    break;
            }
        } else if (browserType.equals("chrome")) {
            Thread.sleep(3000);
            tsmManager.GetBrowser().SwitchToPage("זבחים ק״א א:ב");
        }
        
        tsmManager.GetBrowser().UpdateCurrentPage();
        String currentUrl = tsmManager.GetBrowser().GetFocusedPage().GetUrl();
        
        if (browserType.equals("chrome"))
            tsmManager.GetBrowser().ClosePage(tsmManager.GetBrowser().GetFocusedPage().GetTitle());
        
        String expectedUrl = "https://www.sefaria.org.il/Zevachim.101a.2?lang=he";
        Asserting.DoAssertEquals(currentUrl, expectedUrl);
    }
    
    @Test
    public void TestReturnToTopOfPage() throws InterruptedException {
        JustSearch("Testing the 'Return to Top of Page' function", "שלום");
        
        // Scroll to the bottom of the page
        System.out.println("Scrolling to the bottom of the page");
        tsmManager.ScrollToBottom();
        Thread.sleep(1000);
        
        // Clicking the 'to top of page' button
        System.out.println("Clicking the 'back to top' element");
        tsmManager.ClickBackToTop();
        Thread.sleep(1000);
        
        // Retrieving the page's y offset
        JavascriptExecutor je = (JavascriptExecutor)tsmManager.GetBrowser().GetDriver();
        Long offSetValue = (Long)je.executeScript("return window.pageYOffset;");
        
        Long expectedValue = 0L;
        Asserting.DoAssertEquals(offSetValue, expectedValue);
    }
}
