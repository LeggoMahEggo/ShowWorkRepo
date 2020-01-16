import pandas as pd
import tagshegen_new as tsg
import disautils_new as du
import etagshe as ets
import logging
import sys
import re
import os
from os import listdir
from os.path import isfile, join

# Module that creates scoring sheets for tagged files - both for majority/minority cases and MLP files
# Author - Yehuda Broderick

# Logging object
logger = logging.getLogger(__name__)

# Custom exception for no files found
class NoFilesFoundError(Exception):
    pass


# Creates files that show how well each word-phrase scored for majority/minority case words
# path - the path to the directory
# word - the word that is being worked on
def CreateScoringFiles(path, word):
    fullPath = path + word + "\\"
    logger.info("Creating scoring files in {}".format(fullPath))
    fileList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]
    loYadua = "לא ידוע" # For 'unknown' files
    noisaf = "נויסף" # For 'extra' files
    morphologia = "מורפולוגיה" # For bitmorph files
    
    scoreFiles = [] # For returning created files
    for file in fileList:
        
        # Only .tsv files that are majority/minority cases can be worked with
        if (".tsv" not in file or loYadua in file or morphologia in file or "MLP" in file):
            continue
    
        # A dictionary containing the score of each word group  (אוכל להם, בו אוכל, וכו)
        scoreDict = {}
        logger.info("Processing {}".format(file))
        
        with open(fullPath + file, "r", encoding = "utf-8") as fReader:
    
            skippedHeader = False
            for line in fReader:
            
                if (skippedHeader == False):
                    skippedHeader = True
                    continue
            
                # Get the tagging line, and the symbol for a particular word group
                # symbol - what was marked/unmarked by the tagger
                wholeLine = re.split("\t", line)
                symbol = wholeLine[0].strip()
                wordPhrase = wholeLine[1].strip() if (noisaf not in file) else wholeLine[2].strip() # NEW - 'extra' file has the word-type before the word-phrase
                wordType = "" if (noisaf not in file) else wholeLine[1].strip() # NEW - accounting for 'extra' files
        
                # Add a dictionary as an entry to a dictonary, if the word group does not exist
                # Correct: amount marked correct
                # Incorrect: amount marked incorrect
                # Blank: amount not marked at all
                # Group: total amount of examples that exist
                if (wordPhrase not in scoreDict.keys()):
                    scoreDict[wordPhrase] = {"Correct": 0, "Incorrect": 0, "Blank": 0, "Other": 0, "Phrase": 0, "WordType": ""}

                # '*' is a correct answer, '-' is an incorrect answer, ' ' is blank
                if (symbol == "*"):
                    cat = "Correct" 
                elif (symbol == "-"):
                    cat = "Incorrect"
                elif (symbol == ""):
                    cat = "Blank"
                else: 
                    cat = "Other"
                scoreDict[wordPhrase][cat] += 1
                scoreDict[wordPhrase]["WordType"] = wordType # NEW - for 'extra' files, denotes the word-type of the word-phrase
    
        # Get total examples of each word found in the training datafile
        # List of words being used
        wordPhraseList = list(scoreDict.keys())
    
        # Get the training/nmin datafile loaded into a dataframe
        logger.info("Loading training datafile into memory")
        filterDF = tsg.LoadDatafileIntoDataFrame(path, word, tsg.TRAIN) if (noisaf not in file) else ets.GetNMinDataframe(path, word)
        filterDF = filterDF.applymap(lambda x: x.strip())
        filterDF.drop_duplicates(subset = "Sentence", inplace = True, keep = "first")
    
        # Filter the dataframe to only include the words from wordPhraseList
        filterDF = filterDF[filterDF["WordPhrase"].isin(wordPhraseList)]
        
        # Check to make sure that both training data word-phrases and word-phrases from the tagged sheet match up
        wplLen = len(wordPhraseList)
        twpLen = len(list(filterDF["WordPhrase"].unique()))
        if (wplLen != twpLen):
            raise ValueError("Word-phrase count from {} does not match {}_train.txt word-phrase count:\n".format(file, word) +
                             "{}: {}\n{}_train.txt: {}".format(file, wplLen, word, twpLen))
    
        # Put the total examples of each word into the group dictionary
        wordPhraseDict = dict(filterDF["WordPhrase"].value_counts())
        for i in range(0, len(wordPhraseList)):
            wrd = wordPhraseList[i]
            cnt = wordPhraseDict[wrd]
            scoreDict[wrd]["Phrase"] = cnt

        # Write the score file
        scoreFile = file.replace(".tsv", "") + "_score.csv"
        scoreFiles.append(scoreFile)
        logger.info("Writing results of {} to {}".format(file, scoreFile))
        with open(fullPath + scoreFile, "wb") as fWriter:
            headerLine = "%sWord Phrase,Correct,Incorrect,Blank,Other,Sample Size,Total Examples,Score\n" % ("" if (noisaf not in file) else "Word Type,")
            fWriter.write(headerLine.encode("utf-8"))
    
            for wordPhrase,wpDict in scoreDict.items():
                wType = wpDict["WordType"]
                correct = wpDict["Correct"]
                incorrect = wpDict["Incorrect"]
                blank = wpDict["Blank"]
                other = wpDict["Other"]
                totalExamples = correct + incorrect + blank + other # Total Examples
                score = (correct/totalExamples) * 100 # Final score
                scoreLine = "{}{},{},{},{},{},{},{},{}%\n".format("" if (noisaf not in file) else wType+",",
                                                                wordPhrase, correct, incorrect, blank, other, totalExamples, 
                                                                wpDict["Phrase"], 
                                                                int(score) if float(score).is_integer() else round(score, 2))
                fWriter.write(scoreLine.encode("utf-8"))
    
    # Raise an exception if there are no files to be worked on
    if (len(scoreFiles) == 0):
        raise NoFilesFoundError("No tagged majority/minority sheets for {} were found in {}".format(word, fullPath))
        
    return scoreFiles
    

# Generates a scored file on the various answers marked in MLP tagged sheets
# Returns the filename of the score file generated
# path - the path to the directory
# word - the word that is being checked
def CreateMLPScoringFile(path, word):
    fullPath = path + word + "\\"
    logger.info("Creating MLP scoring file in {}".format(fullPath))
    
    # Put the data from each tagged MLP sheet into a dataframe
    taggedDF = None
    fileList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]

    # Setup the lists as data for the dataframe
    types = []
    answers = []
    sentences = []

    # Loop through each file in the directory
    filesCount = 0
    for file in fileList:
    
        # Only take tagged MLP files
        if (".tsv" in file and "MLP" in file):
            filesCount += 1
            logger.info("Processing {}".format(file))
            
            with open(fullPath + file, "r", encoding = "utf-8") as fReader:
            
                skippedHeader = False
                taggedType = ""
            
                for line in fReader:
                
                    if (skippedHeader == False):
                        skippedHeader = True
                    
                        # Get the word-type from the header
                        headerLine = re.split("\t", line.replace(tsg.BOM, "").strip(), 1)
                        nachon = "נכון" + "?"
                        taggedType = headerLine[0][:-(len(nachon))].strip()
                        continue
                
                    # Skip blank lines (if any)
                    if (len(line.strip()) <= 5):
                        continue
                
                
                    fullLine = re.split("\t", line)
                    answerIndex = 0
                    sentenceIndex = len(fullLine) - 1
                
                    # Answer is the first column, sentence is the last
                    answer = fullLine[answerIndex].strip()
                    sentence = fullLine[sentenceIndex].strip()
                
                    # Append data to the lists. They carry over for every tagged MLP file
                    types.append(taggedType)
                    answers.append(answer)
                    sentences.append(sentence)
       
    # Exit if there were no tagged files to work on
    if (filesCount == 0):
        raise NoFilesFoundError("No tagged MLP files for {} were found in {}".format(word, fullPath))
    
    logger.info("Calculating results")
    # Create the dataframe, and drop duplicates (though there shouldn't be any dropped)
    taggedDF = pd.DataFrame(data = {"Type": types, "Sentence": sentences, "Answer": answers})
    taggedDF.drop_duplicates(subset = "Sentence", inplace = True, keep = "first")

    # Create filtered dataframes by marking. '*' is correct, '-' incorrect, and '' or ' ' is blank
    correctDF = taggedDF[taggedDF["Answer"] == "*"]
    incorrectDF = taggedDF[taggedDF["Answer"] == "-"]
    blankDF = taggedDF[(taggedDF["Answer"] == "") | (taggedDF["Answer"] == " ")]

    # Finally, get a filtered dataframe of all the sentences marked that don't fit into the above categories
    cAnswers = list(correctDF["Answer"])
    icAnswers = list(incorrectDF["Answer"])
    bAnswers = list(blankDF["Answer"])
    otherDF = taggedDF[(~taggedDF["Answer"].isin(cAnswers)) & \
                       (~taggedDF["Answer"].isin(icAnswers)) & \
                       (~taggedDF["Answer"].isin(bAnswers))]

    # For every type, get their appropriate data
    mlpScoreLines = []
    mlpTypes = taggedDF["Type"].unique()
    for mlpType in mlpTypes:
        cTotal = len(taggedDF[taggedDF["Type"] == mlpType]) # Total amount of examples in the file
    
        # Total examples marked correct
        cCorrect = len(correctDF[correctDF["Type"] == mlpType])
        cCorrectPercent = du.GetPercentage(cCorrect, cTotal)
    
        # Total examples marked incorrect
        cIncorrect = len(incorrectDF[incorrectDF["Type"] == mlpType])
        cIncorrectPercent = du.GetPercentage(cIncorrect, cTotal)
    
        # Total examples left blank
        cBlank = len(blankDF[blankDF["Type"] == mlpType])
        cBlankPercent = du.GetPercentage(cBlank, cTotal)
    
        # Total examples marked in other ways
        cOther = len(otherDF[otherDF["Type"] == mlpType])
        cOtherPercent = du.GetPercentage(cOther, cTotal)
    
        # Add the lines to a list to later write to a file
        line = "MLP Tagged File - {}".format(mlpType)
        mlpScoreLines.append("{}\n{}".format(line, "-" * len(line)))
        mlpScoreLines.append("Correct: {} - {}%".format(cCorrect, cCorrectPercent))
        mlpScoreLines.append("Incorrect: {} - {}%".format(cIncorrect, cIncorrectPercent))
        mlpScoreLines.append("Blank: {} - {}%".format(cBlank, cBlankPercent))
        mlpScoreLines.append("Other: {} - {}%".format(cOther, cOtherPercent))
        mlpScoreLines.append("Total: {}".format(cTotal))
        mlpScoreLines.append("")
    
        # Add extra entries for the 'other' markings, if applicable
        if (cOther > 0):
            line = "Breakdown of other inputs"
            mlpScoreLines.append("{}\n{}".format(line, "-" * len(line)))
        
            otherDFByType = otherDF[otherDF["Type"] == mlpType] # Filter the 'other answers' dataframe by the current type
            otherAnswers = otherDFByType["Answer"].unique() # Get a list of every unique answer in the dataframe
        
            for answer in otherAnswers:         
                cAnswerAmount = len(otherDFByType[otherDFByType == answer]) # Total times specific answer appears
                mlpScoreLines.append("{}: {}".format(answer, cAnswerAmount))
        
    
        mlpScoreLines.append("\n\n")

    # Write the lines to a file
    mlpScoreFile = word + "_MLP_score.txt"
    logger.info("Writing results to {}".format(mlpScoreFile))
    with open(fullPath + mlpScoreFile, "wb") as fWriter:
    
        for line in mlpScoreLines:
            cLine = line + "\n"
            fWriter.write(cLine.encode("utf-8"))
    
    return mlpScoreFile