import pandas as pd
import tagshegen_new as tsg
import os
import logging
import re

# Module for creating a .csv file that marks the cases (majority/minority) of the word-types of a word
# Author: Yehuda Broderick

# Logging object
logger = logging.getLogger(__name__)


# Constants
NEW_FILE = 0
OVRW_FILE = 1
REGEN_FILE = 2


# Creates a .csv file that lists the different word-types (אוכל_שם_עצם, אוכל_שם_פועל, וכו) and the amount of examples each one has
# Returns the filename of the cases file being created
# path: the path to the directory that has a list of directories, whose names are 'words'
# word: the hebrew word being worked on
# action: 1 of 3 options, to either create a new cases file (NEW_FILE), overwrite an old one (OVRW_FILE), or regenerate
#     the current cases file with the marked cases remaining marked (REGEN_FILE)
# NOTE: if using NEW_FILE while the cases file exists, an exception will be raised
def CreateCasesFile(path, word, action):
    fullPath = path + word + "\\"
    actionType = "Creating a" if (action == NEW_FILE) else (
        "Overwriting the" if (action == OVRW_FILE) else "Regenerating the")
    
    logger.info("{} cases file in {}".format(actionType, fullPath))
    casesFilename = "{}_cases.csv".format(word)
    
    # If trying to create a new cases file, check first to see if it exists
    if (os.path.isfile(fullPath + casesFilename) and action == NEW_FILE):
        raise FileExistsError("{} already exists in {}".format(casesFilename, fullPath))
    
    # If trying to regenerate a cases file, make sure it exists
    if (not os.path.isfile(fullPath + casesFilename) and action == REGEN_FILE):
        raise FileNotFoundError("{} was not found in {}".format(word, fullPath))
    
    logger.info("Loading training datafile into memory")
    # Load the dataframe into memory
    trainDF = tsg.LoadDatafileIntoDataFrame(path, word, tsg.TRAIN)
    
    # Remove trailing whitespace
    trainDF = trainDF.applymap(lambda x: x.strip())
    
    # Drop duplicate rows
    trainDF.drop_duplicates(subset = "Sentence", inplace = True, keep = "first")

    # Dictionary of word-types and how many times they appear
    wordTypeDict = trainDF["WordType"].value_counts()

    # If regenerating the cases file, save the cases marked in the file
    regenDict = {}
    if (action == REGEN_FILE):
        logger.info("Loading prior marked cases into memory")
        with open(fullPath + casesFilename, "r", encoding = "utf-8") as fReader:
            skippedHeader = False
            
            for line in fReader:
                if (skippedHeader == False):
                    skippedHeader = True
                    continue
                
                # Split the line, and strip it of whitespace
                regenLine = re.split("\t", line.replace(tsg.BOM, ""))
                regenLine = [e.strip() for e in regenLine]
                
                # If the word-type was marked with a case, save it
                if (regenLine[-1] != ""):
                    if (regenLine[0] not in regenDict.keys()):
                        regenDict[regenLine[0]] = regenLine[-1]
    
    # Lines to write for the cases file
    casesLines = []
    logger.info("Preparing word-types")
    
    # Header line
    header = "Type\tCount\tMajority/Minority (maj/min)"
    casesLines.append(header)
    
    # Append rest of the lines to the list
    for wordType, count in wordTypeDict.items():
        
        marking = " " if (wordType not in regenDict.keys()) else regenDict[wordType]
        casesLines.append("{}\t{}\t{}".format(wordType, count, marking))
    
    # Create the cases file
    logger.info("Writing results to {}".format(casesFilename))
    with open(fullPath + casesFilename, "wb") as fWriter:
        for line in casesLines:
            fullLine = line + "\n"
            fWriter.write(fullLine.encode("utf-8"))
        
    return casesFilename