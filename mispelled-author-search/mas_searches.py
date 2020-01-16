import hebrew_handler as hh
from mas_levenshtein import LevenshteinDistance
import mas_database as mas_d
import os
import pandas as pd
import re
import whoosh.analysis as wa
from whoosh.fields import *
import whoosh.qparser as wqp
from whoosh.qparser import QueryParser


# CLASSES
class HebrewSoundexFilter(wa.Filter):
    
    def __call__(self, tokens):
        for t in tokens:
            t.text = soundex(t.text, "heb")
            yield t
            
class EnglishSoundexFilter(wa.Filter):
    
    def __call__(self, tokens):
        for t in tokens:
            t.text = soundex(t.text, "eng")
            yield t


# Analyzers for Whoosh (used for the soundex algorithm)
def GetHebrewSoundexAnalyzer():
    return wa.RegexTokenizer() | wa.LowercaseFilter() | HebrewSoundexFilter()
            
def GetEnglishSoundexAnalyzer():
    return wa.RegexTokenizer() | wa.LowercaseFilter() | EnglishSoundexFilter()

    
# Helper functions for soundex algorithm
# Check whether or not the characters in the query ALL come from English, ALL come from Hebrew, or are some mix of both
# query: the text being searched for
# lang: which language is being used in the search ('heb' or 'eng')
def HasOppositeLanguageInside(query, lang):
    hebChars = hh.GetHebrewLetters()
    engChars = [chr(i) for i in range(65, 90 + 1)] + [chr(i) for i in range(97, 122 + 1)]
    checkChars = hebChars if (lang == "eng") else engChars
    
    for char in query:
        if (char in checkChars):
            return True
    
    return False

# Strips the characters of the opposite language, of the language being used for the query
# E.G. if you're searching for משה and the language is 'heb', and the query is Moshe (משה), strip out all non-Hebrew
#     characters
# query: the text being searched for
# lang: which language is being used in the search ('heb' or 'eng')
def StripWrongLanguageChars(query, lang):
    hebRegex = "[^" + "".join(hh.GetHebrewLetters()) + "]"
    engRegex = "[^A-Za-z]"
    stripRegex = engRegex if (lang == "eng") else hebRegex
    
    if (HasOppositeLanguageInside(query, lang)):
        return re.sub(stripRegex, "", query).strip()
    else:
        return query

# Returns stuff for soundex based on language and thing needed
def GetCodesOrChars(lang, isChar):
    if (lang == "eng"):
        if (isChar):
            return EngToIgnore()
        else:
            return EngCodes()
    elif (lang == "heb"):
        if (isChar):
            return HebToIgnore()
        else:
            return HebCodes()

# English characters to ignore when doing Soundex
def EngToIgnore():
    return ('a', 'e', 'i', 'o', 'u', 'y', 'h', 'w')
# Hebrew characters to ignore when doing Soundex
def HebToIgnore():
    return ("א", "ה", "י", "ע")

# Soundex codes for English characters
def EngCodes():
    return {('b', 'f', 'p', 'v'): 1, ('c', 'g', 'j', 'k', 'q', 's', 'x', 'z'): 2,
            ('d', 't'): 3, ('l',): 4, ('m', 'n'): 5, ('r',): 6}
# Soundex codes for Hebrew characters
def HebCodes():
    return {
     ("ב", "ו", "פ", "ף"): 1,
#     ("ג", "ז", "ח", "כ", "ך", "ק", "ש", "ס"): 2,
     ("ג", "ז", "ח", "כ", "ך", "ק"): 2,
     ("ד", "ט", "ת"): 3,
     ("צ", "ץ"): 32,
     ("ל"): 4, 
     ("מ", "ם", "נ", "ן"): 5,
     ("ר"): 6,
     ("ש", "ס"): 7,
     ("א", "ה", "י", "ע"): 8
    } 
    
# Soundex algorithm generously donated by Yash Agarwal at https://medium.com/@yash_agarwal2/soundex-and-levenshtein-distance-in-python-8b4b56542e9e
def soundex(query, lang):
    removeChars = "\"'." # characters that can't be in the query, else we wrong soundex codes
    removeChars += "\'̣:-\"\u202cʼ,ʻ[]ṭ;ùèüñó412\u202b/ḥ`״Ã¦&¶Ìæ’\\́=Ḥ()ḳàʾ\u200fṿʹ!öá׳Ḳ̈̀3…6°ò¬ʿśí|50Ṭ\xa0ä7ŚéẔŠū9‘8Ṿ?Æ̆_øėß̃̄̋ł–\u200eẒÖŁçŽžńćÜšýē<>ûőôîāПольшаąŭëșÉțеœİěБртисвМукчНдныГмяЛюбРřÚęìČżčĺОã"
    
    # Step -1: remove opposite language characters + other bad chars
    query = StripWrongLanguageChars(query, lang)
    temp = ""

    for char in query:
        if (char not in removeChars):
            temp += char
    query = temp
    
    # Step 0: Clean up the query string
    query = query.lower()
    letters = [char for char in query if char.isalpha()]
    
    # Return '000' if the query is empty or there are no characters in 'letters'
    if (len(query) == 0 or len(letters) == 0):
        return "000"
    
    # Step 1: Save the first letter. Remove all occurrences of a, e, i, o, u, y, h, w/aleph hei yud ayin.
    # If query contains only 1 letter, return query+"000" (Refer step 5)
    if (len(query) == 1):
        return query + "000"

    to_remove = GetCodesOrChars(lang, True)
    first_letter = letters[0]
    letters = letters[1:] if (lang == "eng") else letters[:] # first_letter only applies for english
    
    # FOR HEBREW ONLY - leave 'ignore' characters alone if they appear at the end of a word
    if (lang == "heb"):
        letters = [char for i,char in enumerate(letters) if (char not in to_remove  or (char in to_remove and i+1 == len(letters)))]
    else:
        letters = [char for char in letters if char not in to_remove]

    if (len(letters) == 0):
        return first_letter + "000"
    
    # Step 2: Replace all consonants (include the first letter) with digits according to rules
    to_replace = GetCodesOrChars(lang, False)

    if (lang == "eng"):
        first_letter = [value if first_letter else first_letter for group, value in to_replace.items()
                        if first_letter in group]
    letters = [value if char else char
               for char in letters
               for group, value in to_replace.items()
               if char in group]

    # Step 3: Replace all adjacent same digits with one digit.
    letters = [char for ind, char in enumerate(letters)
               if (ind == len(letters) - 1 or (ind+1 < len(letters) and char != letters[ind+1]))]

    # Step 4: If the saved letter’s digit is the same the resulting first digit, remove the digit (keep the letter)
    if (first_letter == letters[0]):
        letters[0] = query[0]
    else:
        letters.insert(0, query[0])

        
    # Step 5: Append 3 zeros if result contains less than 3 digits.
    # Remove all except first letter and 3 digits after it.
    first_letter = letters[0]
    letters = letters[1:]

    letters = [char for char in letters if isinstance(char, int)][0:3]

    while len(letters) < 3:
        letters.append(0)

    letters.insert(0, first_letter)
    string = "".join([str(l) for l in letters])

    return string    


# Regular searches using Whoosh
# Perform a search on the Footprints dataframe that returns a dataframe with the searched data using Whoosh
# pathToIndex: the path where the Whoosh index resides
# dbDF: the Footprints database
# lang: which language the search is being performed in ('heb' or 'eng')
# textToSearch: what to search for
# searchType: what type of search being performed (currently 'exact' or 'soundex')
def PerformWhooshSearch(pathToIndex, dbDF, lang, textToSearch, searchType):
    
    # Only Whoosh search types currently available are 'exact' and 'soundex'
    if (searchType not in ["exact", "soundex"]):
        raise ValueError("'%s' is not a valid search type!" % (searchType))

    doSoundex = False
    ix = mas_d.CreateOrLoadSchemaAndGetIndex(pathToIndex, dbDF)

    if (searchType == "soundex"):
        doSoundex = True
        soundexDict = {"heb": "hebNameSX", "eng": "engNameSX"}
        searchField = soundexDict[lang]
    elif (searchType == "exact"):
        searchField = "recordName"

    searchDF = pd.DataFrame(columns = ["id", "name", "type"])
    
    # Perform the search
    with ix.searcher() as searcher:
        parser = QueryParser(searchField, ix.schema)
        query = parser.parse(textToSearch)
        results = searcher.search(query, limit = None)
 
        for i,result in enumerate(results):
            recordID = result["recordID"]
            recordName = result["recordName"]
            recordType = result["recordType"]
            searchDF.loc[i] = [recordID, recordName, recordType]
    
    return {"searchType": searchType, "df": searchDF.reset_index(drop = True)}


# Levenshtein search
# Helper function - does the actual work, used mainly in pandas's .apply function
def LevenshteinHelperFunction(searchTokens, name, maxScore = 2.0):
    removeChars = "[\\\(\)\[\]\.\?\"'ʻʼ\-–_,;!:°…¦\|¬=<>]" # Invalid search characters
    
    # Remove invalid characters and separate name string into tokens
    nameTokens = re.sub(removeChars, "", name.strip().lower().replace("...", " ")).split()
    
    # Set initial score for author in the search (100 per token)
    aggScore = 100.0 * len(searchTokens)
    
    # If no search text, return 100 as a score
    if (len(searchTokens) == 0):
        return 100.0
    
    # Only loop by the amount of *search* tokens
    for searchToken in searchTokens:        
        aggScoreList = []
        
        # Score every author token against each search token, and if the score falls under the maxScore value add it to a list
        for nameToken in nameTokens:
            score = LevenshteinDistance(searchToken, nameToken)
            if (score <= maxScore): aggScoreList.append(score)
        
        # Decrease the overall score and add the lowest score found of the author tokens to the aggregated score
        if (len(aggScoreList) > 0):
            aggScore -= 100.0
            aggScore += min(aggScoreList)
            
    return aggScore
    
# Returns a list of dictionaries built from the Footprints database that have been scored by a levenshtein algorithm
# Best scores to take when combing through the database are those that are no bigger than maxScore*(number of search tokens)
# searchText: the text to search for
# dbDF: the database to search through
# maxScore(optional): the max amount of edit-distance allowed. The less, the better (default 2.0)
# NOTE: if searchText is empty, the function returns the database dataframe, *without* the 'score' column
def LevenshteinSearchByScore(searchText, dbDF, maxScore = 2.0):
    removeChars = "[\\\(\)\[\]\.\?\"'ʻʼ\-–_,;!:°…¦\|¬=<>]" # Invalid search characters
    
    # Search text should not be empty
    if (searchText.strip() == ""):
        return dbDF
    
    # Remove invalid search characters and separate search text into words ('tokens')
    searchTokens = re.sub(removeChars, "", searchText.strip().lower().replace("...", " ")).split()
    
    # Perform the search
    dbDF["score"] = dbDF["name"].apply(lambda x: LevenshteinHelperFunction(searchTokens, x, maxScore))
        
    searchTokenCount = len(searchTokens)
    return dbDF[dbDF["score"] <= maxScore * searchTokenCount].reset_index(drop = True) # Return only names whose scores were under the threshold


# Put all search results into one large dataframe, and return it in a dictionary (all of the dataframes from searches are
#     returned as well)
# searchDicts: a list of dictionaries that contain the dataframes from searches
def GetFinalSearchResults(searchDicts):
    finalDF = pd.DataFrame(columns = ["id", "name", "type"])
    jsonSearchDicts = [] # For converting dataframes to JSON objects (they can't be sent otherwise)
    
    for searchDict in searchDicts:
        searchDF = searchDict["df"]
        searchType = searchDict["searchType"]
        
        finalDF = pd.concat([finalDF, searchDF], sort = False)
        jsonSearchDicts.append({"searchType": searchType, "df": searchDF.to_json()})
    
    # Remove duplicates
    finalDF.drop_duplicates(subset = ["id", "name", "type"], keep = "first", inplace = True)
    
    # Fill NaN values with -1.0 (will identify non-levenshtein searches - levenshtein search has a column the others don't)
    finalDF.fillna(value = -1.0, inplace = True)

    return {"final_results": finalDF.reset_index(drop = True).to_json(), "search_data": jsonSearchDicts}