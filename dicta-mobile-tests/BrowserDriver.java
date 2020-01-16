import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.*;
import java.util.*;

// Class to hold data for loading specific drivers for specific browsers into memory
public class BrowserDriver {
    private String browserName; // Name of the browser whom we wish to work with (and load their WebDriver into memory)
    private WebDriver browserDriver; // Web driver for the specific browser
    private String driverProperty; // For adding to the system path
    private String driverPath; // Path to the web driver executable
    private static String toDriverPath = "C:\\Users\\Yehuda\\Documents\\GitHub\\DictaMobileTests\\Selenium\\drivers\\";
    public static String downloadPath = "C:\\Users\\Yehuda\\Documents\\GitHub\\DictaMobileTests\\downloads\\";

    public BrowserDriver(String browserName) {
        this.browserName = browserName;
        this.driverProperty = SelectDriverProperty(browserName);
        this.driverPath = SelectDriverPath(browserName);
        System.setProperty(this.driverProperty, toDriverPath + this.driverPath);
        this.browserDriver = SelectWebDriver(browserName, downloadPath);
    }
    
    // Returns the appropriate WebDriver class to the appropriate browser
    // (String) browserName: which browser Driver to load
    // (String) downloadPath: where to put downloads from tests
    private WebDriver SelectWebDriver(String browserName, String downloadPath) {
        
        switch(browserName) {
            case "chrome": return new ChromeDriver(SetChromeOptions(downloadPath));
            case "edge": return new EdgeDriver();
            case "firefox": return new FirefoxDriver(SetFirefoxProfile(downloadPath));
            default: return null;
        }
    }
    
    // Webdriver property to add to the system PATH
    // (String) browserName: which browser's property is being loaded
    private String SelectDriverProperty(String browserName) {
        switch(browserName) {
            case "chrome": return "webdriver.chrome.driver";
            case "edge": return "webdriver.edge.driver";
            case "firefox": return "webdriver.gecko.driver";
            default: return null;
        }
    }
    
    // Path to the webdriver executable
    // (String) browserName: which browser's webdriver is being loaded
    private String SelectDriverPath(String browserName) {
        switch(browserName) {
            case "chrome": return "chromedriver.exe";
            case "edge": return "msedgedriver.exe";
            case "firefox": return "geckodriver.exe";
            default: return null;
        }
    }
    
    // Sets options for the chrome driver. For the purposes of setting the download path
    // (String) downloadPath: the path where downloaded files will go
    private ChromeOptions SetChromeOptions(String downloadPath) {
        ChromeOptions options = new ChromeOptions();
        Map<String, Object> prefs = new HashMap<String, Object>();
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.default_directory", downloadPath);
        options.setExperimentalOption("prefs", prefs);       
        
        return options;
    }
    
    // Used to set the profile for the Firefox browser (used for downloads; not working at the moment)
    // (String) downloadPath: the path where downloaded files will go
    private FirefoxOptions SetFirefoxProfile(String downloadPath) {
        
        FirefoxProfile profile = new FirefoxProfile();
        profile.setPreference("browser.download.folderList",2);
        profile.setPreference("browser.download.manager.showWhenStarting",false);
        profile.setPreference("browser.download.dir", downloadPath);
        
        // MIME-type for .txt files doesn't work with text/plain, so all those text file MIME-types are necessary
        profile.setPreference("browser.helperApps.neverAsk.saveToDisk", "application/txt,text/plain,text/txt,text/txt,text/html,text/csv,application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        FirefoxOptions options = new FirefoxOptions();
        options.setProfile(profile);
        return options;
    }
    
    // Gets the webdriver
    public WebDriver GetWebDriver() {
        return this.browserDriver;
    }
    
}