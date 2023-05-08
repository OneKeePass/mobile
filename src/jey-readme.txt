watchman watch-del-all  ( To clear the watchman cache)

Sometime need to do this 
watchman shutdown-server
rm -rf /usr/local/var/run/watchman/root-state  or rm -rf /usr/local/var/run/watchman/username-state
See https://stackoverflow.com/questions/72609347/how-to-resolve-error-watchman-error-watchmanrootresolveerror ( somewhat old one )

yarn start 
------

To use Calva REPL client with Krell REPL 

Start in a terminal this 
clj -A:nrepl  -M -m nrepl.cmdline --middleware '[ "cider.piggieback/wrap-cljs-repl"]'

In VSCode  Start/Connect to a REPL Server -> Connect to a running REPL, not in your project  -> "deps.edn + Krell" and then enter the nREPL port

The simulator needs to be running for the Calva repl to connect to cljs 


If we need to install or update 'clj', we need to do 

brew install clojure/tools/clojure

======================================
Update to 0.70.5
Follwed the changes mentioned in 
https://react-native-community.github.io/upgrade-helper/?from=0.70.1&to=0.70.5

Initial auto upgrade did not work because of ruby version ( see org doc and readme.txt for more details)

After updating package.json, used
yarn install

then we ned to do
cd ios 
arch -x86_64 pod install --repo-update


------
info React Native v0.70.3 is now available (your project is running on v0.70.1).
info Changelog: https://github.com/facebook/react-native/releases/tag/v0.70.3
info Diff: https://react-native-community.github.io/upgrade-helper/?from=0.70.1
info To upgrade, run "react-native upgrade".


===========================
Android 

https://developer.android.com/studio/debug/device-file-explorer
To see app specifc files in emulator, In Android Studio

Tools -> Device Manager ->  data -> data -> com.onekeepassmobile -> 

we can see dirs 
cache
files
databases 
etc

When exploring a device's files, the following directories are particularly useful:

data/data/app_name/
    Contains data files for your app stored on internal storage
sdcard/
    Contains user files stored on external user storage 


In Android we see this Warning in log as we pass the 'FileDescriptor' to rust which uses and closes that 
2022-10-25 11:05:49.213 10263-11188/com.onekeepassmobile W/ParcelFileDescriptor: Peer expected signal when closed; unable to deliver after detach


=====================================

File Access flow 

iOS
New kdbx creation flow
----------------------
First 'pickDirectory' from 'OkpDocumentPickerService' needs to be called

User picks a dir 
e.g 
file:///Users/jeyasankar/Library/Developer/CoreSimulator/Devices/EA356BA6-E229-46B2-8FB8-C41A9EB210CB/data/Containers/Shared/AppGroup/7ABFBED0-73F2-47A1-A0C9-3EBE7CDFBD7F/File%20Provider%20Storage/My%20Folder1/

Call comes back to frontend with the picked  dir path url

With that dir path url and the kdbx file name (somedbname.kdbx) 
'createKdbx' of OkpDbService is called 

Open an existing Kdbx file first time
------------------------------------
'pickKdbxFile' of 'OkpDocumentPickerService' is called 
User is presented with a view to select a kdbx file 
User selects a file. The selected file url is bookmarked for future use 
Call comes back to frontend with the picked full file url
With that picked full file url 
'readKdbx' of OkpDbService is called 



Android
New kdbx creation flow
----------------------
'pickKdbxFileToCreate' from 'OkpDocumentPickerService' needs to be called with kdbx file name (somedbname.kdbx)

User presented with a picker view with that file name and 'Save' button in any selected external folder

User clicks the save button

Call comes back to the frontend with the complete file url 
e.g "content://com.android.externalstorage.documents/document/primary%3ADocuments%2FTest2.kdbx"

In case 'Test2.kdbx' already exist in that location, the return url will have file as 'Test2.kdbx(1)'
Need to ask the user to choose another folder or remove the existing file before new db creation

With that full file url 'createKdbx' of OkpDbService is called - The parameters passed in android version of createKdbx from ios


Open an existing Kdbx file first time
------------------------------------
'pickKdbxFileToOpen' of 'OkpDocumentPickerService' is called 
User is presented with a view to select a kdbx file 
User selects a file. 
Call comes back to frontend with the picked full file url
With that picked full file url 
'readKdbx' of OkpDbService is called 