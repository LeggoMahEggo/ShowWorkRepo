# Checks the type of a variable, through strType
# Types checked: string (str), integer (int), floating-point (float), list (list), dictionary (dict)
# var: the variable being checked
# strType: the type to check for
def CheckIfTypeByString(var, strType):
    
    if (strType == "str" and type(var) is str):
        return True
    if (strType == "int" and var.isdigit()):
        return True
    if (strType == "float" and type(var) is float):
        return True
    if (strType == "list" and type(var) is list):
        return True        
    if (strType == "dict" and type(var) is dict):
        return True    

    return False

# Makes sure that the form data is correct
# form: the data sent by the POST request (in dictionary form)
def CheckFormData(form):
    errMsgs = []
    
    # Setup required fields and their datatypes
    reqFields = {"search_text": "str", "lang": "str", "search_types": "list"}
    #nonReqFields = {"max_score": "float"}
    nonReqFields = {}
    
    # CORRECT FIELDS
    reqFieldsNames = list(reqFields.keys())
    reqFieldExistStatus = {}
    for field in reqFieldsNames:
        reqFieldExistStatus[field] = "No"
    
    # Check form fields against required fields
    formFields = list(form.keys())
    for field in formFields:
        if (field in reqFieldsNames):
            reqFieldExistStatus[field] = "Yes"
    
    missingFields = []
    for field,status in reqFieldExistStatus.items():
        if (status != "Yes"):
            missingFields.append(field)
    
    if (len(missingFields) > 0):
        for missingField in missingFields:
            errMsgs.append("Form is missing the field: '%s'" % (missingField))
        
    # CORRECT TYPES OF DATA
    # Add non-required fields
    incorrectFields = []
    withNonReqFields = dict(reqFields)
    withNonReqFields.update(nonReqFields)
    
    # Check each field for correct datatypes
    for field,data in form.items():
        if (field in withNonReqFields.keys()):
            
            strType = withNonReqFields[field]
            correctType = CheckIfTypeByString(data, strType)
        
            if (not correctType):
                incorrectFields.append(field)
            
    if (len(incorrectFields) > 0):
        for incorrectField in incorrectFields:
            errMsgs.append("'%s' is of incorrect type (must be '%s')" % (incorrectField, withNonReqFields[incorrectField]))
    
    return errMsgs