package definitions;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class WETag implements Tag
{
   private String name;
   private String xpath;
   private String id;
   
   public WETag(String name, String xpath, String id) {
       this.name = name;
       this.xpath = xpath;
       this.id = id;
   }
   
   @Override
   public String GetName() {
       return this.name;
   }
   
   public String GetXPath() {
       return this.xpath;
   }
   
   public String GetID() {
       return this.id;
   }
   
   @Override
   public String toString() {
       return String.format("WETag:: name='%s'\txpath='%s'\tid='%s'", this.name, this.xpath, this.id);
   }
   
   @Override
   public boolean equals(Object obj) {
       if (!(obj instanceof WETag))
            return false;
       if (obj == this)
            return true;
       
       WETag otherTag = (WETag)obj;
       if (this.name.equals(otherTag.GetName()) && this.xpath.equals(otherTag.GetXPath()) && this.id.equals(otherTag.GetID()))
           return true;
       else
           return false;
   }
   
   @Override
   public int hashCode() {
       HashCodeBuilder builder = new HashCodeBuilder();
       builder.append(this.name).append(this.xpath).append(this.id);
       return builder.hashCode();
   }
}
