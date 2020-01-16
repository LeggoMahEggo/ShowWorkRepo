package definitions;
import org.apache.commons.lang3.builder.HashCodeBuilder;


public class TTag implements Tag
{
    private String name;
    private String value;
    
    public TTag(String name, String value) {
        this.name = name;
        this.value = value;
    }
    
    @Override
    public String GetName() {
        return this.name;
    }
    
    public String GetValue() {
        return this.value;
    }
    
    @Override
    public String toString() {
        return String.format("TTag:: name='%s'\tvalue='%s'", this.name, this.value);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TTag))
             return false;
        if (obj == this)
             return true;
       
        TTag otherTag = (TTag)obj;
        if (this.name.equals(otherTag.GetName()) && this.value.equals(otherTag.GetValue()))
            return true;
        else
            return false;
    }
   
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(this.name).append(this.value);
        return builder.hashCode();
    }
}
