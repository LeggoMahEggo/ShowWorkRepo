import pandas as pd
import re
import os
import logging
import xlsxwriter
import sys
import tagshegen_new as tsg
from os import listdir
from os.path import isfile, join
import zipfile as zp

# Module for various utilities for the disambiguation project
# Author: Yehuda Broderick

# Logging object
logger = logging.getLogger(__name__)

# CONSTANTS
NONE = 0
TAGGING = 1
TAGGING_BITMORPH = 2
BITMORPH = 4
MLP = 8
EXTRA = 16

# Custom exception for no files found
class NoFilesFoundError(Exception):
    pass


# Check if the file can be loaded in 32-bit architecture
def CheckFileByArch32(path, word, file):
    fullPath = path + word + "\\"
    max32Int = (2 ** 31) - 1
    maxSysInt = sys.maxsize
    max32FileSize = 716 * (1024 ** 2) # Max size of file is 750MB
    fileSize = os.path.getsize(fullPath + file)

    if (max32Int == maxSysInt and fileSize >= max32FileSize):
        raise MemoryError("Not enough memory available to load %s in 32-bit mode" % (file))


# Returns a percentage of a from b
# b - the total amount
# a- the amount out of b
def GetPercentage(a, b):
    percent = (a / b) * 100
    return int(percent) if float(percent).is_integer() else round(percent, 2)


# Returns a dictionary with the keys as the word-types and the values as the specific cases
# path - the path to the file
# word - the word that is being checked
def GetWordCases(path, word):
    fullPath = path + word + "\\"
    casesFile = word + "_cases.csv"
    
    # Make sure that the file exists
    if (not os.path.isfile(fullPath + casesFile)):
        raise FileNotFoundError("{} was not found in {}".format(word, fullPath))
    
    casesDict = {}
    with open(fullPath + casesFile, "r", encoding = "utf-8") as fReader:
    
        skippedHeader = False
        for line in fReader:
        
            if (skippedHeader == False):
                skippedHeader = True
                continue
        
            fullLine = re.split("\t| ", line.replace(tsg.BOM, ""), 1)
            wordType = fullLine[0].strip()
            if ("maj" in line):
                casesDict[wordType] = "maj"
            elif ("min" in line):
                casesDict[wordType] = "min"
            elif ("check" in line):
                casesDict[wordType] = "chk"
    
    # Do not return an empty dictionary
    if (len(casesDict) == 0):
        raise EOFError("Cases file for {} in {} is empty".format(word, fullPath))
        
    # The cases file *must* have at least 2 cases marked
    if (len(casesDict) == 1):
        raise ValueError("Cases file for {} in {} only has 1 case marked".format(word, fullPath))
    
    return casesDict


# Returns a line from a file with all whitespace removed from the *end*
def GetNonBrokenLine(line):
    whitespace = [" ", "\t", "\n"]
    counter = 0
    
    for char in line[::-1]:
        if (counter == 0 and char not in whitespace):
            return line
        
        if (char in whitespace):
            counter += 1
            continue
        else:
            break
            
    return line[:-counter]


# Prevents breaking of code when loading tagged sheets, by removing the whitespace at the end of every line
# path: the path to the main disambiguation directory
# word: the hebrew word of which to fix the sheets
def FixTaggedSheets(path, word):
    fullPath = path + word + "\\"
    logger.info("Fixing whitespace issues in tagged sheets for %s in %s" % (word, fullPath))
    fileList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]

    for file in fileList:
        if (".tsv" not in file):
            continue
        
        logger.info("Fixing %s" % (file))
        print("Fixing %s..." % (file), end = "", flush = True)
        
        # Get lines of file
        brokenLines = []
        with open(fullPath + file, "r", encoding = "utf-8") as fReader:
            brokenLines = fReader.readlines()
        
        # Fix lines
        processedHeader = False
        with open(fullPath + file, "wb") as fWriter:
            for line in brokenLines:
                if (not processedHeader):
                    processedHeader = True
                    wLine = line.strip() + "\n"
                else:
                    wLine = GetNonBrokenLine(line) + "\n"
        
                fWriter.write(wLine.encode("utf-8"))
        
        print("done")
        
        
#######################################################
#                                                     #
#  #      #####  #####   #####  #####  #####   #####  #
#  #      #        #       #    #      #    #  ##     #
#  #      ###      #       #    ###    #####     ###  #
#  #####  #####    #       #    #####  #    #  #####  #
#######################################################


# Returns a list of hebrew letters, specifically aleph-tav, backwards nun, and double vav/yud, and vav-yud
def GetHebrewLetters():
    hebList = []
    hebLen = 1514 - 1488

    # Add all the regular hebrew letters
    for i in range(0, hebLen + 1):
        hebList.append(u"{}".format(chr(1488 + i)))
    
    # Add backwards nun
    hebList.append(u"{}".format(chr(int("0x5C6", 0))))
    
    # Add double-vav, vav-yud, and double-yud
    hebList.append(u"{}".format(chr(int("0x5F0", 0))))
    hebList.append(u"{}".format(chr(int("0x5F1", 0))))
    hebList.append(u"{}".format(chr(int("0x5F2", 0))))
    
    return hebList


# Returns a hebrew word with letters with a meteg and accompanying vowels removed
# word - the hebrew word
def RemoveMetegLetter(word):
    metegList = []
    meteg = u"\u05bd" # Meteg unicode character
    hebList = GetHebrewLetters() # A list of hebrew characters
    
    # Work out the full length of a letter+vowel and/or meteg, working back to front
    # The index values are added in pairs: first, the index of the meteg, then the index of the hebrew character that
    #     it's located under
    start = False
    for i in range(len(word) - 1, -1, -1):
        hebChar = word[i]
        
        if (start == False and hebChar == meteg):
            start = True
            metegList.append(i)
        
        if (start == True and hebChar in hebList):
            start = False
            metegList.append(i)
            
    # Next, remove the characters+meteg and/or nikud from the word
    prevLen = 0
    newWord = word
    for i in range(len(metegList) - 1, -1, -2):
        letPos = metegList[i] # Index pos of the letter
        metegPos = metegList[i - 1] # Index pos of the meteg
        letLen = metegPos - letPos + 1        
        
        startPos = letPos - prevLen
        endPos = metegPos - prevLen + 1
        newWord = newWord[:startPos] + newWord[endPos:]
        prevLen += letLen
        
    return newWord


########################################
#                                      #
#  #####  #   #  #####   #####  #      #
#  #       ###   #       #      #      #
#  ###      #    #       ###    #      #
#  #####  ## ##  #####   #####  #####  #
########################################


# Takes a list of sheets/whatever, and sorts them in the appropriate order by majority, misc, minority, morphology,
#     unknown, MLP, then finally any additional sheets
# sheetList: a list containing various sheets or other stuff which have 
def OrderSheetByType(sheetList):
    haRov = "הרוב"
    shonot = "שונות"
    miut = "מיעוט"
    morphologia = "מורפולוגיה"
    loYadua = "לא ידוע"
    
    newList = []
    orderedList = []
    orderedList.append(haRov)
    orderedList.append(shonot)
    orderedList.append(miut)
    orderedList.append(morphologia)
    orderedList.append(loYadua)
    orderedList.append("MLP")
    
    for i in range(0, len(orderedList)):
        for j in range(0, len(sheetList)):
            if (orderedList[i] in sheetList[j]):
                newList.append(sheetList[j])
                continue
    
    # Add additional sheets that aren't the regular tagging sheets (currently, nmin sheets)
    if (len(newList) < len(sheetList)):
        for sheet in sheetList:
            if (sheet not in newList):
                newList.append(sheet)
    
    return newList    


# Loads a .txt style file into a list 
# fullPath: the full path to the file
def LoadFileIntoMemory(fullPath):
    
    lines = []
    with open(fullPath, "r", encoding = "utf-8") as fReader:
        
        for line in fReader:
            if (line.strip() != ""):
                lines.append(line.replace("\n", ""))
    return lines
 
# Convert a file to an Excel sheet
# path - the path to the file(s)
# word - the word that is being worked with
# fileName (optional): the filename of the file to convert to an Excel sheet (use if tagType is NONE). Default ""
# tagType (optional): if converting tagging sheets or not. Default NONE
# rightToLeft (optional): whether or not the Excel sheet's layout should be right-to-left. Default True
# autoFilter (optional): whether or not the Excel sheet has the autofilter option enabled. Default False
def CreateExcelSheetFromFiles(path, word, fileName = "", tagType = NONE, rightToLeft = True, autoFilter = False):
    fullPath = path + word + "\\"
    
    # End part of tagging sheets
    haRov = "הרוב" + ".csv"
    miut = "מיעוט" + ".csv"
    loYadua = "לא ידוע" + ".csv"
    morphologia = "מורפולוגיה" + ".csv"
    shonot = "שונות" + ".csv"
    emellpee = "MLP.csv"
    
    # Get files to convert, and the eventual Excel sheet's name
    excelFile = ""
    filesToLoad = []
    
    if (tagType == NONE or tagType == EXTRA):
        filesToLoad = [fileName]
        excelFile = "{}.xlsx".format(fileName.replace(".csv", ""))
    elif (tagType == BITMORPH):
        filesToLoad = [morphologia]
        excelFile = word + "_BitMorph_final.xlsx"
    elif (tagType == MLP):
        filesToLoad = [emellpee]
        excelFile = word + "_MLP_final.xlsx"
    elif (tagType == TAGGING):
        filesToLoad = [haRov, miut, loYadua, shonot]
        excelFile = word + "_final.xlsx"
    elif (tagType == TAGGING_BITMORPH):
        filesToLoad = [haRov, miut, loYadua, morphologia]
        excelFile = word + "_final.xlsx"
    
    if (tagType == NONE):
        logger.info("Creating an excel sheet for {} in {}".format(fileName, fullPath))
    else:
        logger.info("Creating tagging excel sheet(s) in {}".format(fullPath))
        
    # The filename must not be blank if converting a non-tagging sheet file
    if (fileName == "" and (tagType == NONE or tagType == EXTRA)):
        raise ValueError("Filename not specified")
    
    # The type of file when tagType is NONE can only be cases files, precious metals files, or regular scoring files
    # NOTE: This can be changed later if in the future new filetypes are added (hazui?)
    if ((tagType == NONE or tagType == EXTRA) and 
        ("cases" not in filesToLoad[0] and "_score.csv" not in filesToLoad[0] 
            and "precious" not in filesToLoad[0] and "נויסף" not in filesToLoad[0])):
        raise ValueError("Only tagging sheets, extra sheets, cases, regular scoring, and precious metals files can be" +
            " converted to a .xlsx file")    
     
    # Get the requisite files
    dirFileList = [f for f in listdir(fullPath) if os.path.isfile(join(fullPath, f)) and ".zip" not in f]
    convertList = [x for x in dirFileList for y in filesToLoad if (y in x)]
    
    if (len(convertList) == 0):
        raise NoFilesFoundError("No files were found in {} to convert to .xlsx file(s)".format(fullPath))
    
    # Create the workbook
    logger.info("Opening workbook for writing")
    workbook = xlsxwriter.Workbook(fullPath + excelFile)
    
    #Preformatting
    defaultFontSize = 11
    taggingFontSize = 24

    # Create 2 format objects - one for regular cells, and one for the header
    fontSize = 0
    if (tagType == NONE):
        fontSize = defaultFontSize
    else:
        fontSize = taggingFontSize
    
    cellFormat = workbook.add_format({'reading_order': 2})
    cellFormat.set_font_size(fontSize)
    cellFormat.set_align('right')

    headerFormat = workbook.add_format()
    headerFormat.set_font_size(fontSize)
    headerFormat.set_align("right")
    
    # Order the list
    if (tagType == NONE or tagType == EXTRA):
        newList = filesToLoad
    else:
        newList = OrderSheetByType(convertList)
    
    for file in newList:
        logger.info("Loading {} into memory".format(file))
        sheetData = LoadFileIntoMemory(fullPath + file)
    
        # Set the sheet's name
        sheetName = ""
        if (tagType == NONE):
            sheetName = "Sheet1"
        else:
            sheetName = file[:-4]
        logger.info("Adding '{}' sheet to workbook".format(sheetName))
        worksheet = workbook.add_worksheet(sheetName)

        # Set page layout direction
        if (rightToLeft == True):
            worksheet.right_to_left()

        # Set the delimiter (score files have a different delimiter)
        delimiter = "\t"
        if ("_score" in file):
            delimiter = "\t|,"

        # Set the table's headers
        logger.info("Adding headers")
        headers = []
        hData = re.split(delimiter, sheetData[0])
        for i in range(0, len(hData)):
            headers.append({"header": hData[i], "header_format": headerFormat})
    
        # Create the range for the table (starts in the right-most corner)
        startColumn = chr(64 + 1)
        endColumn = chr(64 + len(headers))
        startRow = 1
        endRow = len(sheetData) + 1
        rowRange = "{}{}:{}{}".format(startColumn, startRow, endColumn, endRow)

        # Add the table to the worksheet
        logger.info("Adding table")
        worksheet.add_table(rowRange, {"header_row": True, 'columns': headers, "autofilter": autoFilter})

        # Set the starting column widths
        maxWidths = [len(str(h)) for h in hData]

        # Write the data to the table
        logger.info("Writing data to {}".format(excelFile))
        for i in range(1, len(sheetData)):
            row = re.split(delimiter, sheetData[i].replace("\"\"\"", "\""))

            # Check the widths of the row, if they're bigger than the current column widths then change them
            rowWidths = [len(str(l)) for l in row]
            maxWidths = [rCellWidth if (rCellWidth > cColumnWidth) else cColumnWidth for rCellWidth, cColumnWidth in zip(rowWidths, maxWidths)]

            worksheet.write_row("A{}".format(i + 1), row, cellFormat)

        # Approximate autofit of the columns of the table
        pIncrease = fontSize / defaultFontSize # Based on font size
        autofilterAdjust = 1.42 # Having the autofilter slightly changes the width, thus the adjustment
        
        for i, width in enumerate(maxWidths):
            worksheet.set_column(i, i, (width * pIncrease * autofilterAdjust) + 1)
        
        # Make sentence column a specific width (only when creating tagging sheets)
        if (tagType != NONE):
            sentenceColumn = len(maxWidths) - 1
            worksheet.set_column(sentenceColumn, sentenceColumn, 114.2)
    
        # Freeze the top row
        worksheet.freeze_panes(1, 0)

    # Close the workbook
    logger.info("Closing workbook")
    workbook.close()
    
    return excelFile


######################################################
#                                                    #
#  #####  #####  #####   #####  #####  #      #####  #
#    ##     #    #    #  #        #    #      #      #
#   ##      #    #####   ###      #    #      ###    #
#  #####  #####  #       #      #####  #####  #####  #
######################################################
    
# Zips up a file into a .zip archive.
# path - the directory of the particular word
# word - the word that is being worked on
# file - the file to zip up
def ZipIndividualFile(path, word, file):
    fullPath = path + word + "\\"
    zipFile = file + ".zip"
    
    # Raise exception if the file to be zipped doesn't exist
    if (not os.path.isfile(fullPath + file)):
        raise FileNotFoundError("{} was not found in {}".format(file, fullPath))

    # If the zipped up file already exists, raise an exception
    if (os.path.isfile(fullPath + zipFile)):
        raise FileExistsError("{} already exists in {}".format(zipFile, fullPath))

    print("Zipping up {}......".format(file), end = "")
        
    # Create the archive, using the zlib compression algorithm
    with zp.ZipFile(fullPath + zipFile, "w", compression = zp.ZIP_DEFLATED) as myZip:
        myZip.write(filename = fullPath + file, arcname = file)
    print("done")
    
    
# Unzips a particlar .zip archive
# path - the directory of the particular word
# word - the word that is being worked on
# file - the file to unzip (without the .zip extension, with its normal extension)
def UnzipIndividualFile(path, word, file):
    fullPath = path + word + "\\"
    zipFile = file.replace(".zip", "") + ".zip"
    
    # If file does not exist, cannot extract
    if (not os.path.isfile(fullPath + zipFile)):
        raise FileNotFoundError("{} was not found in {}".format(zipFile, fullPath))

    print("Unzipping {}......".format(zipFile), end = "")
    
    # Extract the file
    with zp.ZipFile(fullPath + zipFile, "r") as myZip:
        myZip.extractall(fullPath)
    print("done")
    
    
# Removes a file in a directory that is also found in a zip file with the same name
# path - the directory of the particular word
# word - the word that is being worked on
# file - the file to remove, with its normal extension
def RemoveIndividualFile(path, word, file):
    fullPath = path + word + "\\"
    file = file.replace(".zip", "")

    # Exit if the file does not exist
    if (not os.path.isfile(fullPath + file)):
        raise FileNotFoundError("{} was not found in {}".format(file, fullPath))

    # Remove the file from the directory
    print("Removing {}......".format(file), end = "")
    os.remove(fullPath + file)
    print("done")
    
    
# Will return True if any of the elements of 'checkList' appear in 'checkString'
# checkList - a list of strings to check for inside of checkString
# checkString - the string being checked
def ListInString(checkList, checkString):
    
    for i in range(0, len(checkList)):
        if (checkList[i] in checkString):
            return True
    return False
    
    
# Returns how many files, if any, need to be zipped
# path - the path to the directory
# word - the word that is being checked
# zipFileList - a list of words to look for in the files, that will be zipped
def GetNumberOfFilesToZip(path, word, zipFileList):
    fullPath = path + word + "\\"
    amountDone = 0

    # Get list of files in directory
    fileList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]
    
    for file in fileList:
        if (ListInString(zipFileList, file) == True and ".zip" not in file):
            amountDone += 1
    return amountDone
    
# Returns a list of files that have .zip equivalents
# path - the path to the directory
# word - the word that is being checked
# zipFileList - a list of words to look for in the files, that will be zipped
def GetFilesThatHaveZips(path, word, zipFileList):
    fullPath = path + word + "\\"
    
    logger.info("Getting list of files that have .zip equivalents")
    checkList = []
    fileList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]
    for file in fileList:
        if (ListInString(zipFileList, file) == True and ".zip" not in file):
            checkList.append(file)
    
    returnList = []
    for file in fileList:
        if (ListInString(zipFileList, file.replace(".zip", "")) == True and ".zip" in file):
            returnList.append(file)
    
    return returnList

 
# Zips up specific files in the particular directory
# path - the path to the directory
# word - the word that is being checked
# zipFileList - a list of words to look for in the files, that will be zipped
def ZipUpFiles(path, word, zipFileList):
    fullPath = path + word + "\\"
    amountDone = 0

    logger.info("Zipping up files in {}".format(fullPath))
    line = "Zipping up (up to) {} file(s)".format(len(zipFileList))
    print("{}\n{}".format(line, "-" * len(line)))
    
    # Get list of files in directory
    fileList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]
    
    for file in fileList:
        if (ListInString(zipFileList, file) == True and ".zip" not in file):
            result = ZipIndividualFile(path, word, file)
            amountDone += 1
            
    print("{} file(s) zipped\n".format(amountDone))


# Unzips .zips in the particular directory
# path - the path to the directory
# word - the word that is being checked
# unzipFileList - a list of words to look for in the files, that will be unzipped
def UnzipFiles(path, word, unzipFileList):
    fullPath = path + word + "\\"
    amountDone = 0
    
    logger.info("Unzipping files in {}".format(fullPath))
    line = "Unzipping (up to) {} file(s)".format(len(unzipFileList))
    print("{}\n{}".format(line, "-" * len(line)))
    
    # Get list of files in directory
    fileList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]
    
    for file in fileList:
        if (ListInString(unzipFileList, file) == True and ".zip" in file):
            result = UnzipIndividualFile(path, word, file)
            amountDone += 1
            
    print("{} file(s) unzipped\n".format(amountDone))
    
    
# Removes .zips in the particular directory
# path - the path to the directory
# word - the word that is being checked
# removeFileList - a list of words to look for in the files, that will be removed (the .zip files)
# ignoreList - a list of words to look for in the files, that will be ignored
def RemoveZipFiles(path, word, removeFileList, ignoreList = []):
    fullPath = path + word + "\\"
    amountDone = 0
    
    logger.info("Removing zipped files in {}".format(fullPath))
    line = "Removing (up to) {} .zip file(s)".format(len(removeFileList))
    print("{}\n{}".format(line, "-" * len(line)))
    
    # Get list of files in directory
    fileList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]
    
    for file in fileList:
        if (ListInString(removeFileList, file) == True and 
            ListInString(ignoreList, file) == False and ".zip" in file):
            print("Removing {}......".format(file), end = "")
            os.remove(fullPath + file)
            print("done")
            amountDone += 1
            
    print("{} .zip file(s) removed\n".format(amountDone))
    

# Removes unzipped files in the particular directory
# path - the path to the directory
# word - the word that is being checked
# unzipFileList - a list of words to look for in the files, that will be removed as they are unzipped files
def RemoveUnzippedFiles(path, word, unzipFileList):
    fullPath = path + word + "\\"
    amountDone = 0
    
    logger.info("Removing unzipped files in {}".format(fullPath))
    line = "Removing (up to) {} unzipped file(s)".format(len(unzipFileList))
    print("{}\n{}".format(line, "-" * len(line)))
    
    # Get list of files in directory
    fileList = [f for f in listdir(fullPath) if isfile(join(fullPath, f))]
    
    for file in fileList:
        if (ListInString(unzipFileList, file) == True and ".zip" not in file):
            result = RemoveIndividualFile(path, word, file)
            amountDone += 1
            
            
    print("{} file(s) removed\n".format(amountDone))