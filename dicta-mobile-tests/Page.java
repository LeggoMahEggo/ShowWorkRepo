import org.apache.commons.lang3.builder.HashCodeBuilder;

// Class for storing data on pages opened in a WebDriver
public class Page
{
    private String url; // Url of the page
    private String title; // Page title
    private String id; // window handle id (from the WebDriver)
    
    public Page(String url, String title, String id) {
        this.url = url;
        this.title = title;
        this.id = id;
    }
    
    public String GetUrl() {
        return this.url;
    }
    
    public void SetUrl(String newUrl) {
        this.url = newUrl;
    }
    
    public String GetTitle() {
        return this.title;
    }
    
    public void SetTitle(String newTitle) {
        this.title = newTitle;
    }
    
    // This is to identify via the web driver
    public String GetID() {
        return this.id;
    }
   
   @Override
   public String toString() {
       return String.format("Title: '%s', Url: '%s', Id: '%s'", this.title, this.url, this.id);
   }
   
   @Override
   public boolean equals(Object obj) {
       if (!(obj instanceof Page))
            return false;
       if (obj == this)
            return true;
       
       Page otherPage = (Page)obj;
       if (this.url.equals(otherPage.GetUrl()) && this.title.equals(otherPage.GetTitle()) && this.id.equals(otherPage.GetID()))
           return true;
       else
           return false;
   }
   
   @Override
   public int hashCode() {
       HashCodeBuilder builder = new HashCodeBuilder();
       builder.append(this.url).append(this.title).append(this.id);
       return builder.hashCode();
   }
}
