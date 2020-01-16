import pandas as pd
import re
import logging
import os
from os import listdir
from os.path import isfile, join
from tagshegen_new import BOM, ALL, GetDataFrameDictionary
from etagshe import GetNMinDataframe, GetNMinFilesByWord
from premshe_new import LoadMetalsFileIntoDataframe
from disautils_new import GetPercentage, GetWordCases, RemoveMetegLetter, OrderSheetByType

# Module for generating various reports on the disambiguation files
# Author: Yehuda Broderick

# Logging object
logger = logging.getLogger(__name__)

# Custom exception for no files found
class NoFilesFoundError(Exception):
    pass
    
    
# Returns false if the word (אהבה, אוכל, וכו) is in every array element (ie stuff between backslashes).
#     Otherwise, if at least 1 element is missing the word, return true
# NOTE: This is required for the 'CreateDatafileReport' function
# arr - the array of words inside the backslashes
# word - the word we are working with
def OneElementIncorrect(arr, word):
    
    amountCorrect = 0
    for element in arr:
        if (word in element):
            amountCorrect += 1
        
    if (amountCorrect < len(arr)):
        return True
    else:
        return False

# Creates a file that shows data on and problems with datafiles
# path - the path to the particular word's folder
# word - the word we are working on
# datafileTypes: what type(s) of tagging sheet(s) to generate. Format is as follows:
#     TRAIN: generate tagging sheets from the training datafile
#     TEST: generate a tagging sheet from the testing datafile
#     BITMORPH: generate tagging sheets from the bitmorph datafile
#     MLP: generate tagging sheets from the MLP datafile
# Use like so: [datafile type 1] | [datafile type 2] | .... [datafile type n]
# genProblemFile (optional): generates a second file with all the problem lines listed (*not* duplicates). Default False
def CreateDatafileReport(path, word, datafileTypes, genProblemFile = False):
    fullPath = path + word + "\\"
    logger.info("Creating a datafile report in {}".format(fullPath))
    logger.info("Loading dataframes into memory")
    dfDict = GetDataFrameDictionary(path, word, datafileTypes) # Dictionary of dataframes loaded into memory
    dfLineCountBefore = {} # For keeping track of the total line count before duplicate lines are dropped
    dfSentenceDict = {} # For keeping track of sentences before duplicate lines are dropped
    
    if (len(dfDict) == 0):
        errMsg = "No dataframes could be loaded"
        logger.info(errMsg)
        raise ValueError(errMsg)
    
    # Special code to handle an nmin dataframe
    if ("nminDF" in dfDict.keys()):
        nminDF = dfDict["nminDF"]
        nminWTypes = list(nminDF["WordType"].unique())
        
        nminDFList = []
        for wordType in nminWTypes:
            wtDFName = "nminDF - %s" % (wordType)
            wtDF = nminDF[nminDF["WordType"] == wordType].copy()
            dfDict[wtDFName] = wtDF
        del dfDict["nminDF"]    
    
    # Get rid of whitespace in the dataframes and drop duplicate rows (but first get a count of total rows before)
    for dfName, df in dfDict.items():
        dfDict[dfName] = df.applymap(lambda x: x.strip())
        dfLineCountBefore[dfName] = len(df)
        dfSentenceDict[dfName] = list(df["Sentence"])
        dfDict[dfName].drop_duplicates(subset = "Sentence", inplace = True, keep = "first")
                
    noBackslashesList = [] # List of all the sentences that are missing backslashes
    extraBackslashesList = [] # List of all the sentences that have extraneous backslashes
    notInBackslashesList = [] # List of all the sentences that have the wrong word
    resultsList = [] # List of the lines to write to the report file
    problemsList = [] # List of problem lines to write to a file (only if genProblemFile is 'True')
    
    # dfIndex is for getting the count when iterating over dfDict
    for dfIndex, (dfName, df) in enumerate(dfDict.items()):
        logger.info("Processing %s dataframe", dfName.replace("DF", ""))
        
        # The total problematic lines in the file
        problemCount = 0 
    
        # Loop through the sentences of the dataframe and get problems
        dfSentenceList = dfSentenceDict[dfName]
        for i, line in enumerate(dfSentenceList):
                
            # Problem lines are lines that are:
            #   a) missing backslashes
            #   b) have extra (more than 2 pairs) backslashes (skipping lines with multiple examples of the word)
            #   c) do not have the word inbetween the backslashes
        
            # Get the word-phrases between backslashes (get odd elements in case there are 2+ word-phrases)
            initialSearch = re.findall(R"(?<=\\\\)(.*?)(?=\\\\)", line)
            wordsBetween = [item for i, item in enumerate(initialSearch) if (i == 0 or i % 2 == 0)]
        
            # Check if the sentence has a problem
            hasMissingBackslashes = (line.count("\\\\") == 0)
            hasExtraBackslashes = (len(wordsBetween) < line.count("\\\\") / 2)
            hasWrongWord = (hasMissingBackslashes == False and hasExtraBackslashes == False 
                            and OneElementIncorrect(wordsBetween, word) == True)
        
            # Check if the sentence has a 'problem'
            if (hasMissingBackslashes == True or hasExtraBackslashes == True or hasWrongWord == True):
                problemCount += 1
        
            # Missing backslashes, extra backslashes, or not inside backslashes
            if (hasMissingBackslashes == True):
                noBackslashesList.append(line)
            elif (hasExtraBackslashes == True):
                extraBackslashesList.append(line)
            elif (hasWrongWord == True):
                notInBackslashesList.append(line)
        
        # Put the line numbers and sentences of the missing, extra, and not inside backslashes sentences, 
        #     and drop the duplicates.
        # Also, get a before and after count for displaying the correct amount of problem lines (of each type)

        # Missing backslashes
        # Create a dataframe
        noDF = pd.DataFrame(data = {"Sentence": noBackslashesList})
    
        # Drop duplicate sentences, and get the before and after line count
        noBSLCBefore = len(noDF)
        noDF.drop_duplicates(subset = "Sentence", inplace = True, keep = "first")
        noBSLCAfter = len(noDF)
    
    
        # Extra backslashes
        # Create a dataframe
        extraDF = pd.DataFrame(data = {"Sentence": extraBackslashesList})
    
        # Drop duplicate sentences, and get the before and after count
        extraBSLCBefore = len(extraDF)
        extraDF.drop_duplicates(subset = "Sentence", inplace = True, keep = "first")
        extraBSLCAfter = len(extraDF)
    
    
        # Not inside backslashes
        # Create a dataframe
        notInDF = pd.DataFrame(data = {"Sentence": notInBackslashesList})
    
        # Drop duplicate sentences, and get the before and after count
        notInBSLCBefore = len(notInDF)
        notInDF.drop_duplicates(subset = "Sentence", inplace = True, keep = "first")
        notInBSLCAfter = len(notInDF)
    
        # Get the final count of the problem lines
        noFinalDroppedCount = noBSLCBefore - noBSLCAfter
        extraFinalDroppedCount = extraBSLCBefore - extraBSLCAfter
        notInFinalDroppedCount = notInBSLCBefore - notInBSLCAfter
    
        # Calculate all sorts of different info
        finalLineCount = len(df) # Final amount of lines in the file (after duplicates)
        totalDuplicateProblemsDropped = noFinalDroppedCount + extraFinalDroppedCount + notInFinalDroppedCount
        finalProblemCount = problemCount - totalDuplicateProblemsDropped
    
        # Amount of each type of specific 'problem' (after duplicates)
        noBackslashesLineCount = noBSLCAfter
        extraBackslashesLineCount = extraBSLCAfter
        notInBackslashesLineCount = notInBSLCAfter
    
        # Get the percentages of the rows dropped, problem count, and integrity (ie how
        #     'healthy' the file is)
        pRowsDropped = GetPercentage(dfLineCountBefore[dfName] - finalLineCount, dfLineCountBefore[dfName])
        pProblemsPercent = GetPercentage(finalProblemCount, finalLineCount)
        health = round(100 - pProblemsPercent - pRowsDropped, 2)    

        # Potential differences between the training datafile word-types and the MLP datafile word-types
        #     may arise, hence this section
        if (dfName == "mlpDF"):
        
            # Check the word-types for issues (doesn't subtract from the integrity, just reports on them)
            logger.info("Loading cases file into memory")
            casesDict = GetWordCases(path, word)
            wordTypeList = df["WordType"].unique()
            wordTypeResultsList = []
    
            for wordType in wordTypeList:        
                if (wordType not in casesDict.keys()):
                    #casesTypeList = list(casesDict)
                        
                    # First method: _type is in the same string as the correct word-type
                    # Second method: removing metegs makes the strings equivalent
                    compMorphType = RemoveMetegLetter(wordType)
                    for casesType in casesDict.keys():
                        compCType = RemoveMetegLetter(casesType)
                            
                        if (wordType in casesType or compMorphType == compCType):
                            wordTypeResultsList.append("{} appears in this file, equivalent to {} in the cases file".format(wordType, casesType))
                            break
                else:
                    wordTypeResultsList.append("{} appears in this file and the cases file".format(wordType))        
              
        # Report on lines in the file
        possibleFilename = {"trainDF": "{}_train.txt".format(word), 
                            "testDF": "{}_test.txt".format(word), 
                            "bitmorphDF": "{}_BitMorph.txt".format(word), 
                            "mlpDF": "{}_test_MLP.txt".format(word)}
        nminFilename = "OutputNikudSearch-%s.txt"
        fileName = possibleFilename.get(dfName, nminFilename % (dfName.replace("nminDF - ", "")))
        resultsList.append("{}\n{}".format(fileName, "-" * len(fileName)))
        resultsList.append("Lines: {} (excluding blank lines)".format(dfLineCountBefore[dfName]))
        resultsList.append("Duplicate lines dropped: {}".format(dfLineCountBefore[dfName] - finalLineCount))
        finalLine = "Percentage lines dropped: {}%".format(pRowsDropped)
        resultsList.append(finalLine)
        resultsList.append("-" * len(finalLine))

        # Report specificially on problems
        resultsList.append("Problems (after dropped lines): {}".format(finalProblemCount))
        resultsList.append("Duplicate problem lines dropped: {}".format(totalDuplicateProblemsDropped))
        resultsList.append("Lines missing backslashes: {}".format(noBackslashesLineCount))
        resultsList.append("Lines with extra backslashes: {}".format(extraBackslashesLineCount))
        resultsList.append("Lines with the wrong word inside backslashes: {}".format(notInBackslashesLineCount))
        finalLine = "Percentage problem lines: {}%".format(pProblemsPercent)
        resultsList.append(finalLine)
        resultsList.append("-" * len(finalLine))
    
        # File integrity
        resultsList.append(fileName + " health: {}%".format(health))
                
        # Add extra lines if creating the MLP file
        if (dfName == "mlpDF"):
            finalLine = "\n\nAdditional (potential) issues (non-affecting integrity)"
            resultsList.append(finalLine)
            resultsList.append("-" * len(finalLine.strip()))
            
            for line in wordTypeResultsList:
                resultsList.append(line)
        
        # Add lines breaks/dividers if the current report is not the last one to be printed
        if (dfIndex < len(dfDict) - 1):
            resultsList.append("\n{}\n".format("=" * 45))
        
        # Add problem lines to list
        if (genProblemFile == True):
            problemsList.append("{}\n{}\n".format(fileName, "-" * len(fileName)))
            
            # No backslashes
            line = "Missing Backslashes"
            problemsList.append("{}\n{}".format(line, "-" * len(line)))
            for i, item in enumerate(noBackslashesList):
                problemsList.append("#{}: {}".format(i + 1, item))
            problemsList.append("\n")
            
            # Extra backslashes
            line = "Extra Backslashes"
            problemsList.append("{}\n{}".format(line, "-" * len(line)))
            for i, item in enumerate(extraBackslashesList):
                problemsList.append("#{}: {}".format(i + 1, item))
            problemsList.append("\n")
                        
            # Wrong word
            line = "Wrong Word In Backslashes"
            problemsList.append("{}\n{}".format(line, "-" * len(line)))
            for i, item in enumerate(notInBackslashesList):
                problemsList.append("#{}: {}".format(i + 1, item))
            problemsList.append("\n")
            
            if (dfIndex < len(dfDict) - 1):
                problemsList.append("\n{}\n".format("=" * 45))
        
        # Clear the problems' lists
        del noBackslashesList[:]
        del extraBackslashesList[:]
        del notInBackslashesList[:]
        
    
    # Write the results to a file
    reportFilename = word + "_datafile_report.txt"
    logger.info("Writing results to {}".format(reportFilename))
    with open(fullPath + reportFilename, "wb") as fWriter:
        
        for line in resultsList:
            wLine = line + "\n"
            fWriter.write(wLine.encode("utf-8"))
    
    # Write the problem lines to a file
    if (genProblemFile == True):
        problemFilename = word + "_datafile_problems.txt"
        logger.info("Writing problem lines to {}".format(problemFilename))
        with open(fullPath + problemFilename, "wb") as fWriter:

            for line in problemsList:
                wLine = line + "\n"
                fWriter.write(wLine.encode("utf-8"))
            
    return reportFilename
    
    
# Creates a file that lists what was tagged in the sheet, and their amounts
# Returns the filename of the report created
# path - the path to the directory
# word - the word that is being checked
def CreatedTaggedSheetsReport(path, word):
    fullPath = path + word + "\\"
    loYadua = "לא ידוע"
    noisaf = "נויסף"
    logger.info("Creating a report for tagged sheets in {}".format(fullPath))

    # Get a list of files in the directory    
    fileList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]

    # Loop through files and add the tagged files to a list
    tempList = []
    for file in fileList:

        if (".tsv" in file and "MLP" not in file):
            tempList.append(file)
    
    # Make sure that there are actually files to load
    if (len(tempList) == 0):
        raise NoFilesFoundError("No tagged sheet files (.tsv) for {} were found in {}.format(word, fullPath)")
    
    # Order the tagged sheet list
    taggedSheetList = OrderSheetByType(tempList)
        
    markedEntryDictBySheet = {} # Dictionary of dictionaries
    wordTypeCorrectCountDict = {} # Dictionary of dictionaries of amount of correct examples, for each word-type (accounts for both unknown and 'extra' sheets)
    logger.info("Loading cases file into memory")
    casesDict = GetWordCases(path, word) # Dictionary of word-types+cases
    wordTypeList = list(casesDict.keys()) # List of the word-types from the cases file

    # Go through every tagged file and count the number of '*'s, '-'s, and everything else in between
    for taggedSheet in taggedSheetList:
        logger.info("Processing {}".format(taggedSheet))
    
        # Create dictionary for the tagged file
        markedEntryDictBySheet[taggedSheet] = { "*": 0, "-": 0, "Blank": 0 }
    
        # Initializing the dictionary for word-type amount count (only for unknown and 'extra' sheets)
        if (loYadua in taggedSheet or noisaf in taggedSheet):
            wordTypeCorrectCountDict[taggedSheet] = {}
            
            for wordType in wordTypeList:
                wordTypeCorrectCountDict[taggedSheet][wordType] = 0
    
        # Open tagged file
        with open(fullPath + taggedSheet, "r", encoding = "utf-8") as fReader:
        
            skippedHeader = False
            for line in fReader:
            
                # Skip header
                if (skippedHeader == False):
                    skippedHeader = True
                    headerLine = re.split("\t", line.replace(BOM, "").strip())
                    continue
                
                # Split the line by tabbed characters (do not strip this line, for there may be blank entries)
                lineParts = re.split("\t", line)
            
                # Handle differently depending whether or not the file is majority/minority or unknown
                if (loYadua in taggedSheet):
                
                    # Find the marked word-type. Loop through the possible places that marks were inputted and process the results
                    # NOTE: 'Sentence' is the last area on the line, so it's skipped
                    found = False
                    markedEntry = ""
                    unknownEntriesList = [entry for entry in lineParts[:-1]]
                    
                    
                    # Check for completely blank entries
                    checkCounter = 0
                    
                    for entry in unknownEntriesList:
                        if (entry.strip() == ""):
                            checkCounter += 1        
                    if (checkCounter == len(unknownEntriesList)):
                        found = True
                        markedEntry = "Blank"
                    
                    # Continue if there is at least 1 entry marked
                    if (found == False):
                        checkCounter = 0 # Reset the check counter to check for an incorrect example
                        
                        for entry in unknownEntriesList:
                            if (entry.strip() == "-"):
                                checkCounter += 1
                                
                        if (checkCounter == len(unknownEntriesList)):
                            found = True
                            markedEntry = "-"
                        else:
                        
                            # Get entry inputted
                            # First, check if the entire line contains all '*'s
                            allStar = ""
                            for entry in unknownEntriesList:
                                allStar = allStar + entry.strip()
                            uelLen = len(unknownEntriesList)
                            if (len(allStar) == uelLen and allStar.count("*") == uelLen):
                                found = True
                                markedEntry = "Every input marked with stars"
                            else:
                                
                                # Otherwise, check what is contained in the line
                                for i, entry in enumerate(unknownEntriesList):
                                    if (entry.strip() != ""):
                                        found = True
                                        markedEntry = entry.strip()
                                        
                                        # If it's correct, add to the correct case count
                                        if (markedEntry == "*"):
                                            wordType = headerLine[i][:-1] # It's the same position
                                            wordTypeCorrectCountDict[taggedSheet][wordType] += 1
                                        break
                        
                            # If nothing was found, make sure that there is at least something to go inside
                            if (found == False):
                                markedEntry = "N/A"
                
                    # Add the particular marked entry as a key to the dictionary
                    if (markedEntry not in markedEntryDictBySheet[taggedSheet].keys()):
                        markedEntryDictBySheet[taggedSheet][markedEntry] = 0
                
                    # Update the amount times a particular marked entry was found                   
                    markedEntryDictBySheet[taggedSheet][markedEntry] += 1
                else:
                    # Set the marked entry
                    markedEntry = lineParts[0].strip()
                    if (markedEntry == ""):
                        markedEntry = "Blank"
                        
                    # NEW - accounting if the file is an 'extra' file or not
                    if (noisaf in taggedSheet):
                        typeIndex = 1
                        wordType = lineParts[typeIndex]
                        
                        if (markedEntry != "Blank" and markedEntry != "-"): 
                            wordTypeCorrectCountDict[taggedSheet][wordType] += 1
                        
                    # Add the particular marked entry as a key to the dictionary
                    if (markedEntry not in markedEntryDictBySheet[taggedSheet].keys()):
                        markedEntryDictBySheet[taggedSheet][markedEntry] = 0
                    
                    # Update the amount times a particular marked entry was found
                    markedEntryDictBySheet[taggedSheet][markedEntry] += 1

    # Write the results to a file
    taggedSheetsReportFile = "{}_tagged_sheets_current_report.txt".format(word)
    logger.info("Writing results to {}".format(taggedSheetsReportFile))
    with open(fullPath + taggedSheetsReportFile, "wb") as fWriter:

        for taggedSheet in taggedSheetList:
    
            # Filename and dashes underlining it
            fWriter.write("{}\n".format(taggedSheet).encode("utf-8"))
            fWriter.write("{}\n".format("-" * len(re.sub('[^\w\s]', '', taggedSheet))).encode("utf-8"))
        
            sheetDict = markedEntryDictBySheet[taggedSheet]
            for key in sheetDict.keys():
                fWriter.write("{}: {}\n".format(key, sheetDict[key]).encode("utf-8"))
                
            # If the tagged sheet is the unknown/'extra' one, list amount of majority/minority
            #     tagged correct/incorrect
            if (loYadua in taggedSheet or noisaf in taggedSheet):
                line = "Breakdown by word-type+case marked correct (not counting all-star line)"
                fWriter.write("    |\n    |\n    v\n{}\n{}\n".format(line, "-" * len(line)).encode("utf-8"))
                
                for wordType in wordTypeList:
                    case = casesDict[wordType]
                    count = wordTypeCorrectCountDict[taggedSheet][wordType]
                    fWriter.write("{}({}): {}\n".format(wordType, case, count).encode("utf-8"))
            
            fWriter.write("\n\n".encode("utf-8"))
    return taggedSheetsReportFile
    

# Creates a report based on the precious and unprecious metals files
# Returns the filename of the report created
# path - the path to the sheets
# word - the word that is being worked on
def SavePreciousMetalsReport(path, word):
    fullPath = path + word + "\\"
    reportFile = word + "_metals_report.txt"
    logger.info("Creating a report for precious metals files in {}".format(fullPath))
    
    preciousList = CreatePreciousMetalsReport(path, word)
    unpreciousList = CreatePreciousMetalsReport(path, word, True)
    
    logger.info("Writing results to {}".format(reportFile))
    with open(fullPath + reportFile, "wb") as fWriter:
        
        # Write the precious metals report
        for i in range(0, len(preciousList)):
            wLine = preciousList[i] + "\n"
            fWriter.write(wLine.encode("utf-8"))
        
        # Write blank lines in-between
        wLine = "\n\n"
        fWriter.write(wLine.encode("utf-8"))
        
        # Write the unprecious metals report    
        for i in range(0, len(unpreciousList)):
            wLine = unpreciousList[i] + "\n"
            fWriter.write(wLine.encode("utf-8"))
    return reportFile
    
# Returns a list of lines to be printed to a file, as a report on the precious/unprecious
#     metals files generated prior
# path - the path to the sheets
# word - the word that is being worked on
# isUnPrecious (optional) - if the metals file to be loaded is unprecious (default False)
def CreatePreciousMetalsReport(path, word, isUnPrecious = False):
    fullPath = path + word + "\\"
    
    # Decide whether or not the file to be loaded is the precious or unprecious metals file
    if (isUnPrecious == True):
        metalsFile = word + "_unprecious_metals.csv"
    else:
        metalsFile = word + "_precious_metals.csv"
    logger.info("Processing {}".format(metalsFile))
    
    # Create a dataframe based on the metals file
    if (not os.path.isfile(fullPath + metalsFile)):
        raise FileNotFoundError("{} was not found in {}".format(metalsFile, fullPath))
    
    metalsDF = None
    metalsDF = LoadMetalsFileIntoDataframe(path, word, isUnPrecious)
    droppedLines = len(metalsDF)
    metalsDF.drop_duplicates(subset = "Sentence", inplace = True, keep = "first")
    
    # Get all sorts of data from the dataframe
    totalLines = droppedLines
    droppedLines = droppedLines - len(metalsDF)
    
    # Cases
    casesDict = dict(metalsDF["Case"].value_counts())
    totalMajorityCases = casesDict["maj"] if "maj" in casesDict else 0
    totalMinorityCases = casesDict["min"] if "min" in casesDict else 0
    totalUnknownCases = casesDict["unk"] if "unk" in casesDict else 0
    
    # Breakdown by metals
    metalsDict = dict(metalsDF["Metal"].value_counts())
    
    # Breakdown by word-phrases
    wordPhrasesDict = dict(metalsDF["Word Phrase"].value_counts())

    
    # List for storing the lines of the file
    linesList = []
    
    # First line
    firstLine = "Report on {}".format(metalsFile)
    linesList.append(firstLine)
    linesList.append("{}\n".format("-" * len(firstLine)))
    
    # General information
    linesList.append("Total lines in file: {}".format(totalLines))
    linesList.append("Dropped lines: {}".format(droppedLines))
    
    # Breakdown by case
    breakdownLine = "\nBreakdown of cases"
    linesList.append(breakdownLine)
    linesList.append("{}".format("-" * len(breakdownLine)))
    linesList.append("Majority: {}".format(totalMajorityCases))
    linesList.append("Minority: {}".format(totalMinorityCases))
    linesList.append("Unknown: {}".format(totalUnknownCases))
        
    # Breakdown by metal
    breakdownLine = "\nBreakdown of metals"
    linesList.append(breakdownLine)
    linesList.append("{}".format("-" * len(breakdownLine)))
    nameDict = metalsDict
    nameList = list(nameDict.keys())
    for i in range(0, len(nameList)):
        name = nameList[i]
        linesList.append("{}: {}".format(name, nameDict[name]))
        
    # Breakdown by word
    breakdownLine = "\nBreakdown of word-phrases"
    linesList.append(breakdownLine)
    linesList.append("{}".format("-" * len(breakdownLine)))
    nameDict = wordPhrasesDict
    nameList = list(nameDict.keys())
    for i in range(0, len(nameList)):
        name = nameList[i]
        linesList.append("{}: {}".format(name, nameDict[name]))
    
    return linesList
    
  
# Generates a report on the sentence count of the training/testing/morphology/MLP/nakdan minority datafiles, as well as the
#     precious/unprecious metals files in the form of a .csv
# path: the path to the main disambiguation directory
# word: the hebrew word of which the aggregated sentences report is being generated
def CreateAggregatedSentenceReport(path, word):
    fullPath = path + word + "\\"
    logger.info("Creating an aggregated sentence report in %s" % (fullPath))
    
    # Get the main datafiles' dataframes
    logger.info("Loading datafiles into memory")
    datafileDict = GetDataFrameDictionary(path, word, ALL)

    # Get the nmin dataframe
    hasNMinFiles = True if (len(GetNMinFilesByWord(path, word)) > 0) else False
    if (hasNMinFiles):
        logger.info("Loading nmin datafile into memory")
        datafileDict["nminDF"] = GetNMinDataframe(path, word)
    
    # Get the dataframes for the precious/unprecious metals files
    metalsFiles = ["%s_precious_metals.csv" % (word), "%s_unprecious_metals.csv" % (word)]
    
    # Doing this prevents errors
    if (os.path.exists(fullPath + metalsFiles[0])):
        logger.info("Loading %s_precious_metals.csv into memory" % (word))
        datafileDict["preciousDF"] = LoadMetalsFileIntoDataframe(path, word, isUnPrecious = False)
    if (os.path.exists(fullPath + metalsFiles[1])):
        logger.info("Loading %s_unprecious_metals.csv into memory" % (word))
        datafileDict["unPreciousDF"] = LoadMetalsFileIntoDataframe(path, word, isUnPrecious = True)

    # Process dataframes
    logger.info("Processing dataframes")
    sentenceCountDict = {}
    for dfName, df in datafileDict.items():
        logger.info("Processing %s" % (dfName))
        df.drop_duplicates(subset = "Sentence", inplace = True, keep = "first")
        sentenceCountDict[dfName] = len(df["Sentence"])

        # For processing precious/unprecious metals files
        if ("precious" in dfName.lower()):
            logger.info("Loading cases file into memory")
            casesDict = GetWordCases(path, word)
            if ("un" in dfName.lower()): casesDict[word] = "unk" # Accounting for 'unknown' cases, ie the word without nikud
        
            wordTypes = list(casesDict.keys())
            metalTypes = list(df["Metal"].unique())
        
            # Loop over each metal type (Gold, Silver, Bronze, Brass, Lead), and within that loop, loop over each word-type available
            for metalType in metalTypes:
                logger.info("Analyzing '%s' metal type" % (metalType))
                metalDF = df[df["Metal"] == metalType]
                metalWordTypes = list(metalDF["Word Type"].unique()) # word-types associated with the current metal type
            
                for wordType in wordTypes:    
                    if (wordType not in metalWordTypes):
                        continue
                
                    # Accounts for multiple word-types with the same case
                    filteredDF = metalDF[(metalDF["Case"] == casesDict[wordType]) & (metalDF["Word Type"] == wordType)]
                
                    sentenceCountDict["%s - %s - %s" % (wordType, metalType, casesDict[wordType])] = len(filteredDF["Sentence"])
                
    # Reorder items
    logger.info("Reording sentence counts")
    # Change names of the keys with 'DF' inside them
    nameExchange = {"trainDF": "Training Datafile", "testDF": "Testing Datafile", "bitmorphDF": "Morphology Datafile", 
                    "mlpDF": "MLP Datafile", "nminDF": "Nakdan Minority Datafile", "preciousDF": "Precious Metals File", 
                    "unPreciousDF": "UnPrecious Metals File"}

    # First, set the 'input' files' sentence count to 0
    tempDict = {}
    for key in nameExchange.keys():
        tempDict[key] = 0

    # Next, set the sentence count of the 'input' files that do exist
    for key in sentenceCountDict.keys():
        if ("DF" in key):
            tempDict[key] = sentenceCountDict[key]
        
    # Finally, set the sentence count of the various metals+word-types+cases in the precious/unprecious metals files
    for key in sentenceCountDict.keys():
        if ("DF" not in key):
            tempDict[key] = sentenceCountDict[key]
    sentenceCountDict.clear()

    # Rename the keys with 'DF' in them to their proper names
    for key in tempDict.keys():
        keyToPrint = key if (key not in nameExchange.keys()) else nameExchange[key]
        sentenceCountDict[keyToPrint] = tempDict[key]

    # Write sentence count to a .csv file
    aggFile = WriteAggregatedSentencesToFile(path, word, sentenceCountDict)
    
    return aggFile
    
    
# Takes a dictionary of the sentence counts of various data/precious/unprecious files and writes it to a .csv file
# path: the path to the main disambiguation directory
# word: the hebrew word which is being worked on
# sentenceCountDict: a dictionary in which the keys are the row headers and the values are their sentence counts
def WriteAggregatedSentencesToFile(path, word, sentenceCountDict):
    fullPath = path + word + "\\"
    aggFile = word + "_agg_sentences.csv"
    logger.info("Writing %s to %s" % (aggFile, fullPath))
    
    with open(fullPath + aggFile, "wb") as fWriter:
        
        # Header
        wLine = " \tSentence Count" + "\n"
        fWriter.write(wLine.encode("utf-8"))
        
        for key,value in sentenceCountDict.items():
            wLine = "%s\t%d" % (key, value) + "\n"
            fWriter.write(wLine.encode("utf-8"))
    
    return aggFile