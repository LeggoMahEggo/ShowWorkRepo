import pandas as pd
import tagshegen_new as tsg
import sys
import re
import os
import zipfile
import pkg_resources

dists = [d for d in pkg_resources.working_set]
print(dists)
sys.exit()


wordList = [f for f in listdir(path) if os.path.isdir(join(path, f))]
wordList = [f for f in wordList if (f != "את" and f != "OTHER")] # Ignoring את for now
yesCount = 0
noCount = 0
yesList = []
noList = []

for word in wordList:
    fullPath = path + word + "\\"
    dirList = [f for f in listdir(fullPath) if os.path.isdir(join(fullPath, f))]
    
    if ("precious" in dirList):
        hasPrecious = "%s: Yes" % (word)
        yesList.append(word)
        yesCount += 1
    else:
        hasPrecious = "%s: No" % (word)
        noList.append(word)
        noCount += 1
    print(hasPrecious)
print("Yes: %s\nNo: %s" % (yesCount, noCount))
print("Yes\n---")
for x in yesList:
    print(x)
print("No\n--")
for x in noList:
    print(x)