# Make sure string is of appropriate type
def CheckStringType(s):
    if not isinstance(s, str):
        raise TypeError("expected str or unicode, got %s" % type(s).__name__)

# INACTIVE
# While performing the levenshtein distance algorithm, apply certain rules for the deletion/insertion costs
def CheckForCost(ch1, ch2):
    if (ch1 == "י" or ch2 == "י"):
        return 0.5
    else:
        return 1

# INACTIVE
# After applying the levenshtein distance algorithm, reduce the 'distance' based on multiple rules
def PostCostProcessing(s1, s2, startCost):
    # First letter of either string is a 'ה'
    #if ((s1[0] == "ה" or s2[0] == "ה") and (s1[1:] == s2 or s2[1:] == s1)):
    #    startCost -= 0.5
                
    return max(0, startCost)
    
    
# Levenshtein distance algorithm. Returns a value based on the amount of insertions/deletions/substitions necessary
#     to convert one string into another. This version was found online somewhere, modified by me
# s1: the original string
# s2: the string to convert s1 into
def LevenshteinDistance(s1, s2):
    CheckStringType(s1)
    CheckStringType(s2)
    
    # If both are the same, no changes made, hence 0
    if (s1 == s2):
        return 0
    
    # Set length of rows and columns
    rows = len(s1) + 1
    cols = len(s2) + 1

    # Return length of opposite string if one of them is empty
    if (not s1):
        return cols - 1
    if (not s2):
        return rows - 1
    
    # Voodo stuff
    prev = None 
    cur = range(cols)
    for r in range(1, rows):
        prev, cur = cur, [r] + [0]*(cols-1)
        
        for c in range(1, cols):
            deletion = prev[c] + 1#CheckForCost(s1[r-1], s2[c-1])
            insertion = cur[c-1] + 1#CheckForCost(s1[r-1], s2[c-1])
            edit = prev[c-1] + (0 if s1[r-1] == s2[c-1] else 2)
            cur[c] = min(edit, deletion, insertion)

    return PostCostProcessing(s1, s2, cur[-1])