import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Assert;

// For doing asserts with custom stuff - prints out what was being tested, and what was expected
public class Asserting
{
    public Asserting() {
    }
   
    public static void DoAssertEquals(Object testing, Object expected) {
        System.out.println(String.format("Asserting that '%s' (type %s) is equal to '%s' (type %s)", 
            testing.toString(), testing.getClass().getName(), expected.toString(), expected.getClass().getName()));
        Assert.assertEquals(expected, testing);
    }
    
    public static void DoAssertNotEquals(Object testing, Object expected) {
        System.out.println(String.format("Asserting that '%s' (type %s) is *not* equal to '%s' (type %s)", 
            testing.toString(), testing.getClass().getName(), expected.toString(), expected.getClass().getName()));
        Assert.assertNotEquals(expected, testing);
    }
}
