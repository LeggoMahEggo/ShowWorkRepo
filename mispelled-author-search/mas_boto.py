import boto3
import sys
import os
from os.path import join

# Returns only the filename part of a full path
# fullPath: the full path to the file
def path_leaf(fullPath):
    head, tail = os.path.split(fullPath)
    return tail or os.path.basename(head)

# A class that manages s3 operations
class S3Manager():
    def __init__(self, bucketName, enableVersioning = True):
        self.s3 = boto3.resource("s3")
        self.bucketName = bucketName
        
        # Whether or not to allow versioning (ie files aren't deleted, only latest version is available)
        self.versioning = self.s3.BucketVersioning(self.bucketName)
        if (enableVersioning): 
            self.versioning.enable()
        else:
            self.versioning.suspend()
        
        # The bucket object we are working with
        self.bucket = self.s3.Bucket(self.bucketName)
    
    # Enable/disable versioning of 'Objects' (preferable to enable in the event of messing up file data)
    def EnableVersioning(self):
        self.versioning.enable()
    def DisableVersioning(self):
        self.versioning.suspend()
    
    # Returns a list of 'Object' items from the s3 bucket
    # substr (optional): a subtring to look for in the 'Objects' (ie files) in the s3 bucket (default empty)
    # exactSearch (optional): whether or not 'Objects' being looked for match 'substr' EXACTLY (default False)
    def GetObjectsInBucket(self, substr = "", exactSearch = False):
        objList = []
        
        for obj in self.bucket.objects.all():
            # Get objects by a substring in their key
            if (substr == "" or
               (substr != "" and exactSearch == False and substr in obj.key) or
               (substr != "" and exactSearch == True and substr == obj.key)
               ):
                s3Object = self.s3.Object(self.bucketName, obj.key)
                objList.append(s3Object)

        return objList
    
    # Downloads a file from the s3 bucket
    # objName: the 'key' (ie filename) of the object to download from the bucket
    # downloadPath: the path to download the object to
    def DownloadObjectFromBucket(self, objName, downloadPath):
        s3Object = self.s3.Object(self.bucketName, objName)
        s3Object.download_file(join(downloadPath, objName))
        return downloadPath + objName
    
    # Uploads a file to the s3 bucket
    # objName: the 'key' (ie filename) of the object to upload to the bucket
    # data: the file's data (an io object, use 'open()')
    def UploadObjectToBucket(self, objName, data):
        return self.bucket.put_object(Key = path_leaf(objName), Body = data)

    # Places a 'delete marker' in an object in the s3 bucket (not a true deletion)
    # objName: the 'key' (ie filename) of the object in the bucket
    def DeleteObjectFromS3(self, objName):
        objectToDelete = self.s3.Object(self.bucketName, objName)
        return objectToDelete.delete()