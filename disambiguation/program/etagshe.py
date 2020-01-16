import re
import logging
import pandas as pd
import os
from os import listdir
from os.path import isfile, join
import tagshegen_new as tsg
from disautils_new import CheckFileByArch32, NoFilesFoundError

# Module for generating additional tagging sheets ('extra') after the main tagging sheets have been generated, for 
#     the disambiguation project
# Author: Yehuda Broderick

# Logging object
logger = logging.getLogger(__name__)

# If there are 2+ whitespace characters next to each other within the first 35 characters of a line, split into 2 and return
#     the second element (the sentence of a datafile line). For getting rid of word-types of a training datafile line.
# line: the datafile line that potentially needs splitting
def SplitIfNeeded(line):
    doSplit = False
    
    wsCount = 0
    for i,char in enumerate(line):
        counter = i + 1
        if (i > 35):
            break
        
        if (char in [" ", "\t"]):
            wsCount += 1
            
            if (wsCount >= 2):
                doSplit = True
                break
        else:
            wsCount = 0
    
    # Return a split or non-split line    
    if (doSplit):
        return re.split("\t| ", line.strip(), 1)[1].strip()
    else:
        return line.strip()


# Returns the nmin ('nikud/nakdan minority') datafiles for a particular word
# path: the path to the directory with every hebrew word
# word: the hebrew word to get nmin files from
# NOTE: for now, the nmin datafiles will have the 'OutputNikudSearch-' as part of their filenames'
def GetNMinFilesByWord(path, word):
    fullPath = path + word + "\\"
    onlyHebRegex = "[^א-ת]"
    
    reqFiles = []
    nminList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]
    
    logger.info("Retrieving nmin datafiles")
    for file in nminList:
        if ("Output" not in file):
            continue
            
        # Only the .txt files get checked
        if (".zip" not in file):
            CheckFileByArch32(path, word, file) # Make sure that the file is less than 750 mb (if running in 32-bit mode)
        
        hebWordOnly = re.sub(onlyHebRegex, "", file)
        
        if (hebWordOnly == word and file.replace(".zip", "") not in reqFiles):
            reqFiles.append(file.replace(".zip", ""))
    
    logger.info("Nmin datafiles found: %s" % (", ".join(reqFiles)))
    return reqFiles
    

# Get a dictionary of dictionaries, with each key-value pair being the word-type, plus a dictionary of the hebrew-only lines
#     and their respective word-phrases
# path: the path to the main disambiguation directory
# word: the hebrew word to work on
# nminFiles: the list of nmin datafiles to load into memory
# NOTE: for now, the nmin datafiles will have the 'OutputNikudSearch-' as part of their filenames'
def GetNMinFileDict(path, word, nminFiles):
    logger.info("Loading nmin datafiles into memory")
    fullPath = path + word + "\\"
    nminDict = {}
    
    # Make sure there are files to load
    if (len(nminFiles) == 0):
        raise NoFilesFoundError("Could not find any nmin datafiles to load in %s" % (fullPath))
    
    for file in nminFiles:
        logger.info("Loading %s" % (file))
        wordType = file[:-4].replace("OutputNikudSearch-", "").strip()
        nminDict[wordType] = {}
        sentences = []
        phrases = []
        
        fileLines = []
        with open(fullPath + file, "r", encoding = "utf-8") as fReader:
            fileLines = fReader.readlines() # due to an error, doing it this way
        
        for line in fileLines:            
            if (line.strip() == ""):
                continue
            
            sentenceOnly = SplitIfNeeded(line.strip().replace(tsg.BOM, "")).replace("\t", " ")
            wordPhrase = re.search(R"(?<=\\\\)(.*?)(?=\\\\)", sentenceOnly)
            
            # Lines that are missing the '\\'s get 'UNK_REMOVE' (for removing the lines later)
            if (wordPhrase is None):
                phrases.append("UNK_REMOVE")
            else:
                hebRegex = "[^א-ת \-―']"
                newPhrase = re.sub(hebRegex, "", wordPhrase.group(0).strip())
                phrases.append(newPhrase)
        
            sentences.append(sentenceOnly.strip())
        nminDict[wordType] = {"sentences": sentences, "phrases": phrases}
        
    return nminDict
    

# Returns a dataframe of the nikud/nakdan minority-case words
# path: path to the hebrew words directory
# word: the hebrew word which is being workd on
def GetNMinDataframe(path, word):
    logger.info("Loading nmin dataframe into memory")
    fullPath = path + word + "\\"

    # Setup nmin files' data
    nminFiles = GetNMinFilesByWord(path, word)
    nminDict = GetNMinFileDict(path, word, nminFiles) # Should be: per word-type, a dictionary of lines and word-phrases
    nminWordTypes = list(nminDict.keys())
  
    # Load training datafile into memory
    trainFile = word + "_train.txt"
    
    # First, check that the training datafile exists
    if (not os.path.exists(fullPath + trainFile)):
        raise FileNotFoundError("%s was not found in %s" % (trainFile, fullPath))
    
    # Make sure that the file is less than 750 mb (if running in 32-bit mode)
    CheckFileByArch32(path, word, trainFile)
    
    logger.info("Loading %s" % (trainFile))
    trainSentences = []
    with open(fullPath + trainFile, "r", encoding = "utf-8") as fReader:
        for line in fReader:
            if (line.strip() == ""):
                continue
                
            trainSentences.append(SplitIfNeeded(line.replace(tsg.BOM, "").strip()))
    trainSenSize = len(trainSentences)
        
    trainOrigin = ["Train" for i in range(0, trainSenSize)]
    trainTypes = ["" for i in range(0, trainSenSize)]
    trainPhrases = ["" for i in range(0, trainSenSize)]


    # Arrange nmin data in a similar manner to the training datafile
    logger.info("Arranging nmin data to conform to the training datafile")
    nminSentences = []
    nminTypes = []
    nminPhrases = []
    for wordType in nminWordTypes:
        typeDict = nminDict[wordType]
        
        nminSentences += typeDict["sentences"]
        nminPhrases += typeDict["phrases"]
        nminTypes += [wordType for i in range(0, len(typeDict["sentences"]))]
    nminOrigin = ["NMin" for i in range(0, len(nminSentences))]    
    

    # Create a dataframe with the training datafile lines and the nmin datafile lines combined (sentence, wordtype, and origin)
    logger.info("Creating base dataframe")
    combinedDF = pd.DataFrame(data = {"WordType": trainTypes+nminTypes,
                                      "WordPhrase": trainPhrases+nminPhrases,
                                      "Sentence": trainSentences+nminSentences,
                                      "Origin": trainOrigin+nminOrigin})
    
    # Create a deep copy of the above dataframe (used for removing identical sentences)
    logger.info("Removing identical sentences from base dataframe")
    removerDF = combinedDF.copy() 
    
    # Drop duplicate sentences
    hebRegex = "[^א-ת ]"
    removerDF["Sentence"] = removerDF["Sentence"].apply(lambda x: re.sub(hebRegex, "", x))
    removerDF.drop_duplicates(subset = "Sentence", keep = False, inplace = True)
    
    # Perform an inner join with the original dataframe and the dataframe that just had rows dropped
    finalDF = combinedDF.join(other = removerDF, how = "inner", rsuffix = "_other")
    
    # Remove sentences that do not have retrievable word-phrases
    onlyNMinDF = finalDF[(finalDF["Origin"] == "NMin") & (finalDF["WordPhrase"] != "UNK_REMOVE")][["WordType", "WordPhrase", "Sentence"]]
    
    return onlyNMinDF
    

# Returns a sorted dataframe of random samples (using the nmin dataframe as the base)
# nminDF: the dataframe to get the random samples from. Created from nmin files
# sampleSize(optional): how many samples to take per word-type (default 1000)
def GetNMinSampledDataframe(nminDF, sampleSize = 1000):
    logger.info("Returning the nmin dataframe with a sample size of %d per word-type available" % (sampleSize))
    randomSamplesDF = pd.DataFrame(columns = nminDF.columns) # Create an empty dataframe for the random samples
    wordTypes = list(nminDF["WordType"].unique())
    
    for wordType in wordTypes:
        wordTypeDF = nminDF[nminDF["WordType"] == wordType]
    
        randomSamplesDF = pd.concat([randomSamplesDF, wordTypeDF.sample(n = min(sampleSize, len(wordTypeDF)))], sort = False)
    return randomSamplesDF.sort_values(by = ["WordType", "WordPhrase"])
        
    
# Loads all 'extra' sentences to be tagged (from 'extra' files) into a dataframe
# path: the path to the disambiguation directory
# word: the hebrew word being worked on
def LoadExtraFilesIntoDataframe(path, word):
    logger.info("Loading (potentially) existing 'extra' files into memory")
    fullPath = path + word + "\\"
    fileList = [f for f in listdir(fullPath) if (isfile(join(fullPath, f)) and "נויסף" + ".csv" in f)] # Load only 'extra' files
    fileLines = []
    
    for file in fileList:
        logger.info("Loading %s" % (file))
        CheckFileByArch32(path, word, file) # Make sure that the file is less than 750 mb (if running in 32-bit mode)
        
        with open(fullPath + file, "r", encoding = "utf-8") as fReader:
            fileLines += fReader.readlines()[1:] # Skips header lines
    
    dfColumns = ["WordPhrase", "WordType", "Case", "Sentence"]
    dfLines = []
    for line in fileLines:
        if (line.strip() == ""):
            continue
    
        lineSplit = re.split("\t", line.strip().replace("\"\"\"", "\""))
        #if (len(lineSplit) > 4): print(lineSplit) # For debug testing
        dfLines.append(lineSplit)

    extraDF = pd.DataFrame(dfLines, columns = dfColumns)
    return extraDF
    
    
# Returns an filename based on how many 'extra' files are in the directory, and the count of the newest 'extra' file
# path: the path to the disambiguation directory
# word: the hebrew word being worked on
# NOTE: the 'extra' file is denoted by 'נויסף', to allow for the event that we use 'נוסף' in the nakdan
def GetNewNMinFilename(path, word):
    fullPath = path + word + "\\"
    
    # Get list of 'extra' files
    fileList = [f for f in listdir(fullPath) if (isfile(join(fullPath, f)) and "נויסף" + ".csv" in f)]
    
    # Find latest 'extra' file
    newestExtra = max([int(f.split()[2].strip()) for f in fileList]) if (fileList) else 0
    
    return "%s - %d - %s.csv" % (word, max(len(fileList), newestExtra) + 1, "נויסף")


# Creates a .csv file for uploading to Google Sheets, it's extra sentences for tagging. Returns the filename of the file created.
#     Note: Every time this function is called, it will create a new numbered 'extra' file, and will pull sentences from the
#     nmin datafile that haven't been previously used in 'extra' files.
# path: the path to the disambiguation directory
# word: the hebrew word being worked on
# NOTE: the 'extra' file is denoted by 'נויסף', to allow for the event that we use 'נוסף' in the nakdan
# NOTE2: only minority-case sentences work currently
def CreateExtraTaggingSheet(path, word):
    fullPath = path + word + "\\"
    logger.info("Creating an 'extra' taggging sheet in %s" % (fullPath))
    
    # Get the nmin dataframe
    nminDF = GetNMinDataframe(path, word)
    nminDF.drop_duplicates(subset = "Sentence", keep = "first", inplace = True)   
    
    # Remove sentences from existing 'extra' files from the dataframe
    extraDF = LoadExtraFilesIntoDataframe(path, word)
    if (len(extraDF) > 0):
        nminDF = pd.concat([nminDF, extraDF], sort = False)    
    nminDF.drop_duplicates(subset = "Sentence", keep = False, inplace = True)

    # Get dataframe of random samples for each word-type found from nmin files
    randomSamplesDF = GetNMinSampledDataframe(nminDF)

    # Create lines to write to file
    # FORMAT: Correct,Word Type,Word Phrase,Case,Sentence
    logger.info("Generating lines for the 'extra' sheet")
    case = "מיעוט" # Only minority-cases for now
    randomSamplesDF["Line"] = \
        randomSamplesDF.apply(lambda x: " \t%s\t%s\t%s\t%s" % \
                         (x["WordType"], x["WordPhrase"], case, tsg.PossiblyEscapeQuotes(x["Sentence"].strip())), axis = 1)
    rowsToFile = list(randomSamplesDF["Line"])
    
    # Get the filename
    filename = GetNewNMinFilename(path, word)
    
    # For writing the header
    nachon = "נכון"
    kvutzah = "קבוצה"
    tzurat = "צורת מילה"
    yachas = "יחס"
    mishpat = "משפט"

    # Write lines to file
    logger.info("Writing 'extra' sheet to %s" % (filename))
    with open(fullPath + filename, "wb") as fWriter:
        headerLine = "%s?\t%s\t%s\t%s\t%s" % (nachon, tzurat, kvutzah, yachas, mishpat) + "\n"
        fWriter.write(headerLine.encode("utf-8"))
   
        for row in rowsToFile:
            lineToWrite = row + "\n"
            fWriter.write(lineToWrite.encode("utf-8"))
    
    return filename