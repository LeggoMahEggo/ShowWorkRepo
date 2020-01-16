import os
from os import listdir
from os.path import isfile, join
import logging
from tagshegen_new import TRAIN, TEST, BITMORPH, MLP, NMIN, ALL, AppendFileTypeToWord, CreateTaggingSheets
import genclist_new as gcl
import genreps as gr
import scofil_new as scf
import premshe_new as pms
import disautils_new as du
import etagshe as ets
import infake as ifws


# Logging object
logger = logging.getLogger(__name__)

# Constants
NONE = 0
PRECIOUS = 128
UNPRECIOUS = 256 # continuation of the bitwise-operations constants of 'tagshegen_new'
#NMIN = 512 # NEW - for the nmin datafiles


# Utility functions for D-CLIP. Returns a list of files to be zipped or unzipped
# word: the hebrew word being worked on
# zippingfileTypes: which files to zip/unzip
def GetFilesToZipUnzip(path, word, zippingfileTypes):
    zippingfileList = []
    
    # If the all option is chosen (only for taggging sheets/datafile report), get all datafiles
    if (zippingfileTypes == ALL):
        zippingfileTypes = TRAIN | TEST | BITMORPH | MLP | NMIN
    
    if (zippingfileTypes & TRAIN):
        zippingfileList.append(AppendFileTypeToWord(word, TRAIN))
    
    if (zippingfileTypes & TEST):
        zippingfileList.append(AppendFileTypeToWord(word, TEST))
    
    if (zippingfileTypes & BITMORPH):
        zippingfileList.append(AppendFileTypeToWord(word, BITMORPH))
        
    if (zippingfileTypes & MLP):
        zippingfileList.append(AppendFileTypeToWord(word, MLP))

    if (zippingfileTypes & PRECIOUS):
        zippingfileList.append("{}_precious_metals.csv".format(word))

    if (zippingfileTypes & UNPRECIOUS):
        zippingfileList.append("{}_unprecious_metals.csv".format(word))
        
    if (zippingfileTypes & NMIN):
        fullPath = path + word + "\\"
        nminFiles = ets.GetNMinFilesByWord(path, word) + ["%s_train.txt" % (word)]
        
        for file in nminFiles:
            zippingfileList.append(file)
    
    return list(set(zippingfileList))


# Unzips specific files, and returns a dictionary with a list of files to unzip, and a list of files that 
#     have .zip equivalents
# path: the path to the directory of the hebrew word
# word: the hebrew word being worked on
# unzipTypes: which files to unzip
def DoUnzip(path, word, unzipTypes):
    fileZipList = GetFilesToZipUnzip(path, word, unzipTypes)
    unzipFileList = du.GetFilesThatHaveZips(path, word, fileZipList)
    du.UnzipFiles(path, word, unzipFileList)
    
    return {"fileZipList": fileZipList, "unzipFileList": unzipFileList}


# Removes unzipped files, and zips files that should be zipped, but aren't
# path: the path to the directory of the hebrew word
# word: the hebrew word being worked on
# fileDict: a dictionary that contains the list of files to zip, and a list of files that have .zip equivalents
# preciousType (optional): what precious metals file to rezip. Default NONE
def DoRemoveAndMaybeZip(path, word, fileDict, preciousType = NONE):
    fileZipList = fileDict["fileZipList"]
    unzipFileList = fileDict["unzipFileList"]
    
    if (len(fileZipList) > len(unzipFileList) or preciousType != NONE):
        notZipped = [f for f in fileZipList if (f + ".zip" not in unzipFileList)]
        
        if (preciousType != NONE):
            file = "".join(GetFilesToZipUnzip(path, word, preciousType))
            notZipped.append(file)
            fileZipList.append(file)
            du.RemoveZipFiles(path, word, [file])
        
        du.ZipUpFiles(path, word, notZipped)
    du.RemoveUnzippedFiles(path, word, fileZipList)
    

# Returns the method by which a specific Excel file will be created. ONLY needed for tagging sheets
# NOTE: du.MLP, du.BITMORPH etc *MUST NOT* be imported, so as not to clash with the BITMORPH, MLP, etc from tagshegen_new
# datafileTypes:
def GetExcelCreateType(datafileTypes):
    if (datafileTypes == MLP):
        return du.MLP
    elif (datafileTypes == BITMORPH):
        return du.BITMORPH
    elif (datafileTypes == NMIN): # NEW - for 'extra' files
        return du.EXTRA
    else:
        if (datafileTypes == TRAIN 
            or datafileTypes == TEST 
            or datafileTypes == TRAIN | TEST):
            return du.TAGGING
        elif (datafileTypes == TRAIN | TEST | BITMORPH or datafileTypes == ALL):
            return du.TAGGING_BITMORPH
    
        return du.NONE


# Functions that the dataprocessing themselves blah
# Cases file
def DoCasesFile(args):
    path = args[0]
    word = args[1]
    action = args[2]
    doExcel = args[3]
    rtl = args[4]
    autoFilter = args[5]
    
    line = "Generation of {}_cases".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    actionName = "Regenerating" if (action == gcl.REGEN_FILE) else \
        ("Overwriting" if (action == gcl.OVRW_FILE) else "Creating")
    
    fileDict = DoUnzip(path, word, TRAIN)
    try:
        print("%s cases file......" % (actionName), end = "", flush = True)
        casesFile = gcl.CreateCasesFile(path, word, action)
        print("done")
        if (doExcel):
            print("Creating Excel file of cases......", end = "", flush = True)
            du.CreateExcelSheetFromFiles(path, word, casesFile, du.NONE, rtl, autoFilter)
            print("done\n")
        else:
            print("")
            
    except Exception as e:
        errName = type(e).__name__
        errMsg = "Excel file is open, and must be closed before overwriting it"
        if (errName == "PermissionError"):
            logger.info(errMsg)
            print("{}: {}".format(errName, errMsg))
        raise
        
    finally:
        DoRemoveAndMaybeZip(path, word, fileDict)


# Tagging sheets
def DoTaggingSheets(args):
    path = args[0]
    word = args[1]
    datafileTypes = args[2] & ~NMIN # No nmin files for tagging sheets
    doExcel = args[3]
    rtl = args[4]
    autoFilter = args[5]
    
    line = "Generation of tagging sheets for {}".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    
    fileDict = DoUnzip(path, word, datafileTypes)
    try:
        print("Creating sheets for tagging......", end = "", flush = True)
        CreateTaggingSheets(path, word, datafileTypes)
        print("done")
        
        if (doExcel):
            print("Creating Excel file of sheets......", end = "", flush = True)
            tagType = GetExcelCreateType(datafileTypes)
            if (tagType == du.NONE):
                raise ValueError("Cannot create Excel files of the current selected datafile combination")
            print("done\n")
                   
            du.CreateExcelSheetFromFiles(path, word, "", tagType, rtl, autoFilter)
        else:
            print("")
    except Exception as e:
        errName = type(e).__name__
        errMsg = "Excel file is open, and must be closed before overwriting it"
        if (errName == "PermissionError"):
            logger.info(errMsg)
            print("{}: {}".format(errName, errMsg))
        raise
        
    finally:
        DoRemoveAndMaybeZip(path, word, fileDict)


# 'Extra' sheets
def DoExtraSheets(args):
    path = args[0]
    word = args[1]
    #datafileTypes = NMIN # For the excel sheet
    doExcel = True # Always true, as the code always generates a *new* sheet
    rtl = args[3]
    autoFilter = args[4]
    
    line = "Generation of 'extra' sheet for {}".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    
    fileDict = DoUnzip(path, word, NMIN | TRAIN)
    try:
        print("Creating 'extra' sheet for tagging......", end = "", flush = True)
        extraSheet = ets.CreateExtraTaggingSheet(path, word)
        print("done")
        
        if (doExcel):
            print("Creating Excel file of sheet......", end = "", flush = True)
            tagType = GetExcelCreateType(NMIN)
            du.CreateExcelSheetFromFiles(path, word, extraSheet, tagType, rtl, autoFilter)
            print("done\n")
        else:
            print("")
    except Exception as e:
        errName = type(e).__name__
        errMsg = "Excel file is open, and must be closed before overwriting it"
        if (errName == "PermissionError"):
            logger.info(errMsg)
            print("{}: {}".format(errName, errMsg))
        raise
        
    finally:
        DoRemoveAndMaybeZip(path, word, fileDict)


# Score files
def DoScoringFiles(args):
    path = args[0]
    word = args[1]
    doExcel = args[2]
    rtl = args[3]
    autoFilter = args[4]
    
    line = "Generation of scoring files for {}".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    
    fileDict = DoUnzip(path, word, TRAIN | NMIN)
    try:
        print("Creating scoring files......", end = "", flush = True)
        scoredFiles = scf.CreateScoringFiles(path, word)
        print("done")
        if (doExcel):
            print("Creating Excel files of scored files......", end = "", flush = True)
            for scoredFile in scoredFiles:
                du.CreateExcelSheetFromFiles(path, word, scoredFile, du.NONE, rtl, autoFilter)
            print("done\n")
        else:
            print("")
            
    except Exception as e:
        errName = type(e).__name__
        errMsg = "Excel file is open, and must be closed before overwriting it"
        if (errName == "PermissionError"):
            logger.info(errMsg)
            print("{}: {}".format(errName, errMsg))
        raise
    finally:
        DoRemoveAndMaybeZip(path, word, fileDict)


def DoMLPScoringFile(args):
    path = args[0]
    word = args[1]
    
    line = "Generation of MLP tagged sheets scored file for {}".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    
    try:
        print("Creating MLP scored file......", end = "", flush = True)
        scf.CreateMLPScoringFile(path, word)
        print("done\n")
    except Exception as e:
        raise


# Precious metals files
def DoPreciousMetalsFile(args):
    path = args[0]
    word = args[1]
    doExcel = args[2]
    rtl = args[3]
    autoFilter = args[4]
    
    line = "Generation of precious metals file for {}".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    
    fileDict = DoUnzip(path, word, TRAIN)   
    try:
        print("Creating precious metals file......", end = "", flush = True)
        pmFile = pms.CreatePreciousMetalsFile(path, word)
        print("done")
        
        if (doExcel):
            print("Creating Excel file of precious metals file......", end = "", flush = True)
            du.CreateExcelSheetFromFiles(path, word, pmFile, du.NONE, rtl, autoFilter)
            print("done\n")
        else:
            print("")
    except Exception as e:
        errName = type(e).__name__
        errMsg = "Excel file is open, and must be closed before overwriting it"
        if (errName == "PermissionError"):
            logger.info(errMsg)
            print("{}: {}".format(errName, errMsg))
        raise
    finally:
        print("") # Extra newline, so the output screen isn't too confusing
        DoRemoveAndMaybeZip(path, word, fileDict, PRECIOUS)

    
def DoUnPreciousMetalsFile(args):
    path = args[0]
    word = args[1]
    doExcel = args[2]
    rtl = args[3]
    autoFilter = args[4]
    
    line = "Generation of unprecious metals file for {}".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    
    fileDict = DoUnzip(path, word, TRAIN | TEST | PRECIOUS)    
    try:
        print("Creating unprecious metals file......", end = "", flush = True)
        upmFile = pms.CreateUnPreciousMetalsFile(path, word)
        print("done")
        if (doExcel):
            print("Creating Excel file of unprecious metals file......", end = "", flush = True)
            du.CreateExcelSheetFromFiles(path, word, upmFile, du.NONE, rtl, autoFilter)
            print("done\n")
        else:
            print("")
    except Exception as e:
        errName = type(e).__name__
        errMsg = "Excel file is open, and must be closed before overwriting it"
        if (errName == "PermissionError"):
            logger.info(errMsg)
            print("{}: {}".format(errName, errMsg))
        raise
    finally:
        print("") # Extra newline, so the output screen isn't too confusing
        DoRemoveAndMaybeZip(path, word, fileDict, UNPRECIOUS)


# Reports
def DoDatafileReport(args):
    path = args[0]
    word = args[1]
    datafileTypes = args[2]
    genProblemFile = args[3]
    
    line = "Generation of {}_datafile_report".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    
    fileDict = DoUnzip(path, word, datafileTypes)
    print("Creating datafile report......", end = "", flush = True)
    try:
        gr.CreateDatafileReport(path, word, datafileTypes, genProblemFile)
        print("done")
        if (genProblemFile):
            print("Problem lines written to file\n")
        else:
            print("")
        
    except Exception as e:
        print("Error creating datafile report\n")
        raise
    finally:
        DoRemoveAndMaybeZip(path, word, fileDict)
    
def DoTaggedSheetsReport(args):
    path = args[0]
    word = args[1]
    
    line = "Generation of {}_tagged_sheets_current_report".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    
    try:
        print("Creating tagged sheets report......", end = "", flush = True)
        gr.CreatedTaggedSheetsReport(path, word)
        print("done\n")
    except Exception as e:
        raise
        
def DoPreciousMetalsReport(args):
    path = args[0]
    word = args[1]
    
    line = "Generation of report for precious and unprecious metals files for {}".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    
    fileDict = DoUnzip(path, word, PRECIOUS | UNPRECIOUS)
    try:
        print("Generating report......", end = "", flush = True)
        gr.SavePreciousMetalsReport(path, word)
        print("done\n")
    except Exception as e:
        raise
    finally:
        DoRemoveAndMaybeZip(path, word, fileDict)
    
def DoAggregatedSentencesReport(args):
    path = args[0]
    word = args[1]
    
    line = "Generation of aggregated sentence report for {}".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    
    datafileTypes = TRAIN | TEST | BITMORPH | MLP | NMIN | PRECIOUS | UNPRECIOUS
    fileDict = DoUnzip(path, word, datafileTypes)
    try:
        print("Generating report......", end = "", flush = True)
        gr.CreateAggregatedSentenceReport(path, word)
        print("done\n")
    except Exception as e:
        raise
    finally:
        DoRemoveAndMaybeZip(path, word, fileDict)

        
# Insertion of fake case/sentence
def DoInsertFakeTypeSentence(args):
    path = args[0]
    word = args[1]
    
    line = "Insertion of a fake word-type and appropriate sentence for {}".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    
    reZip = True
    fileDict = DoUnzip(path, word, TRAIN)
    try:
        print("Inserting fake word-type and sentence......", end = "", flush = True)
        fakeDict = ifws.InsertFakeTypeSentence(path, word)
        print("done\n")
        
        print("Fake word-type created: %s" % (fakeDict["fakeType"]))
        print("Fake line inserted into the cases file: %s" % (fakeDict["lineToWrite"]))
        print("Fake line inserted into the training datafile: %s" % (fakeDict["fakeLine"]))
        print("")
    except Exception as e:
        reZip = False
        raise
    finally:
        if (reZip):
            DoRemoveAndMaybeZip(path, word, fileDict, TRAIN)
        else:
            DoRemoveAndMaybeZip(path, word, fileDict)
    

# Fixes whitspace issues in tagged sheets
def DoFixSheets(args):
    path = args[0]
    word = args[1]
    
    line = "Fixing tagged sheets for {}".format(word)
    print("{}\n{}".format(line, "-" * len(line)))
    
    try:
        du.FixTaggedSheets(path, word)
        print("")
    except Exception as e:
        raise    