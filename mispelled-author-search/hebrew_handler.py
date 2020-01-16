
# Returns the number of digits a number has
def GetDigits(num):
    if (num > 1):
        return 1 + GetDigits(num/10)
    elif (num == 1):
        return 1
    else:
        return 0


# Returns a list of hebrew letters, specifically aleph-tav, backwards nun, and double vav/yud, and vav-yud
def GetHebrewLetters(noSpecial = False, noSofit = False):
    hebList = []
    hebLen = 1514 - 1488

    # Add all the regular hebrew letters
    for i in range(0, hebLen + 1):
        hebList.append(u"{}".format(chr(1488 + i)))
    
    # Possibly remove sofit letters
    if (noSofit):
        sofitList = ["ך", "ם", "ן", "ף", "ץ"]
        hebList = [let for let in hebList if (let not in sofitList)]
    
    if (not noSpecial):
        # Add backwards nun
        hebList.append(u"{}".format(chr(int("0x5C6", 0))))
    
        # Add double-vav, vav-yud, and double-yud
        hebList.append(u"{}".format(chr(int("0x5F0", 0))))
        hebList.append(u"{}".format(chr(int("0x5F1", 0))))
        hebList.append(u"{}".format(chr(int("0x5F2", 0))))
    
    return hebList


# Returns the hebrew letter equivalent of a sofit letter
def HandleSofitLetter(letter):
    sofitDict = {"ך": "כ", "ם": "מ", "ן": "נ", "ף": "פ", "ץ": "צ"}
    
    if (letter in sofitDict.keys()):
        return sofitDict[letter]
    else:
        return letter


# Gets number that the hebrew letter represents
def GetGematriaOfLetter(letter):
    letter = HandleSofitLetter(letter)
    hebList = GetHebrewLetters(noSpecial = True, noSofit = True)
    letterPos = hebList.index(letter) + 1 # There are 22 hebrew letters not including sofit letters
    
    if (letterPos <= 10):
        return letterPos
    elif (letterPos >= 11 and letterPos <= 19):
        return (letterPos - 9) * 10
    elif (letterPos >= 20):
        lastGematria = [200, 300, 400]
        return lastGematria[letterPos - 20]
    
# Gets the hebrew letter by the number that is represented by it
def GetLetterFromGematria(gematria):
    hebList = GetHebrewLetters(noSpecial = True, noSofit = True)
    
    lastGematria = [200, 300, 400]
    if (gematria in lastGematria):
        return hebList[20 + lastGematria.index(gematria) - 1]
    elif (gematria >= 20 and gematria <= 100 and gematria % 10 == 0):
        return hebList[int(gematria/10) + 9 - 1]
    elif (gematria <= 10):
        return hebList[gematria - 1]

# Gets decimal number of pasuk
def GetPasukNumber(pasuk):
    pasukNumber = 0
    hebList = GetHebrewLetters(noSpecial = True, noSofit = True)
    
    for letter in pasuk:
        pasukNumber += GetGematriaOfLetter(letter)

    return pasukNumber


# Gets the pasuk from the number listed
def GetPasukFromNumber(totalGematria):
    hebList = GetHebrewLetters(noSpecial = True, noSofit = True)
    
    pasuk = ""
    totalDigits = GetDigits(totalGematria)
    digits = [int(d) for d in str(totalGematria)]
    
    for i in range(1, totalDigits + 1):
        n = totalDigits - i
        
        gematria = digits[i - 1] * (10 ** n)
        if (gematria == 0): continue
        else:
            pasuk += GetLetterFromGematria(gematria)
    
    # For 15/16, put in טו/טז
    check1516 = int(str(totalGematria)[-2:])
    if (check1516 == 15 or check1516 == 16):
        if (check1516 == 15): pasuk = pasuk[:-2] + "טו"
        elif (check1516 == 16): pasuk = pasuk[:-2] + "טז"
    
    return pasuk
    
# Returns a dictionary with the english names for sifrei tanach serving as keys for the hebrew names
# englishToHebrew (optional): if the keys of the dictionary are in english (so that the values are hebrew). Default True
def GetTanachDict(englishToHebrew = True):
    torahEnglish = ["Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy"]
    torahHebrew = ["בראשית", "שמות", "ויקרא", "במדבר", "דברים"]
    
    neviimEnglish = ["Joshua", "Judges", "I_Samuel", "II_Samuel", "I_Kings", "II_Kings",
                     "Isaiah", "Jeremiah", "Ezekiel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", 
                     "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi"]
    neviimHebrew = ["יהושע", "שופטים", "שמואל א", "שמואל ב", "מלכים א", "מלכים ב",
                     "ישעיהו", "ירמיהו", "יחזקאל", "הושע", "יואל", "עמוס", "עובדיה", "יונה", "מיכה", 
                     "נחום", "חבקוק", "צפניה", "חגי", "זכריה", "מלאכי"]
    
    kesuvimEnglish = ["Psalms", "Proverbs", "Job", "Song_of_Songs", "Ruth", "Lamentations", "Ecclesiastes", "Esther", 
                      "Daniel", "Ezra", "Nehemiah", "I_Chronicles", "II_Chronicles"]
    kesuvimHebrew = ["תהילים", "משלי", "איוב", "שיר השירים", "רות", "איכה", "קהלת", "אסתר", 
                      "דניאל", "עזרא", "נחמיה", "דברי הימים א", "דברי הימים ב"]
    
    torahDict = dict(zip(torahEnglish,torahHebrew)) if (englishToHebrew) else dict(zip(torahHebrew,torahEnglish))
    neviimDict = dict(zip(neviimEnglish,neviimHebrew)) if (englishToHebrew) else dict(zip(neviimHebrew,neviimEnglish))
    kesuvimDict = dict(zip(kesuvimEnglish,kesuvimHebrew)) if (englishToHebrew) else dict(zip(kesuvimHebrew,kesuvimEnglish))
    
    return {**torahDict, **neviimDict, **kesuvimDict}