from mas_searches import HebrewSoundexFilter, EnglishSoundexFilter, GetHebrewSoundexAnalyzer, GetEnglishSoundexAnalyzer
import pandas as pd
import os
from os.path import join
from whoosh.fields import *
from whoosh.index import create_in, open_dir


# Loads Footprints .csv file(s) into a dataframe
# path: the path to the .csv files
# filenames: a list of .csv filenames
def LoadDatabaseFromCSV(path, filenames):

    # Setup the dataframe. 'id' for their Footprints id, 'name' of the entry, and the 'type' ('people', 'places', 'imprints')
    databaseDF = pd.DataFrame(columns = ["id", "name", "type"])
    #hebChars = hh.GetHebrewLetters()
    #engChars = [chr(i) for i in range(65, 90 + 1)] + [chr(i) for i in range(97, 122 + 1)]
    
    #notEngChars = ""
    for file in filenames:
        lineID = []
        lineName = []
        lineType = []
        
        with open(join(path, file), "r", encoding = "utf-8") as fReader:
            for line in fReader:
                if (line.strip() == ""):
                    continue
                
                # First entry is id, then name of the record
                splitByID = re.split(",", line.strip(), 1)
                idToInt = "".join([num for num in splitByID[0] if (str(num).isdigit())])
                
                # If the id of the record doesn't exist (there are a few records like that), make it -1, other convert
                #     to int
                if (not idToInt.isdigit()):
                    idToInt = -1
                else:
                    idToInt = int(idToInt)
                
                lineID.append(idToInt)
                name = splitByID[-1].strip()
                
                #for char in name:
                #    if (char not in engChars and char not in hebChars and char not in [" ", "\t", "\n"]):
                #        if (char not in notEngChars): notEngChars += char
                
                lineName.append(name)
                lineType.append(file[:-4]) # Get the type of record from the filename
        
        # Put the current file's data into a dataframe, then append it to the database dataframe
        fileDF = pd.DataFrame(data = {"id": lineID, "name": lineName, "type": lineType})    
        databaseDF = pd.concat([databaseDF, fileDF], sort = False)

    databaseDF = databaseDF.sort_values(by = ["id"])
    #return {"df": databaseDF[databaseDF["id"] > 0], "notEngChars": notEngChars}
    return databaseDF[databaseDF["id"] > 0]
        
# Create/Load a schema and return an index using whoosh
# pathToIndex: the path that the index sits in
# dbDF: the database dataframe for Footprints
def CreateOrLoadSchemaAndGetIndex(pathToIndex, dbDF, recreateIndex = False):
        
    # Load or create index for searching
    ix = None
    if (not recreateIndex):
        ix = open_dir(pathToIndex)
    else:
    
        # Soundex analyzers
        hebAnalyzer = GetHebrewSoundexAnalyzer()
        engAnalyzer = GetEnglishSoundexAnalyzer()

        # Create the schema
        schema = Schema(recordName = TEXT(stored = True),
                        hebNameSX = TEXT(stored = True, analyzer = hebAnalyzer),
                        engNameSX = TEXT(stored = True, analyzer = engAnalyzer),
                        recordID = NUMERIC(stored = True),
                        recordType = TEXT(stored = True))
    
        # Create the index
        ix = create_in(pathToIndex, schema)
        
        # Add data to the schema
        with ix.writer() as writer:
        
            # Add data to documents
            for i in range(0, len(dbDF)):
                row = dbDF.iloc[i]
                
                writer.add_document(recordName = row["name"], 
                                    hebNameSX = row["name"], 
                                    engNameSX = row["name"], 
                                    recordID = row["id"], 
                                    recordType = row["type"])
    return ix    