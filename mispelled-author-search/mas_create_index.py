import os
from mas_database import LoadDatabaseFromCSV, CreateOrLoadSchemaAndGetIndex

# For re-creating the whoosh index
path = os.getcwd() + "/"
        
# Load Footprints database
print("Loading Footprints database")
footprintsDF = LoadDatabaseFromCSV(path, ["imprint.csv", "people.csv", "place.csv"])

# Re-create the index
print("Re-creating index")
CreateOrLoadSchemaAndGetIndex(path, footprintsDF, recreateIndex = True)

print("DONE")