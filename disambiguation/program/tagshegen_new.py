import pandas as pd
import disautils_new as du
import logging
import os
import re
import etagshe as ets

# Module for creating tagging sheet training data for Hebrew machine learning
# Author: Yehuda Broderick

# Logging object
logger = logging.getLogger(__name__)

# Constants
BOM = u"\ufeff" # For removing possible BOMs when loading files
TRAIN = 1 # TRAIN, TEST, BITMORPH, and MLP are given these values for bitwise operations
TEST = 2
BITMORPH = 4
MLP = 8
NMIN = 16
ALL = 32


# Returns the filename of the datafile needed
# word: the hebrew word being worked on
# datafileType: the type of datafile needed
def AppendFileTypeToWord(word, datafileType):
    if (datafileType == TRAIN):
        return word + "_train.txt"
    elif (datafileType == TEST):
        return word + "_test.txt"
    elif (datafileType == BITMORPH):
        return word + "_BitMorph.txt"
    elif (datafileType == MLP):
        return word + "_test_MLP.txt"
    elif (datafileType == 0):
        return ""


# Will check if the Hebrew sentence needs to have quotes escaped. For the sake of importing a .csv
#     file into Excel/Google Sheets
# sentence - the sentence to check
def PossiblyEscapeQuotes(sentence):
    temp = sentence

    # If the first character is a double-quote, then escape it and the next double-quote after it
    #     (Excel can be weird)
    if (temp[0] == "\""):
        temp = temp.replace("\"", "\"\"\"", 2)
    return temp


# Loads a datafile into memory (a dataframe). Returns a dataframe object with the columns 'WordType', 
#     'WordPhrase', and 'Sentence'
# path: the path to main directory
# word: the hebrew word being worked on
# datafileType: the type of datafile being worked with (training, testing, bitmorph, or MLP)
def LoadDatafileIntoDataFrame(path, word, datafileType):
    fullPath = path + word + "\\"
    file = AppendFileTypeToWord(word, datafileType) # Get the appropriate datafile to load
 
    # Make sure that the file exists
    if (not os.path.isfile(fullPath + file)):
        raise FileNotFoundError("{} was not found in {}".format(file, fullPath))
    
    # Check if the file can be loaded in 32-bit architecture
    du.CheckFileByArch32(path, word, file)
    
    # Open the file
    with open(fullPath + file, "r", encoding = "utf-8") as fReader:
    
        # Create lists for each column type
        wordTypes = []
        wordPhrases = []
        sentences = []
    
        # Read in data from each line, and append each part to the appropriate list
        i = 0
        cLine = ""
        try:
            for line in fReader:
                cLine = line
                i += 1
                # Ignore blank lines
                if (len(line.strip()) <= 5): 
                    continue
            
                # Get the word-type + sentence
                splitLine = [line.strip().replace(BOM, "")] if (datafileType == TEST) else re.split("\t| ", line.replace(BOM, ""), 1)
                sentenceIndex = len(splitLine) - 1
            
                # If using the testing datafile (ie unknown sentences), the word being worked on is the word-type
                wordType = word if (datafileType == TEST) else splitLine[0].strip()
            
                # Get the word-phrase using regex if using the training datafile (other datafiles use the
                #     word as the word-phrase)
                wordPhrase = re.search(R"(?<=\\\\)(.*?)(?=\\\\)", splitLine[sentenceIndex].strip()) if (datafileType == TRAIN) else word

                # Ignore lines that are missing the '\\'s
                if (wordPhrase is None):
                    continue
        
                wordTypes.append(wordType)
                wordPhrases.append(wordPhrase.group(0) if (datafileType == TRAIN) else wordPhrase)
                sentences.append(splitLine[sentenceIndex].strip().replace("\t", " ").strip()) # Strip out any additional tabs
            datafileDict = {"WordType": wordTypes, "WordPhrase": wordPhrases, "Sentence": sentences}
        except Exception as e:
            print("Got to line {}".format(i))
            print("Raw line representation:\n{}\n".format(repr(cLine)))
            print("Line split: %s" % (splitLine))
            print("Error: {}".format(repr(e)))
    return pd.DataFrame(data = datafileDict)


# Returns a dataframe that is filtered by word-type, then filtered further by only including entries that have a word-phrase
#     that appears more than 20 times across the dataframe
# df: the dataframe to filter
# isMajCase: if the type of dataframe (ie of the datafile) is a majority case word or not. If it is, then only word-phrases 
#     that appear 21+ times are taken, otherwise all the word-phrases are taken
# wordType: the word-type to filter by
def GetDataFrameOfPhrasesByType(df, isMajCase, wordType):
            
    # Filter the dataframe by word-type, and assign a dataframe consisting of only the word-phrases column
    wordPhrasesDF = pd.DataFrame(df[df["WordType"] == wordType]["WordPhrase"])
    
    # Assign a dataframe that consists of the value counts of each phrase
    tempDF = pd.DataFrame(wordPhrasesDF["WordPhrase"].value_counts())
    
    # Filter the value counts by those who have 21+ entries (if majority case)
    phrasesCount21DF = tempDF[tempDF["WordPhrase"] > 20] if (isMajCase == True) else tempDF
      
    # Get a list of the word-phrases accepted
    phrasesList = list(phrasesCount21DF["WordPhrase"].index)
   
    return df[df["WordPhrase"].isin(phrasesList)]


# Returns a dictionary with the dataframe names as the keys, and the dataframes as the values
# path: the path to the main directory
# word: the hebrew word being worked on
# datafileTypes: what type(s) of dataframe(s) to load. Format is as follows:
#     TRAIN: create a dataframe from the training datafile
#     TEST: create a dataframe from the testing datafile
#     BITMORPH: create a dataframe from the bitmorph datafile
#     MLP: create a dataframe from the MLP datafile
#     NMIN: create a dataframe from the nakdan minority (nmin) datafile(s)
# Use like so: [datafile type 1] | [datafile type 2] | .... [datafile type n]
def GetDataFrameDictionary(path, word, datafileTypes):
    fullPath = path + word + "\\"
    dfDict = {}
    
    # Load the datafile dataframes into memory. Bitwise operators make for easy selection of datafiles to load
    # Load all
    if (datafileTypes & ALL):
        # To allow the outermost try to catch possible exceptions and still continue with execution,
        #     check to make sure the file exits before loading datafiles
        datafilesFailed = []
        
        if (os.path.isfile(fullPath + AppendFileTypeToWord(word, TRAIN))):
            logger.info("Loading training datafile")
            trainDF = LoadDatafileIntoDataFrame(path, word, TRAIN)
            dfDict["trainDF"] = trainDF
        else:
            errMsg = "Couldn't load training datafile"
            datafilesFailed.append("training")
            logger.info(errMsg)
            
        if (os.path.isfile(fullPath + AppendFileTypeToWord(word, TEST))):
            logger.info("Loading testing datafile")
            testDF = LoadDatafileIntoDataFrame(path, word, TEST)
            dfDict["testDF"] = testDF
        else:
            errMsg = "Couldn't load testing datafile"
            datafilesFailed.append("testing")
            logger.info(errMsg)
        
        if (os.path.isfile(fullPath + AppendFileTypeToWord(word, BITMORPH))):
            logger.info("Loading bitmorph datafile")
            bitmorphDF = LoadDatafileIntoDataFrame(path, word, BITMORPH)
            dfDict["bitmorphDF"] = bitmorphDF
        else:
            errMsg = "Couldn't load bitmorph datafile"
            datafilesFailed.append("bitmorph")
            logger.info(errMsg)
            
        if (os.path.isfile(fullPath + AppendFileTypeToWord(word, MLP))):
            logger.info("Loading MLP datafile")
            mlpDF = LoadDatafileIntoDataFrame(path, word, MLP)
            dfDict["mlpDF"] = mlpDF
        else:
            errMsg = "Couldn't load MLP datafile"
            datafilesFailed.append("MLP")
            logger.info(errMsg)
            
        nminFiles = ets.GetNMinFilesByWord(path, word)
        if (len(nminFiles) > 0):
            logger.info("Loading NMin datafile(s)")
            nminDF = ets.GetNMinDataframe(path, word)
            dfDict["nminDF"] = nminDF
        else:
            errMsg = "Couldn't load NMin datafile(s)"
            datafilesFailed.append("NMin")
            logger.info(errMsg)
        
        fullErr = "(couldn't load "
        for i, datafile in enumerate(datafilesFailed):
            if (i > 0 and i == len(datafilesFailed) - 1):
                fullErr += " and %s" % (datafile)
            elif (i > 0):
                fullErr += ", %s" % (datafile)
            elif (i == 0):
                fullErr += "%s" % (datafile)
        fullErr += " datafile(s))......"
        
        if (len(datafilesFailed) > 0):
            print(fullErr, end = "")
            
    # Load one by one
    else:
        if (datafileTypes & TRAIN):
            logger.info("Loading training datafile")
            trainDF = LoadDatafileIntoDataFrame(path, word, TRAIN)
            dfDict["trainDF"] = trainDF
    
        if (datafileTypes & TEST):
            logger.info("Loading testing datafile")
            testDF =  LoadDatafileIntoDataFrame(path, word, TEST)
            dfDict["testDF"] = testDF
    
        if (datafileTypes & BITMORPH):
            logger.info("Loading bitmorph datafile")
            bitmorphDF = LoadDatafileIntoDataFrame(path, word, BITMORPH)
            dfDict["bitmorphDF"] = bitmorphDF
        
        if (datafileTypes & MLP):
            logger.info("Loading MLP datafile")
            mlpDF = LoadDatafileIntoDataFrame(path, word, MLP)
            dfDict["mlpDF"] = mlpDF
            
        if (datafileTypes & NMIN):
            logger.info("Loading NMin datafile(s)")
            nminDF = ets.GetNMinDataframe(path, word)
            dfDict["nminDF"] = nminDF
    
    return dfDict


# Creates sheets for tagging words, in a .csv file format 
# Returns a list of dictionaries with the sheet data
# path: the path to the main directory
# word: the hebrew word being worked on
# datafileTypes: what type(s) of tagging sheet(s) to generate. Format is as follows:
#     TRAIN: generate tagging sheets from the training datafile
#     TEST: generate a tagging sheet from the testing datafile
#     BITMORPH: generate tagging sheets from the bitmorph datafile
#     MLP: generate tagging sheets from the MLP datafile
# Use like so: [datafile type 1] | [datafile type 2] | .... [datafile type n]
def CreateTaggingSheets(path, word, datafileTypes):
    fullPath = path + word + "\\"
    logger.info("Creating tagging sheets in {}".format(fullPath))
    logger.info("Loading datafiles into memory")
    dfDict = GetDataFrameDictionary(path, word, datafileTypes)
    
    if (len(dfDict) == 0):
        errMsg = "No dataframes could be loaded"
        logger.info(errMsg)
        raise ValueError(errMsg)
    
    # Remove nmin dataframe if it exists - there's a separate function for creating nmin tagging sheets
    if ("nminDF" in dfDict.keys()):
        del dfDict["nminDF"]
    
    # Get rid of whitespace in the dataframes and drop duplicate rows
    for dfName, df in dfDict.items():
        dfDict[dfName] = df.applymap(lambda x: x.strip())
        dfDict[dfName].drop_duplicates(subset = "Sentence", inplace = True, keep = "first")
        
        # Remove the rows from testDF whose sentences *also* appear in bitmorphDF
        if (dfName == "testDF" and datafileTypes & BITMORPH):
            bitmorphSentences = list(dfDict["bitmorphDF"]["Sentence"])
            testDF = dfDict["testDF"]
            testDF = testDF[~testDF.Sentence.isin(bitmorphSentences)]
    
    # Set the various hebrew names of the different sheets
    haRov = "הרוב"
    shonot = "שונות" # For word-types that are used to remove a 3rd/4th option (doesn't go through MLP)
    miut = "מיעוט"
    loYadua = "לא ידוע"
    morphologia = "מורפולוגיה"
    
    # Go through the dataframes, and add them (and relevant other data) to a list of dictionaries containing tagging sheet data
    sheetData = []
    for dfName, df in dfDict.items():
        logger.info("Processing {} dataframe".format(dfName.replace("DF", "")))
        
        # Training dataframe - majority and minority cases ('chk' are treated as majority, but don't get process with the MLP)
        if (dfName == "trainDF"):
            logger.info("Loading cases file into memory")
            casesDict = du.GetWordCases(path, word)
            
            # Go through each majority/minority case, filter the majority case words by phrase (only take rows whose phrases
            #     appear 21+ times in the dataframe)
            # Additionally, set the filename, case, and word-type
            for wordType,case in casesDict.items():
                if (case == "maj" or case == "chk"):
                    sheetDF = GetDataFrameOfPhrasesByType(df, True, wordType)
                    sheetWord = shonot if (case == "chk") else haRov
                    sheetCase = "maj"
                elif (case == "min"):
                    sheetDF = GetDataFrameOfPhrasesByType(df, False, wordType)
                    sheetWord = miut
                    sheetCase = "min"
                sheetDict = {"dataframe": sheetDF, "filename": "{} - {}.csv".format(wordType, sheetWord), "case": sheetCase, 
                             "type": wordType}
                sheetData.append(sheetDict)
        
        # Testing dataframe - unknown cases
        # Mostly the same as the training dataframe, no filter for the words
        if (dfName == "testDF"):
            wordType = word
            sheetDF = df
            sheetWord = loYadua
            sheetCase = "unk"
            sheetDict = {"dataframe": sheetDF, "filename": "{} - {}.csv".format(wordType, sheetWord), "case": sheetCase, 
                         "type": wordType}
            sheetData.append(sheetDict)
        
        # Bitmorph dataframe
        # Go through every bitmorph word-type, and create a sheet for it
        if (dfName == "bitmorphDF"):
            bitmorphTypeList = list(df["WordType"].unique())
            for bitmorphType in bitmorphTypeList:
                wordType = bitmorphType
                sheetDF = df[df["WordType"] == wordType]
                sheetWord = morphologia
                sheetCase = "bm"
                sheetDict = {"dataframe": sheetDF, "filename": "{} - {}.csv".format(wordType, sheetWord), "case": sheetCase, 
                         "type": wordType}
                sheetData.append(sheetDict)
        
        # MLP dataframe
        # Go through every MLP word-type, and create a sheet for it
        if (dfName == "mlpDF"):
            mlpTypeList = list(df["WordType"].unique())
            for mlpType in mlpTypeList:
                wordType = mlpType
                sheetDF = df[df["WordType"] == wordType]
                sheetWord = "MLP"
                sheetCase = "mlp"
                sheetDict = {"dataframe": sheetDF, "filename": "{} - {}.csv".format(wordType, sheetWord), "case": sheetCase, 
                         "type": wordType}
                sheetData.append(sheetDict)
    
    # Go through every sheet, and create it
    logger.info("Generating sheets")
    for sheet in sheetData:
        df = sheet["dataframe"]
        filename = sheet["filename"]
        case = sheet["case"]
        wordType = sheet["type"]
        logger.info("Processing {}".format(filename[:-4]))
        
        # Set the sample size according to the particular sheet being created
        sampleSize = 0
        if (case == "unk" or case == "bm"):
            sampleSize = 2000
        elif (case == "mlp"):
            sampleSize = 1000
        elif (case == "maj"):
            sampleSize = 20
        
        # Set variables for formatting the header of the .csv file
        nachon = "נכון"
        kvutzah = "קְבוּצָה"
        mishpat = "משפט"
            
        # Apply the appropriate sample sizes to the dataframe
        finalDF = df.sort_values(by = "WordPhrase")
        if (sampleSize > 0):
            finalDF = finalDF.groupby("WordPhrase").apply(lambda s: s.sample(min(len(s), sampleSize)))
        elif (sampleSize == 0 and case == "min"):
            # Stopgap measure to get proportional samples for minority cases
            finalWordPhraseList = list(finalDF["WordPhrase"].unique())
            stopgapDF = pd.DataFrame(columns = finalDF.columns)
            
            examplesOf1000 = 1000
            for wordPhrase in finalWordPhraseList:
                sampleToUse = int((len(finalDF[finalDF["WordPhrase"] == wordPhrase])/len(finalDF)) * 1000)
                sampleToUse = 1 if (sampleToUse == 0) else sampleToUse
                sampleToUse = min(sampleToUse, len(finalDF[finalDF["WordPhrase"] == wordPhrase]))
                stopgapDF = pd.concat([stopgapDF, finalDF[finalDF["WordPhrase"] == wordPhrase].sample(n = sampleToUse)])
            finalDF = stopgapDF
            
        # Escape quotes at the beginning of sentences (only needed for when importing the .csvs directly into Excel, remove
        #     them otherwise)
        finalDF["Sentence"] = finalDF["Sentence"].apply(lambda x: PossiblyEscapeQuotes(x))
        
        # Generate headers, and manipulate the final dataframe to produce a dataframe with one column, and every row in the format:
        #     Not from testDF: [blank]\t[word phrase]\t[sentence]
        #     From testDF: [1st case]\t[2nd case]...[nth case]\t[sentence]
        if (case == "unk"):
            logger.info("Loading cases file into memory")
            casesDict = du.GetWordCases(path, word)
            
            header = ""
            for wordType,case in casesDict.items():
                header += "{}?\t".format(wordType)
            header += mishpat + "\n"
            casesCount = len(casesDict)
            listDF = pd.DataFrame(data = {"Rows": list(finalDF[finalDF.columns[-1]].apply(lambda x: " \t"*casesCount + "{}".format(x.strip())))})
        else:
            fNachon = "{} {}?".format(wordType, nachon)
            header = "{}\t{}\t{}".format(fNachon, kvutzah, mishpat) + "\n"
            listDF = pd.DataFrame(data = {"Rows": list(finalDF[finalDF.columns[1:]].apply(lambda x: " \t" + "\t".join(x.str.strip()), axis = 1))})
        
        # Convert 'Rows' to list, write the header and rows to a .csv file
        fileList = list(listDF["Rows"])
        filename = filename.replace("\\", "$").replace("/", "$") # No special characters in filenames
        logger.info("Writing sheet to {}".format(filename))
        with open(fullPath + filename, "wb") as fWriter:
                
            fWriter.write(header.encode("utf-8"))
                
            for i in range(0, len(fileList)):
                row = fileList[i] + "\n"
                fWriter.write(row.encode("utf-8"))
                
    # Lastly, return the sheet data, so that further things can be done with the newly-generated tagging sheets (through the
    #     filenames)
    return sheetData