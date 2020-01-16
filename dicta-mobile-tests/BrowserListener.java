import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.events.AbstractWebDriverEventListener;

// A listener class for paying attention when a new page (ie tab) has been opened, and updating the page count (only works if there is an increase in pages)
public class BrowserListener extends AbstractWebDriverEventListener
{
    private Browser testBrowser; // Browser object
    private int pageCountBefore;
    
    public BrowserListener(Browser testBrowser) {
        this.testBrowser = testBrowser;
    }

    public void beforeClickOn(WebElement element, WebDriver driver) {
        this.pageCountBefore = driver.getWindowHandles().size();
    }
    
    public void afterClickOn(WebElement element, WebDriver driver) {
        int pageCountAfter = driver.getWindowHandles().size();
        
        if (this.pageCountBefore < pageCountAfter) {
            System.out.println(String.format("Updating pages (previous: %d, current: %d)", this.pageCountBefore, pageCountAfter));
            testBrowser.UpdatePages();
        }
    }
}
