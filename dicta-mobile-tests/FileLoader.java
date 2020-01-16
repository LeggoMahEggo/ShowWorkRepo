import java.util.*;
import java.io.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import java.util.zip.*;
import org.apache.poi.*;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xwpf.extractor.*;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

// For loading files into memory
public class FileLoader
{
    public FileLoader() {
    }
    
    // Loads a text-like file into a list of strings
    // (String) fullPathToFile: the path to the downloaded file + the filename of the downloaded file
    // (boolean) trimWhitespace: whether or not to trim the whitespace from each line (native java method for this does not work, somehow)
    public static List<String> LoadTextFile(String fullPathToFile, boolean trimWhitespace) {
        List<String> lines = new ArrayList<String>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(new File(fullPathToFile)))) {
            String st = "";     
            
            while ((st = br.readLine()) != null) {
                String removedWhitespace = TrimWhitespace(st);

                if (removedWhitespace.length() == 0)
                    continue;
                    
                lines.add((trimWhitespace) ? removedWhitespace : st);
            }
            
        } catch(IOException e) {
                e.printStackTrace();
        }
        
        return lines;
    }
    
    // HTML files
    // Loads an html file into memory, and returns lines for analyzing
    // (String) fullPathToFile: the path to the downloaded file + the filename of the downloaded file
    // (boolean) trimWhitespace: whether or not to trim the whitespace from each line (native java method for this does not work, somehow)
    
    // NOTE: Format saved in html file: <h3> tags have source, <p> tags contain the results, <br> tags are present in if there are multiple verses
    //     returned for a particular search result, <b> tags mark the search text found
    public static List<String> LoadHtmlFile(String fullPathToFile, boolean trimWhitespace) {
        String htmlString = "";
        
        // Create html string to parse
        try (BufferedReader br = new BufferedReader(new FileReader(new File(fullPathToFile)))) {
            String st; 
            
            while ((st = br.readLine()) != null) {
                String removedWhitespace = TrimWhitespace(st);

                if (removedWhitespace.length() == 0)
                    continue;

                htmlString += (trimWhitespace) ? removedWhitespace : st;
            }
            
        } catch(IOException e) {
                e.printStackTrace();
        }
        
        // Parse string
        org.jsoup.nodes.Document htmlDoc = Jsoup.parse(htmlString);
        
        // Analyze elements and put lines into a list
        List<Element> bodyElements = htmlDoc.body().getAllElements();
        List<String> htmlTextLines = new ArrayList();
                
        for (Element bodyElement : bodyElements) {
            String tagName = bodyElement.tag().getName();
                    
            // Do not get the entire body as a line, or bolded words (from the search term(s)) as a line
            if (tagName.equals("body") || tagName.equals("b"))
                continue;
                    
            // If there are BR tags, add each line separately
            boolean hasBRTag = false;
            for (Element child : bodyElement.children()) {
                if (child.tag().getName().toLowerCase().equals("br")) {
                    hasBRTag = true;
                    break;
                }
            }
                    
            // Add lines
            if (tagName.equals("p") && hasBRTag) {
                String fullText = bodyElement.html().replace("<br>", "\n").replace("<BR>", "\n");
                List<String> splitTexts = Arrays.asList(fullText.split("\n"));
                        
                for (String text : splitTexts)
                    htmlTextLines.add(FileLoader.TrimWhitespace(text.replace("<b>", "").replace("</b>", "")));
            } else
                htmlTextLines.add(FileLoader.TrimWhitespace(bodyElement.text()));
        }
        
        return htmlTextLines;
    }
    
    // Load an HTML file using an InputStream instead (ONLY used for loading the Word document)
    public static org.jsoup.nodes.Document LoadHtmlFile(InputStream fileStream, boolean trimWhitespace) {
        String htmlString = "";
        
        // Create html string to parse
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(fileStream, "UTF-8"))) {
            String st; 
            
            while ((st = br.readLine()) != null) {
                String removedWhitespace = TrimWhitespace(st);

                if (removedWhitespace.length() == 0)
                    continue;

                htmlString += (trimWhitespace) ? removedWhitespace : st;
            }
            
        } catch(IOException e) {
                e.printStackTrace();
        }
        
        // Parse string
        org.jsoup.nodes.Document doc = Jsoup.parse(htmlString);
        
        return doc;
    }
    
    // Loads a .docx Word document into memory, and returns the lines as a list of strings
    // (String) fullPathToFile: the path to the downloaded file + the filename of the downloaded file
    // (boolean) trimWhitespace: whether or not to trim the whitespace from each line (native java method for this does not work, somehow)
    
    // NOTE: Since the word document is in .docx format, the actual text can be extracted from the xml file portion (as a .docx is an archive)
    // All text is contained within w:p (paragraph) tags. Actual text is contained within w:t (text) tags, and linebreaks are done with w:br
    //     tags.
    public static List<String> LoadWordFile(String fullPathToFile, boolean trimWhitespace) throws InvalidFormatException {
        org.jsoup.nodes.Document docFromXML = null;
        
        try (ZipFile zp = new ZipFile(new File(fullPathToFile))) {
            ZipEntry textFromXML = zp.getEntry("word/document.xml");
            docFromXML = LoadHtmlFile(zp.getInputStream(textFromXML), trimWhitespace);
        } catch(IOException e) {
                e.printStackTrace();
        }
        
        List<Element> tags = docFromXML.body().getAllElements();
        List<String> wordTextLines = new ArrayList();
                
        for (Element pTag : tags) {
            if (!pTag.tagName().equals("w:p")) // Only get text from paragraph tags
                continue;
                        
            String lineText = "";
            for (Element tag : pTag.getAllElements()) {
                if (!tag.tagName().equals("w:t") && !tag.tagName().equals("w:br")) // Only pay attention to text tags and linebreaks
                    continue;
                        
                // A linebreak represents the end of a single line (there may be multiple text tags per line)
                if (tag.tagName().equals("w:br")) {
                    wordTextLines.add(lineText);
                    lineText = "";
                } else
                    lineText += tag.wholeText();
            }
                    
            // Add last line of the paragraph tag
            if (!lineText.equals(""))
                wordTextLines.add(lineText);
        }
        
        return wordTextLines;
    }
    
    // Trims whitespace from both ends of a string (native java method for this does not work, somehow)
    // (String) line: the string to trim
    public static String TrimWhitespace(String line) {
         // Remove byte-order-mark
        if (line.startsWith("\uFEFF"))
            line = line.substring(1);
        
        int startSub = 0;
        int endSub = line.length() - 1;
        
        if (line.length() == 0)
            return "";
        
        // Left side of the string
        char c1 = line.charAt(startSub);
        while (c1 == ' ' || c1 == '\t' || c1 == '\n') {
            startSub++;
            c1 = line.charAt(startSub);
        }
        
        // Right side of the string
        char c2 = line.charAt(endSub);
        while (c2 == ' ' || c2 == '\t' || c2 == '\n') {
            endSub--;
            c2 = line.charAt(endSub);
        }
        
        if (endSub < startSub || endSub == startSub)
            return "";
        else
            return line.substring(startSub, endSub + 1);
    }
}
