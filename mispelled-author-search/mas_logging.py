import datetime as dt
import logging
import os
import re
import uuid
from io import StringIO

# FILTERS
# Does not allow 'exceptions' through
class NoExceptionFilter(logging.Filter):
    def filter(self, record):
        return not (record.name == "exception")

# Only allows certain messages through for the action logger
class OnlyActionFilter(logging.Filter):
    def filter(self, record):
        return (record.name == "root")

# FORMATTERS
DEFAULT_FORMATTER = "(%(levelname)s,%(name)s) - %(asctime)s: %(message)s"
DATE_FORMATTER = "%d/%m/%Y %I:%M:%S %p"
MESSAGE_ONLY = "%(asctime)s: %(message)s"

# Remove Lambda logger
root = logging.getLogger()
if root.handlers:
    for handler in root.handlers:
        root.removeHandler(handler)

# MAIN LOGGING CLASS
class LogOp():

    # Sets up logging options
    # ipAddr: the ip address of the API request
    # route: which link the request was made to
    def __init__(self, ipAddr, route):
        # Save API call parameters
        self.__ipAddr = ipAddr
        self.__route = str(route).replace("/", ".")
        
        # Action logger
        self.__actionStream = None
        self.__actionLogger = logging.getLogger()
        if (not len(self.__actionLogger.handlers)):
            self.__actionStream = StringIO()
            self.__actionConfig = logging.StreamHandler(stream = self.__actionStream)
            self.__actionConfig.setFormatter(logging.Formatter(DEFAULT_FORMATTER, DATE_FORMATTER))
            self.__actionConfig.setLevel(logging.INFO)
            logging.basicConfig(handlers = [self.__actionConfig])
            self.__actionLogger = logging.getLogger()
            
            # Add special filter for stack traces and HTTP requests not to appear
            self.__actionLogger.handlers[0].addFilter(NoExceptionFilter()) 
        else:
            self.__actionStream = self.__actionLogger.handlers[0].stream
            self.__actionStream.truncate(0)
            self.__actionStream.seek(0)
        
        # -----------
        # Stack trace
        self.__stackStream = None
        self.__stackLogger = logging.getLogger("exception")
        if (not len(self.__stackLogger.handlers)):
            self.__stackStream = StringIO()
            self.__stackConfig = logging.StreamHandler(stream = self.__stackStream)
            self.__stackConfig.setFormatter(logging.Formatter(DEFAULT_FORMATTER, DATE_FORMATTER))
            self.__stackConfig.setLevel(logging.ERROR)
            self.__stackLogger.addHandler(self.__stackConfig)
        else:
            self.__stackStream = self.__stackLogger.handlers[0].stream
            self.__stackStream.truncate(0)
            self.__stackStream.seek(0)
        self.__LOGGERS = {"action": self.__actionLogger, "exception": self.__stackLogger}
        self.__STREAMS = {"action": self.__actionStream, "exception": self.__stackStream}
    
    # Private method. Writes a message of the appropriate level to a logging stream
    # logger: which logging stream to write to ('action': regular actions, 'exception': stack traces)
    # level: severity of the message ('info', 'error', or 'exception')
    # msg: the message to write
    def __log_base_message(self, logger, level, msg):
        if (logger not in self.__LOGGERS.keys()):
            raise KeyError("Logger '%s' does not exist!" % (logger))
        if (level != "info" and level != "error" and level != "exception"):
            raise ValueError("'level' must be 'info', 'error', or 'exception'! (argument was '%s')" % (level))
        
        theLogger = self.__LOGGERS[logger]
        if (level == "info"):
            theLogger.info(msg)
        elif (level == "error"):
            theLogger.error(msg)
        elif (level == "exception"):
            theLogger.exception(msg)
    
    # Private method. Returns the current logging messages from a logging stream.
    # stream: which stream to get the logging messages from ('action' or 'exception')
    def __get_current_stream(self, stream):
        if (stream not in self.__STREAMS.keys()):
            raise KeyError("Stream '%s' does not exist!" % (stream))
        
        theStream = self.__STREAMS[stream]
        return theStream.getvalue()
    
    # For testing, to be removed
    def BOOP(self, stream):
        if (stream not in self.__STREAMS.keys()):
            raise KeyError("Stream '%s' does not exist!" % (stream))
        
        theStream = self.__STREAMS[stream]
        return theStream.getvalue()
    
    # Private method. Saves a logging stream to a file
    # stream: which logging stream to save ('action' or 'exception')
    # filename: name of the logfile
    # savePath: the directory to save the file to
    def __save_stream_to_file(self, stream, filename, savePath):
        streamLines = re.split("\n", self.__get_current_stream(stream).strip())

        with open(os.path.join(savePath, filename), "wb") as fWriter:
            for line in streamLines:
                wLine = line + "\n"
                fWriter.write(wLine.encode("utf-8"))
    
    # Public methods for writing logging messages. First two write to 'action', last one to 'exception'
    def LogMessage(self, msg):
        self.__log_base_message("action", "info", msg)
    def LogError(self, msg):
        self.__log_base_message("action", "error", msg)
    def LogStackTrace(self, msg):
        self.__log_base_message("exception", "exception", msg)
    
    
    def CheckIfEmpty(self, stream):
        currentStream = self.__get_current_stream(stream)
        
        if (currentStream.strip() == ""):
            return True
        else:
            return False
    
    # Saves a logging stream to a file (returns the filename of the file created)
    # logger: which logging type (regular or stack trace) to save to file
    # savePath: path to where the logfile is saved
    # Filename format: 'LOGFILE - [normal/stack_trace] - [route] - [ip address] - [date] - [time] - [uuid]'
    def SaveLogFile(self, logger, savePath):
        
        # Date and time of logfile creation
        date = dt.datetime.now().strftime("%d.%m.%Y")
        time = dt.datetime.now().strftime("%H.%M.%S")
        
        # Type of logfile ('normal' or 'exception')
        logType = "normal" if (logger == "action") else logger
        
        # Unique id, in the event that two users make requests at the exact same time
        uniqueID = uuid.uuid4().hex
        
        # Set the logfile's filename
        filename = "LOGFILE - %s - %s - %s - %s - %s - %s.txt" % (logType, self.__route, self.__ipAddr, date, time, uniqueID)
        
        self.__save_stream_to_file(logger, filename, savePath)
        
        return filename