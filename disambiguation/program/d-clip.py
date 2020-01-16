import argparse
import os
from os import listdir
from os.path import isfile, join
import re
import sys
import logging
import time
import tagshegen_new as tsg
import genclist_new as gcl
import genreps as gr
import scofil_new as scf
import premshe_new as pms
import disautils_new as du
import dclip_functions as dcf

# Disambiguation Command-Line Program (D-CLIP)

# Filter classes for logging
# Does not allow 'exceptions' through
class NoExceptionFilter(logging.Filter):
    def filter(self, record):
        return not (record.name == "EXCEPTION")
# Does not allow *anything* through. For disabling logging when debugging
class NothingAllowedFilter(logging.Filter):
    def filter(self, record):
        return not (record.levelname == "EXCEPTION" or record.levelname == "INFO" or record.levelname == "ERROR")


# Checks to make sure that a string is *only* made up of hebrew characters
def LineIsAllHebrew(str):
    hebList = du.GetHebrewLetters()
    return all((heb in hebList) for heb in str.strip())

# Returns a dictionary of parsed actions/words with actions to execute instead of directly from the command line
# currentDir: the directory that cli_program.py is found in
# batchFile: the file of batch actions to load
# options: the possible options that can be used from the command-line
def ParseBatchFile(dir, batchFile, options):
    
    # Cannot parse file if it doesn't exist
    if (not os.path.isfile(dir + batchFile)):
        raise FileNotFoundError("{} not found in {}".format(batchFile, dir))
    
    # Must be .dbf or .txt
    ext = batchFile[-4:]
    if (ext != ".dbf" and ext != ".txt"):
        raise ValueError("Batch file extension must be .txt or .dbf")
    
    batchType = ""
    batchWords = []
    fileLines = []
    with open(dir + batchFile, "r", encoding = "utf-8") as fReader:
        fileLines = fReader.readlines()
        
    # Ignore comments (first character is '#')/blank lines
    firstLine = ""
    firstLineNum = 0
    for i, line in enumerate(fileLines):
        if (line.strip() != "" and line.strip()[0] != "#"):
            firstLine = re.split("#", line.strip(), 1)[0].strip()
            firstLineNum = i
            break
    if (firstLine == ""):
        raise ValueError("Could not determine batch type")
    
    # Process first line and determine batch file type by it
    if (firstLine == "all"):
        batchType = "all"
    elif ("," in firstLine):
        batchType = "words"
        batchWords = re.split(",", firstLine)
    elif (LineIsAllHebrew(firstLine)):
        batchType = "split_words"
                    
        for line in fileLines:
            removedComments = re.split("#", line.strip(), 1)[0].strip()
            if (removedComments == ""):
                continue
            
            if (LineIsAllHebrew(removedComments)):
                batchWords.append(removedComments)
    else:
        raise ValueError("Batch file is formatted incorrectly")

    # Process batch actions
    batchDict = ProcessBatchLines(batchType, batchWords, fileLines, firstLineNum, options)
    
    # Make sure there are actually commands to execute
    if (len(batchDict["cmdLines"]) == 0):
        raise ValueError("No commands found in the batch file")
    
    return batchDict

# Processes a batch file (.dbf or .txt), and return a dictionary with the type of batch action,
#     2 lists of processed actions/errors, and a dictionary of processed commands for every word
# batchType: what type of batch file is being worked with
# batchWords: the hebrew words that have commands attached to them
# fileLines: a list of lines of the batch file
# options: the possible options that can be used from the command-line
def ProcessBatchLines(batchType, batchWords, fileLines, firstLineNum, options):
    foundError = False
    cmdLines = []
    cmdErrors = []
    cmdWordDict = {}
    
    # Setup
    offset = 0
    if (batchType == "all" or batchType == "words"):
        batchLines = fileLines[firstLineNum + 1:]
        offset = 2
    elif (batchType == "split_words"):
        batchLines = fileLines
        offset = 1
    
    batchWord = ""
    for i, line in enumerate(batchLines):
        
        # Skip comments (starts with the '#' character) and blank lines
        if (line.strip() == "" or line.strip()[0] == "#"):
            continue
        
        # Setup commands for each word (if there are different commands for different words)
        lineData = re.split("#", line.strip(), 1)[0].strip()
        if (batchType == "split_words" and lineData in batchWords):
            batchWord = lineData
            cmdWordDict[batchWord] = []
            continue
                
        cmdLine = re.split(" ", lineData)
            
        # Look for errors
        for cmd in cmdLine:
            if (cmd not in options):
                foundError = True
                cmdErrors.append("Error found on line {}: {} is not a valid option".format(i + offset, repr(cmd)))
            if (cmd == "--help" or cmd == "-h"):
                foundError = True
                cmdErrors.append("Error found on line {}: help flag invalid".format(i + offset))
            if (cmd == "--batch"):
                cmdErrors.append("Error found on line {}: cannot use batch flag in batch file".format(i + offset))
        
        # If error not found, add the commands to the command list
        if (not foundError):
            cmdLines.append(cmdLine)
            
            if (batchType == "split_words"):
                cmdWordDict[batchWord].append(cmdLine)
        foundError = False
    
    # Add the words to 'cmdWordDict' if batchType == words
    if (batchType == "words"):
        for word in batchWords:
            cmdWordDict[word] = []
    
    return {"batchType": batchType, "cmdLines": cmdLines, "cmdErrors": cmdErrors, "cmdWordDict": cmdWordDict}

# Returns whether or not the directory for a particular word exists
# path: the path to the directories
# word: the word to check for
def CheckForWordDir(path, word):
    return os.path.isdir(path + word + "\\")
    
    
# Returns the function to execute, depending on the command-line options chosen
# args: the options chosen in the command-line
def FunctionGetter(args):

    # Cases
    if (args.cases):
        return dcf.DoCasesFile
        
    # Tagging
    if (args.tsheets):
        return dcf.DoTaggingSheets
    if (args.esheets):
        return dcf.DoExtraSheets
    
    # Scoring
    if (args.score):
        return dcf.DoScoringFiles
    if (args.mlpscore):
        return dcf.DoMLPScoringFile
    
    # Precious metals
    if (args.precious):
        return dcf.DoPreciousMetalsFile
    if (args.unprecious):
        return dcf.DoUnPreciousMetalsFile
    
    # Reports
    if (args.dreport):
        return dcf.DoDatafileReport
    if (args.treport):
        return dcf.DoTaggedSheetsReport
    if (args.mreport):
        return dcf.DoPreciousMetalsReport
    if (args.aggreport):
        return dcf.DoAggregatedSentencesReport
    
    # Other
    if (args.fixsheets):
        return dcf.DoFixSheets
    
    # Secret option -- the fake word-type
    if (args.faketype):
        return dcf.DoInsertFakeTypeSentence

    # Excel
    if (args.excel):
        return None
    
    
# Returns the arguments needed by the appropriate function, depending on the command-line options chosen
# parser: the ArgumentParser object created from argparse
# path: the path to the main directory
# word: the hebrew word being worked on
# args: the options chosen in the command-line
def GetFunctionArguments(parser, path, word, args):
    if (args.aggreport or args.fixsheets or args.faketype or args.esheets or args.treport or args.mreport or 
        args.score or args.mlpscore or args.precious or args.unprecious):
        return [path, word, args.excel, args.ltr, args.autofilter]
    else:
        if (args.dreport or args.tsheets):
                datafileTypes = 0
    
                if (args.train):
                    datafileTypes |= tsg.TRAIN
        
                if (args.test):
                    datafileTypes |= tsg.TEST
    
                if (args.bitmorph):
                    datafileTypes |= tsg.BITMORPH
        
                if (args.mlp):
                    datafileTypes |= tsg.MLP
                
                if (args.nmin and not args.tsheets):
                    datafileTypes |= tsg.NMIN
                    
                if (args.alld):
                    datafileTypes |= tsg.ALL
        
                if (datafileTypes == 0):
                    argType = "tsheets" if (args.tsheets) else "dreport"
                    parser.error("--{} requires 1 or more additional arguments (input --help for details)".format(argType))
                
                if (args.tsheets):
                    return [path, word, datafileTypes, args.excel, args.ltr, args.autofilter]
                else:
                    return [path, word, datafileTypes, args.pfile, args.excel, args.ltr, args.autofilter]
        elif (args.cases):
            action = -1
            multiSelect = 0
        
            if (args.new):
                action = gcl.NEW_FILE
                multiSelect += 1
            
            if (args.overwrite):
                action = gcl.OVRW_FILE
                multiSelect += 1
            
            if (args.regen):
                action = gcl.REGEN_FILE
                multiSelect += 1
                        
            if (action == -1):
                parser.error(" --cases requires either --new, --overwrite, or --regen")
            if (multiSelect > 1):
                parser.error("mutiple options for --cases are not allowed")
            
            return [path, word, action, args.excel, args.ltr, args.autofilter]
        elif (args.excel or args.ltr == False or args.autofilter):
            parser.error("excel flags can only be used in conjuction with disambiguation options")


# START
parser = argparse.ArgumentParser()

# Required, word to work on (optional if using batch option)
parser.add_argument("word", nargs = "?", default = None, 
    help = "the hebrew word to work on (not required when using --batch)")

# Group of disambiguation actions (cannot be used together)
disambigGroup = parser.add_argument_group(title = "DISAMBIGUATION (required)", 
    description = "list of actions that can be performed on a particular hebrew word")
exclusiveGroup = disambigGroup.add_mutually_exclusive_group(required = True)

# Execute commands from a batch file
exclusiveGroup.add_argument("--batch", nargs = 1, 
    help = "executes a series of disambiguation actions from a .dbf/.txt file (cannot be combined with 'word')")

# Cases
exclusiveGroup.add_argument("--cases", action = "store_true", help = "generates a cases file")

# Tagging
exclusiveGroup.add_argument("--tsheets", action = "store_true", help = "generates tagging sheets")
exclusiveGroup.add_argument("--esheets", action = "store_true", help = "generates 'extra' tagging sheets (for adding additional sentences after main tagging sheets have been generated)")

# Scoring
exclusiveGroup.add_argument("--score", action = "store_true", help = "generates scoring files based on tagged sheets")
exclusiveGroup.add_argument("--mlpscore", action = "store_true", help = "generates scoring files based on MLP tagged sheets")

# Precious metals
exclusiveGroup.add_argument("--precious", action = "store_true", help = "generates a precious metals file")
exclusiveGroup.add_argument("--unprecious", action = "store_true", help = "generates an unprecious metals file")

#Reports
exclusiveGroup.add_argument("--dreport", action = "store_true", help = "generates a datafile report")
exclusiveGroup.add_argument("--treport", action = "store_true", help = "generates a tagged sheets report")
exclusiveGroup.add_argument("--mreport", action = "store_true", help = "generates a precious metals files report")
exclusiveGroup.add_argument("--aggreport", action = "store_true", help = "generates an aggregated sentence count report")

# Other
exclusiveGroup.add_argument("--fixsheets", action = "store_true", help = "fixes whitespace issues with tagged sheets")

# Secret option, not in the help -- ONLY FOR ADDING FAKE WORD-TYPES when there is only 1 word-type and we want the
#     sentences in for the k-rules
exclusiveGroup.add_argument("--faketype", action = "store_true", help = argparse.SUPPRESS)

# FLAGS
flagGroup = parser.add_argument_group(title = "FLAGS", 
    description = "list of flags to use with the disambiguation actions")

# Excel options
excelGroup = parser.add_argument_group(title = "EXCEL", description = "excel flags")
excelGroup.add_argument("--excel", action = "store_true", 
    help = "creates an excel sheet version of the file generated (not available for reports or --mlpscore)")
excelGroup.add_argument("--ltr", action = "store_false", help = "sets the direction of the sheet to 'left-to-right' (default 'right-to-left')")
excelGroup.add_argument("--autofilter", action = "store_true", help = "enables the autofilter option")

# Cases file (flags are mutually exclusive)
casesGroup = parser.add_argument_group(title = "CASES", description = "cases flags (mutually exclusive)")
casesGroup.add_argument("--new", action = "store_true", help = "a flag for creating a new cases file")
casesGroup.add_argument("--overwrite", action = "store_true", help = "a flag for overwriting an existing cases file")
casesGroup.add_argument("--regen", action = "store_true", 
    help = "a flag for regenerating the cases file with marked cases left intact")

# Datafiles
datafileGroup = parser.add_argument_group(title = "DATAFILE", description = "datafile flags")
datafileGroup.add_argument("--alld", action = "store_true", 
    help = "a flag to include all datafiles in the datafile report/tagging sheets")
datafileGroup.add_argument("--train", action = "store_true", 
    help = "a flag to include the training datafile in the datafile report/tagging sheets")
datafileGroup.add_argument("--test", action = "store_true", 
    help = "a flag to include the testing datafile in the datafile report/tagging sheets")
datafileGroup.add_argument("--bitmorph", action = "store_true", 
    help = "a flag to include the morphology datafile in the datafile report/tagging sheets")
datafileGroup.add_argument("--mlp", action = "store_true", 
    help = "a flag to include the MLP datafile in the datafile report/tagging sheets")
datafileGroup.add_argument("--nmin", action = "store_true", 
    help = "a flag to include the Nakdan Minority (nmin) for datafile reports ONLY")

# Reports
reportGroup = parser.add_argument_group(title = "REPORT", description = "report flags")
reportGroup.add_argument("--pfile", action = "store_true", 
    help = "a flag for --dreport to generate a file filled with problematic lines")

#  Other
otherGroup = parser.add_argument_group(title = "OTHER", description = "miscellaneous flags")
otherGroup.add_argument("--nolog", action = "store_true", 
    help = "a flag for supressing the logging system. FOR DEBUGGING ONLY!")

# LOGGING
# Setup logging for the program
logging.basicConfig(handlers = [logging.FileHandler("disambiguation.log", "a", "utf-8")],
    level = logging.INFO,
    format = "(%(levelname)s,%(name)s) - %(asctime)s: %(message)s", datefmt = "%d/%m/%Y %I:%M:%S %p")
logger = logging.getLogger()
logger.addHandler(logger.handlers[0])
logger.handlers[0].addFilter(NoExceptionFilter()) # Add special filter for stack traces not to appear

# Setup logging for stack traces of exceptions
stkLog = logging.FileHandler("stack_trace.log", "a", "utf-8")
stkLog.setFormatter(logging.Formatter("(%(levelname)s,%(name)s) - %(asctime)s: %(message)s", 
    "%d/%m/%Y %I:%M:%S %p"))
loggerException = logging.getLogger("EXCEPTION")
stkLog.setLevel(logging.ERROR)
loggerException.addHandler(stkLog) 


# Main part of the program
args = parser.parse_args() # command-line options chosen
path = os.getcwd() + "\\" + os.pardir + "\\Directory\\"
doSecretOption = False

# If debugging, no logging (so that the log files don't get clogged up with error messages)
if (args.nolog):
    logger.handlers[0].addFilter(NothingAllowedFilter())
    loggerException.handlers[0].addFilter(NothingAllowedFilter())

logger.info("%s%s%s", "~" * 32, "START OF PROGRAM", "~" * 32)

#  Make sure to input a hebrew word
if (args.word is None and args.batch is None):
    errMsg = "must input hebrew word to work on"
    logger.error("ParserError: %s", errMsg)
    parser.error(errMsg)

# No using word option and --batch together
if (args.word is not None and (args.batch is not None and len(args.batch) > 0)):
    errMsg = "cannot combine a hebrew word with --batch"
    logger.error("ParserError: %s", errMsg)
    parser.error(errMsg)

# If executing commands from a batch file
batchDict = {}
if (args.batch is not None and len(args.batch) > 0):
    batchFile = args.batch[0]
    options = list(parser.__dict__["_option_string_actions"].keys())
    logger.info("Loading batch file (%s)", batchFile)
    try:
        batchDir = os.getcwd() + "\\{}\\batch files\\".format(os.pardir)
        batchDict = ParseBatchFile(batchDir, batchFile, options)
    except Exception as e:
        errMsg = type(sys.exc_info()[1]).__name__ + ": " + str(e)
        print(errMsg + "\n")
        logger.error("(%s): %s", batchFile, errMsg)
        loggerException.error("%s", "~" * 80)
        loggerException.exception("(%s): %s", batchFile, errMsg)
        parser.error("Cannot parse batch file")
    errList = batchDict["cmdErrors"]
    
    # If there are errors, let the user decide if to continue despite them
    if (len(errList) > 0):
        print("Errors detected in {}".format(batchFile))
        logger.info("Errors detected in %s", batchFile)
        for err in errList:
            print(err)
            logger.info("%s", err)
        
        errInput = input("Continue despite errors? (y/n) (problem lines will not be executed) ").lower()
        print("")
        if (errInput != "y" and errInput != "n"):
            errMsg = "Invalid input to continue batch file execution"
            logger.error("ParserError: %s", errMsg)
            parser.error(errMsg)
        elif (errInput == "n"):
            parser.error("Ending execution")
        logger.info("Continuing execution of %s (lines containing errors skipped)", batchFile)
    
    # Check for secret option
    for cmdLine in batchDict["cmdLines"]:
        if ("--faketype" in cmdLine):
            doSecretOption = True
            break        
    cmdType = batchDict["batchType"]

# If inserting a fake word-type, ask for input to MAKE SURE that is what's wanted
if (args.faketype or doSecretOption):
    makeChoice = input("This is a secret option to insert a fake word-type/sentence into the training datafile and " +\
        "cases file for k-rules. Are you sure you want to go through with this? (y/n) ").lower()
    print("")
    if (makeChoice != "y" and makeChoice != "n"):
        errMsg = "Invalid input to perform the insertion of the fake word-type/sentence"
        logger.error("ParserError: %s", errMsg)
        parser.error(errMsg)
    elif (makeChoice == "n"):
        parser.error("Ending execution")
    
# Get word(s) to work on
logger.info("Loading words")
wordList = []
if (args.word is not None):
    cmdType = args.word

if (cmdType == "all"):
    wordList = [f for f in listdir(path) if os.path.isdir(join(path, f))]
    
elif (cmdType == "words" or cmdType == "split_words"): # For batch files
    for word in batchDict["cmdWordDict"].keys():
        wordList.append(word)
elif ("," in cmdType):
    wordList = re.split(",", args.word)
else:
    wordList = [args.word]

        
# Executing commands
logger.info("Starting execution of commands")
if (args.batch is not None):
    print("Executing batch file {}".format(args.batch[0]))
startTime = time.time() # Start timer for executing time (very simply implemented)

for i, word in enumerate(wordList):
    dirExists = CheckForWordDir(path, word)
    
    if (not dirExists):
        print("Directory for {} does not exist".format(word))
        continue
    
    line = "Working on word {} of {} ({})".format(i + 1, len(wordList), word)
    print("%s\n%s\n" % (line, "-" * len(line)))
    fullPath = path + word + "\\"
    
    # Setup the commands to execute
    cmdList = []
    if (args.batch is None):
        cmdList = [sys.argv[2:]]  
    elif (cmdType != "split_words"):
        cmdList = batchDict["cmdLines"]
    elif (cmdType == "split_words"):
        cmdList = batchDict["cmdWordDict"][word]
    
    
    # Execute each command
    for command in cmdList:
        cmdArgs = None
        try:
            cmdArgs = parser.parse_args([word] + command) # command-line options chosen
        except SystemExit:
        
            # If someone can find a way to get the parser error printed, they're welcome to do so
            logging.error("ParserError: Error executing commands for %s", word)
            continue
        
        # Get the function to execute, and its arguments
        func = FunctionGetter(cmdArgs)
        funcArgs = GetFunctionArguments(parser, path, word, cmdArgs)
    
        try:
            logger.info("=" * 80)
            func(funcArgs)
        except Exception as e:
            errMsg = type(sys.exc_info()[1]).__name__ + ": " + str(e)
            print(errMsg + "\n")
            logging.error("%s", errMsg)
            loggerException.error("%s", "~" * 80)
            loggerException.exception("(%s): %s", word, errMsg)
    print("%s\n" % ("=" * len(line)))
    
endTime = time.time() # End of timer
logger.info("End of execution")
print("Finished execution (%.4f seconds)" % (endTime - startTime))