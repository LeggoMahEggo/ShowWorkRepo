import pandas as pd
import disautils_new as du
import logging
import sys
import os
import re
from os import listdir
from os.path import isfile, join
from tagshegen_new import BOM, TRAIN, TEST, LoadDatafileIntoDataFrame

# Module for creating precious/unprecious metal sheets
# Author: Yehuda Broderick

# Logging object
logger = logging.getLogger(__name__)

# Custom exception for no files found
class NoFilesFoundError(Exception):
    pass


# Takes a precious/unprecious metals file and loads it into a dataframe
# path: the path to the main disambiguation directory
# word: the hebrew word of which to load the precious/unprecious into a dataframe
# isUnPrecious(optional): whether to load the unprecious metals file or not (default False)
def LoadMetalsFileIntoDataframe(path, word, isUnPrecious = False):
    fullPath = path + word + "\\"
    metalFile = word + "_%sprecious_metals.csv" % ("" if (not isUnPrecious) else "un")
    
    with open(fullPath + metalFile, "r", encoding = "utf-8") as fReader:
    
        gotHeader = False
        dfColumns = []
        dfRows = []
        for line in fReader:
            if (not gotHeader):
                gotHeader = True
                dfColumns = re.split("\t", line.strip().replace(BOM, ""))
                continue
        
            if (line.strip() == ""):
                continue
            
            dfRows.append(re.split("\t", line))
        
    metalDF = pd.DataFrame(dfRows, columns = dfColumns)
    metalDF = metalDF.applymap(lambda x: x.strip()) # Remove unnecessary whitespace
    
    return metalDF


# Creates the precious metals file from the tagged sheet of a particular word (categories are GOLD, SILVER, BRONZE, and BRASS)
# path - the path to the sheets
# word - the word that is being worked on
def CreatePreciousMetalsFile(path, word):
    fullPath = path + word + "\\"
    logger.info("Creating precious metals file in {}".format(fullPath))
    haRov = "הרוב"
    miut = "מיעוט"
    noisaf = "נויסף" # For 'extra' files (for now just minority-cases)
    shonot = "שונות" # For the 'check' cases (not fed through MLP)
    loYadua = "לא ידוע"
    morphologia = "מורפולוגיה" # morphology

    fileList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]

    # List of dictionaries for the lines of the precious metals file
    pmLines = []

    # GOLD (sentences that were tagged correct, of majority/minority cases)
    # Loop through files and read them in
    logger.info("Evaluating gold sentences")
    for file in fileList:

        # Only take tab-separated value files, and majority/minority cases (not morphology files)
        if (".tsv" not in file):
            continue
        if (haRov not in file and miut not in file):
            continue
        logger.info("Processing {}".format(file))
        
        with open(fullPath + file, "r", encoding = "utf-8") as fReader:
            wordType = ""
            skippedHeader = False
            
            for line in fReader:

                # Get the word type from the header
                if (skippedHeader == False):
                    skippedHeader = True
                    linePart = re.split("\t", line.replace(BOM, ""), 1)
                    wordType = linePart[0].replace("נכון", "").replace("?", "").strip() # (non-'extra' only) - Remove junk data
                    continue

                # Only pull in samples that were marked as correct, ie '*'
                linePart = re.split("\t", line)
                if ("*" in linePart[0].strip()):
                    wordType = wordType
                    wordPhrase = linePart[1].strip()
                    sentence = linePart[-1].strip()
                    case = "maj" if (haRov in file) else ("chk" if (shonot in file) else "min") #add in 'chk' for check cases, add later
                    pmDict = { "WordPhrase": wordPhrase, "WordType": wordType, "Case": case, "Sentence": sentence, 
                                  "Metal": "Gold"}
                    pmLines.append(pmDict)

    # Raise an exception if no tagged majority/minority files were found                
    if (len(pmLines) == 0):
        raise NoFilesFoundError("No tagged majority/minority sheets (.tsv files) for {} were found in {}".format(word, fullPath))
    
    # SILVER (sentences of majority-case words whose word-phrases scored 100%, and were unsampled)
    logger.info("Evaluating silver sentences")
    majFullScoreList = []

    # Loop through files and read them in
    scoreFileCount = 0
    for file in fileList:
    
        # Only take the majority-case score files
        if (".xlsx" in file):
            continue
        if ("score" not in file):
            continue
        if (haRov not in file):
            continue

        # Only get the word-phrases that scored 100%
        logger.info("Processing {}".format(file))
        scoreFileCount += 1
        with open(fullPath + file, "r", encoding = "utf-8") as fReader:
            skippedHeader = False
            
            for line in fReader:
                
                if (skippedHeader == False):
                    skippedHeader = True
                    continue
                    
                # Get word and score
                linePart = re.split(",", line)
                wordPhrase = linePart[0].strip()

                # Move to next line if the word does not exist for some reason
                if (wordPhrase == ""):
                    continue

                oneHundred = linePart[len(linePart) - 1].replace("%", "")

                # Add word to list if the score is 100%
                if (int(oneHundred) == 100):
                    majFullScoreList.append(wordPhrase)
    
    # Raise an exception if no majority-case score files were found (that's what is needed for silver)
    if (scoreFileCount == 0):
        raise NoFilesFoundError("No scored majority-case files for {} were found in {}".format(word, fullPath))
   
    # Create Silver DataFrame from the training datafile
    logger.info("Loading training datafile into memory")
    silverDF = LoadDatafileIntoDataFrame(path, word, TRAIN)
    silverDF = silverDF.applymap(lambda x: x.strip())
    silverDF = silverDF[silverDF["WordPhrase"].isin(majFullScoreList)] # Only get rows that are majority-case and have 100% score
    silverDF.drop_duplicates(subset = "Sentence", keep="first", inplace=True)

    # Create a Gold DataFrame from preloaded Gold data, so that the Gold sentences can be removed from the Silver dataframe
    # Add sentences to a list that were sampled, and in the "MAJORITY" tab/sheet file, and the words have 100% score
    wordPhrases = []
    wordTypes = []
    sentences = []
    for lineDict in pmLines:

        # We only need to deal with the 100% score majority words, since that's what we need from the silver part
        wordPhrase = lineDict["WordPhrase"]
        if (wordPhrase in majFullScoreList):

            # Get the other data, we are making a virtual copy of a Training DataFrame
            wordType = lineDict["WordType"]
            sentence = lineDict["Sentence"]

            wordPhrases.append(wordPhrase)
            wordTypes.append(wordType)
            sentences.append(sentence.strip())
    goldRemovalDF = pd.DataFrame(data={"WordType": wordTypes, "WordPhrase": wordPhrases, "Sentence": sentences})

    # Combine the gold and silver dataframes
    electrumDF = pd.concat([silverDF, goldRemovalDF])

    # Get rid of the 'gold' dataframe rows
    electrumDF.drop_duplicates(subset = "Sentence", keep = False, inplace = True)

    # Sort the rows
    electrumDF = electrumDF.sort_values(["WordType", "WordPhrase"])

    # Add the 'silver' dataframe's rows to the list for writing data (pmLines)
    # Clear lists
    wordTypes[:] = []
    wordPhrases[:] = []
    sentences[:] = []
    wordTypes = list(electrumDF["WordType"])
    wordPhrases = list(electrumDF["WordPhrase"])
    sentences = list(electrumDF["Sentence"])

    for i in range(0, len(electrumDF)):
        wordType = wordTypes[i].strip()
        wordPhrase = wordPhrases[i]
        sentence = sentences[i]
        case = "maj"
        pmDict = { "WordPhrase": wordPhrase, "WordType": wordType, "Case": case, "Sentence": sentence, "Metal": "Silver"}
        pmLines.append(pmDict)

    # BRONZE (sentences from unknown data that were marked as being a specific word-type)
    # First, find which words are which cases
    logger.info("Evaluating bronze sentences")
    logger.info("Loading cases file into memory")
    casesDict = du.GetWordCases(path, word)
    unkTaggedSheetCount = 0
    
    for file in fileList:
        
        # Only take the 'unknown' tagged file
        if (".tsv" not in file):
            continue
        if (loYadua not in file):
            continue
        
        logger.info("Processing {}".format(file))
        with open(fullPath + file, "r", encoding = "utf-8") as fReader:
            unkTaggedSheetCount += 1
            skippedHeader = False
            bronzeList = []
            sentenceIndex = -1
            
            for line in fReader:

                # Get info from header
                if (skippedHeader == False):
                    skippedHeader = True

                    headerInfo = re.split("\t", line.replace(BOM, ""))
                    sentenceIndex = len(headerInfo) - 1 # Get the index for the sentence part of the file
            
                    # Add the various word types to a list
                    for j in range(0, sentenceIndex):
                        val = headerInfo[j].replace("?", "").strip()
                        bronzeList.append(val)
                    continue

                # Get the data from the line
                unknownLine = re.split("\t", line)
                typeIndex = -1

                # Find the index of the correct type marked
                for j in range(0, sentenceIndex):
                    if ("*" in unknownLine[j]):
                        typeIndex = j
                        break

                # Move on to next statement if *nothing* was marked correct
                if (typeIndex == -1):
                    continue

                wordType = bronzeList[typeIndex] # Get the word's type
                wordPhrase = word # Since the nekudot is unknown, the word is the default unmarked version
                sentence = unknownLine[sentenceIndex].strip() # Get the sentence part of the sentence ;)
                case = casesDict[wordType]
                pmDict = { "WordPhrase": wordPhrase, "WordType": wordType, "Case": case, "Sentence": sentence, 
                              "Metal": "Bronze"}
                pmLines.append(pmDict)
            bronzeList[:] = []  
    
    # Raise an exception if no unknown tagged sheet files were found                
    if (unkTaggedSheetCount == 0):
        raise NoFilesFoundError("No 'unknown' tagged sheets for {} were found in {}".format(word, fullPath))
        
    # BRASS (sentences from morphology data marked correct)
    # NOTE: No morphology tagged sheets in the directory will not raise an exception
    processBrass = False
    for file in fileList:
        
        if (".tsv" not in file):
            continue
        if (morphologia not in file):
            continue
        
        if (not processBrass):
            processBrass == True
            logger.info("Evaluating brass sentences")
        
        logger.info("Processing {}".format(file))
        with open(fullPath + file, "r", encoding = "utf-8") as fReader:
            skippedHeader = False
            headerType = ""
            answerIndex = 0
            sentenceIndex = 2
            
            for line in fReader:

                # Get info from header
                if (skippedHeader == False):
                    skippedHeader = True
                    headerInfo = re.split("\t", line.replace(BOM, ""))
                    headerType = headerInfo[0].strip()[:-1].replace("נכון", "").strip()    
                    continue

                # Get the data from the line
                morphLine = re.split("\t", line)

                # Move on to next statement if the example wasn't marked correct
                if (morphLine[answerIndex].strip() != "*"):
                    continue

                wordType = headerType # Get the word's type
                wordPhrase = word # Since the nekudot is unsure, the word is the default unmarked version
                sentence = morphLine[sentenceIndex].strip() # Get the sentence part of the sentence ;)
                    
                # Morphology files might not have the word-types correctly listed
                # Therefore, there must be appropriate checks
                if (wordType not in casesDict.keys()):
                    typesList = list(casesDict)
                    alignedType = False
                    
                    # First method: wordType is in the same string as the correct
                    #     word-type
                    # Second method: removing metegs makes the strings equivalent
                    compMorphType = du.RemoveMetegLetter(wordType)
                    for j in range(0, len(typesList)):
                        cType = typesList[j]
                        compCType = du.RemoveMetegLetter(cType)
                            
                        if (wordType in cType or compMorphType == compCType):
                            case = casesDict[cType]
                            alignedType = True
                            break
                    if (alignedType == False):
                        raise ValueError("Unable to align the word-type {} with its ".format(wordType) +
                                         "equivalent in {}_cases.csv from {} in {}".format(word, file, fullPath))
                else:
                    case = casesDict[wordType]
                    
                pmDict = { "WordPhrase": wordPhrase, "WordType": wordType, "Case": case, "Sentence": sentence, 
                              "Metal": "Brass"}
                pmLines.append(pmDict)
    
    # TIN - (currently) minority-case words from nmin datafiles (the 'נויסף' files)
    convertCases = {haRov: "maj", miut: "min"} # For converting the cases of the 'extra' files to the format used in these scripts
    
    # Loop through files and read them in
    logger.info("Evaluating tin sentences")
    for file in fileList:
        if (".tsv" not in file):
            continue
        if (noisaf not in file):
            continue
        logger.info("Processing %s" % (file))
        
        with open(fullPath + file, "r", encoding = "utf-8") as fReader:
            wordType = ""
            skippedHeader = False
            
            for line in fReader:

                # Get the word type from the header
                if (skippedHeader == False):
                    skippedHeader = True
                    continue

                # Only pull in samples that were marked as correct, ie '*'
                linePart = re.split("\t", line)
                if ("*" in linePart[0].strip()):
                    wordType = linePart[1].strip()
                    wordPhrase = linePart[2].strip()
                    sentence = linePart[-1].strip()
                    case = convertCases[linePart[3].strip()]
                    pmDict = { "WordPhrase": wordPhrase, "WordType": wordType, "Case": case, "Sentence": sentence, 
                                  "Metal": "Tin"}
                    pmLines.append(pmDict)

    # Create the precious metal file
    pmFile = "{}_precious_metals.csv".format(word)
    logger.info("Writing results to {}".format(pmFile))
    with open(fullPath + pmFile, "wb") as fWriter:

        # Create the header
        fWriter.write("Word Phrase\tWord Type\tMetal\tCase\tSentence\n".encode("utf-8"))

        # Write lines from pmLines list
        for i in range(0, len(pmLines)):
            fWriter.write("{}\t{}\t{}\t{}\t{}\n".format(
                pmLines[i]["WordPhrase"], pmLines[i]["WordType"], pmLines[i]["Metal"], pmLines[i]["Case"], 
                pmLines[i]["Sentence"]).encode("utf-8"))
    

    return pmFile


# Creates a file in the same format as the precious metals, but from unsampled unknown sentences, and
#     unsampled majority-case words that scored less than 100%
# path - the path to the sheets
# word - the word that is being worked on
def CreateUnPreciousMetalsFile(path, word):
    fullPath = path + word + "\\" # The path to the directory of the word
    haRov = "הרוב" # majority-case
    logger.info("Creating an unprecious metals file in {}".format(fullPath))

    # Get a list of files in the directory    
    fileList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]

    # Get a list of majority cases that did NOT get 100%
    majNotFullScoreList = []

    scoreFileCount = 0
    for file in fileList:

        # Only take the majority-case score files
        if ("score" in file and haRov in file and ".xlsx" not in file):
            logger.info("Processing {}".format(file))
            
            # Only get the words that scored 100%
            scoreFileCount += 1
            with open(fullPath + file, "r", encoding = "utf-8") as fReader:
                
                skippedHeader = False
                for line in fReader:

                    # Skip header
                    if (skippedHeader == False):
                        skippedHeader = True
                        continue

                    # Get word and score
                    linePart = re.split(",", line)
                    wordPhrase = linePart[0].strip()

                    # Move to next line if the word does not exist for some reason
                    if (wordPhrase == ""):
                        continue

                    oneHundred = linePart[len(linePart) - 1].replace("%", "")

                    # Add word to list if the score is less than 100%
                    if (int(oneHundred) < 100):
                        majNotFullScoreList.append(wordPhrase)
                        
    # Raise an exception if no majority-case score files were found
    if (scoreFileCount == 0):
        raise NoFilesFoundError("No scored majority-case files for {} were found in {}".format(word, fullPath))
        
    # Get the list of sentences in the precious metals file
    pmFile = "{}_precious_metals.csv".format(word)
    logger.info("Loading {} into memory".format(pmFile))
    if (not os.path.isfile(fullPath + pmFile)):
        raise FileNotFoundError("{} was not found in {}".format(pmFile, fullPath))
    
    pmSentences = []
    with open(fullPath + pmFile, "r", encoding = "utf-8") as fReader:

        skippedHeader = False
        for line in fReader:

            if (skippedHeader == False):
                skippedHeader = True
                continue

            fullLine = re.split("\t", line)
            sentenceIndex = len(fullLine) - 1
            pmSentences.append(fullLine[sentenceIndex].strip())

    # Get the data from the training and testing data files
    # Majority sentences
    logger.info("Loading training datafile into memory")
    trainDF = LoadDatafileIntoDataFrame(path, word, TRAIN)
    trainDF = trainDF.applymap(lambda x: x.strip())
    trainDF.drop_duplicates(subset = "Sentence", inplace = True, keep = "first")

    # Unknown sentences
    logger.info("Loading testing datafile into memory")
    testDF = LoadDatafileIntoDataFrame(path, word, TEST)
    testDF = testDF.applymap(lambda x: x.strip())
    testDF.drop_duplicates(subset = "Sentence", inplace = True, keep = "first")

    # Add an extra column to the dataframes
    trainDF["Case"] = "maj"
    testDF["Case"] = "unk"

    # Filter the training data by sentences that are not silver (ie did NOT get 100% score), AND unsampled
    trainDF = trainDF[trainDF["WordPhrase"].isin(majNotFullScoreList)]
    trainDF = trainDF[~trainDF["Sentence"].isin(pmSentences)]
    trainDF = trainDF.sort_values(by = ["WordPhrase"], ascending = True)

    # Filter the unknown data by sentences that do NOT appear in the precious metals file (by definition they are unsampled)
    testDF = testDF[~testDF["Sentence"].isin(pmSentences)]

    # Concatenate the two dataframes together
    leadDF = pd.concat([trainDF, testDF])

    # Put the resulting data into lists
    words = leadDF["WordPhrase"].tolist()
    types = leadDF["WordType"].tolist()
    sentences = leadDF["Sentence"].tolist()
    cases = leadDF["Case"].tolist()

    # Write the data to a file
    upmFile = "{}_unprecious_metals.csv".format(word)
    logger.info("Writing results to {}".format(upmFile))
    with open(fullPath + upmFile, "wb") as fWriter:
    
        # Write the header line
        fWriter.write("Word Phrase\tWord Type\tMetal\tCase\tSentence\n".encode("utf-8"))

        # Write the lines based on the lists above
        for i in range(0, len(leadDF)):

            # Set the word type based on if it's an unknown word
            wordPhrase = word if (words[i] == "Unknown") else words[i]
        
            # Write the line
            fWriter.write("{}\t{}\t{}\t{}\t{}\n".format(wordPhrase, types[i], "Lead", cases[i], sentences[i]).encode("utf-8"))
    return upmFile