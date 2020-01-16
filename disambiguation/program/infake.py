import random as rd
import re
import os
import logging
from disautils_new import CheckFileByArch32

# Module for inserting fake word-types and sentences into the cases and training datafile (secret option)
# Author: Yehuda Broderick

# Logging object
logger = logging.getLogger(__name__)

# Recursive. Keeps calling itself until it generates a hebrew word with random nikud placed upon it that doesn't
#     appear in 'notAllowedList'
# word: the hebrew word to check/return
# nikudList: the list of hebrew nikud to randomly put on the word
# notAllowedList: a list of hebrew words with unique nikud that the word returned cannot be equal to
def AddNikudToWord(word, nikudList, notAllowedList):
    if (word not in notAllowedList):
        return word
    
    # Create list of random nikud for the word
    randomList = [nikudList[rd.randint(0, 13)] for i in range(0, len(word))]
    
    # Strip OG word of nikud
    word = [letter for i,letter in enumerate(word) if (i % 2 == 0)]
    
    # Make new word with nikud
    nikudWord = ""
    for char in zip(word, randomList):
        nikudWord += char[0] + char[1]
        
    return AddNikudToWord(nikudWord, nikudList, notAllowedList)
    
    
# Takes a hebrew word and returns it with random nikud placed upon it. For generating 'fake' word-types
# word: the hebrew word to put nikud on
# notAllowedList: a list of hebrew words with unique nikud that the word returned cannot be equal to
def PutRandomNikudOnHebrewWord(word, notAllowedList = []):
    nikudList = []

    # First, get nikud
    nikudList = [u"{}".format(chr(int("0x5B%s" % (str(hex(i)[-1:])), 0))) for i in range(0, 14)]
    
    # Second, strip it of any nikud already on the word (if for whatever reason it happens to be there)
    word = "".join([letter for letter in word if (letter not in nikudList)])
    
    # Third, create list of random nikud for the word
    randomList = [nikudList[rd.randint(0, 13)] for i in range(0, len(word))]
    
    # Finally, return a string with the random nikud after every hebrew letter (recursive, to prevent existing word from coming up)
    nikudWord = ""
    for char in zip(word, randomList):
        nikudWord += char[0] + char[1]
    
    return AddNikudToWord(nikudWord, nikudList, notAllowedList)
    

# Inserts a 'fake' (ie not originally present) word-type and sentence to the cases and training datafile
# path: the path to the disambiguation directory
# word: the hebrew word being worked on
def InsertFakeTypeSentence(path, word):
    fullPath = path + word + "\\"
    casesFile = word + "_cases.csv"
    trainFile = word + "_train.txt"
    logger.info("Inserting fake word-type and sentence into the cases and training datafile in %s" % (fullPath))
    
    # Check to make sure the required files exist
    if (not os.path.exists(fullPath + casesFile)):
        raise FileNotFoundError("%s was not found in %s" % (casesFile, fullPath))
    if (not os.path.exists(fullPath + trainFile)):
        raise FileNotFoundError("%s was not found in %s" % (trainFile, fullPath))
    
    # Check to make sure that the file is under 750 mb
    CheckFileByArch32(path, word, trainFile) 
    
    # Load existing word-types into memory
    notAllowedWords = []
    logger.info("Loading existing word-types into memory")
    with open(fullPath + casesFile, "r", encoding = "utf-8") as fReader:
        skippedHeader = False
    
        for line in fReader:
            if (not skippedHeader):
                skippedHeader = True
                continue
            
            separatedLine = re.split("\t", line.strip(), 1)
            notAllowedTypes = re.split("\\\\", separatedLine[0].strip())
        
            for notAllowedType in notAllowedTypes:
                notAllowedWords.append(notAllowedType.strip())

    # Create fake word-type
    logger.info("Creating fake word-type")
    fakeType = PutRandomNikudOnHebrewWord(word, notAllowedWords)

    # Update cases file with fake type
    logger.info("Inserting fake word-type into %s" % (casesFile))
    with open(fullPath + casesFile, "ab") as fAppender:
        lineToWrite = "%s\t%d\t%s" % (fakeType, 1, "min") + "\n"
        fAppender.write(lineToWrite.encode("utf-8"))

    # Load training datafile into memory
    fileLines = []
    logger.info("Loading %s into memory" % (trainFile))
    with open(fullPath + trainFile, "r", encoding = "utf-8") as fReader:
    
        for line in fReader:
            fileLines.append(line.strip())

    # Update training file
    logger.info("Inserting fake sentence into %s" % (trainFile))
    with open(fullPath + trainFile, "wb") as fWriter:
        fakePhrase = "אני" + " " + word
        doNotTag = "אין לתייג, ויש להתעלם ממשפט זו"
        fakeLine = "%s\t\t %s \\\\ %s \\\\ %s" % (fakeType, doNotTag, fakePhrase, doNotTag)
        fileLines.insert(2, fakeLine)
    
        # Rewrite the data to the training datafile
        for i,line in enumerate(fileLines):
            if (i + 1 < len(fileLines)):
                line += "\n"
            
            fWriter.write(line.encode("utf-8"))

    logger.info("Fake word-type created: %s" % (fakeType))
    logger.info("Fake line inserted into the cases file: %s" % (lineToWrite.strip()))
    logger.info("Fake line inserted into the training datafile: %s" % (fakeLine))
    return {"fakeType": fakeType, "lineToWrite": lineToWrite.strip(), "fakeLine": fakeLine}