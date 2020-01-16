package definitions;
import org.apache.commons.lang3.builder.HashCodeBuilder;

// Class for the 'page' tag in the 'definitions.xml' file (for web element names and xpaths/ids)
public class PTag implements Tag
{
    private String name;
    private String lang;
    private String link;
    
    public PTag(String name, String lang, String link) {
        this.name = name;
        this.lang = lang;
        this.link = link;
    }
    
    @Override
    public String GetName() {
        return this.name;
    }
    
    public String GetLang() {
        return this.lang;
    }
    
    public String GetLink() {
        return this.link;
    }
    
    @Override
    public String toString() {
        return String.format("PTag:: name='%s'\tlang='%s'\tlink='%s'", this.name, this.lang, this.link);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PTag))
             return false;
        if (obj == this)
             return true;
       
        PTag otherTag = (PTag)obj;
        if (this.name.equals(otherTag.GetName()) && this.lang.equals(otherTag.GetLang()) && this.link.equals(otherTag.GetLink()))
            return true;
        else
            return false;
    }
   
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(this.name).append(this.lang).append(this.link);
        return builder.hashCode();
    }
}
