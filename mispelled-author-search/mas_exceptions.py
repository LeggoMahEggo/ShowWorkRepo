# Module for user-defined exceptions
# Base class
class Error(Exception):
   """Base class for other exceptions"""
   pass
   
class DirectoryNotFoundError(Error):
    """Raised when a directory to be used does not exist"""
    pass
    
class DatabaseEmptyError(Error):
    """Raised when a database being loaded is empty"""
    pass
    
class FileTypeError(Error):
    """Raised when a file with the wrong extension is loaded"""
    pass