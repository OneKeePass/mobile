(ns onekeepass.mobile.constants)

;; This is the default Entry type to use 
(def UUID_OF_ENTRY_TYPE_LOGIN "ffef5f51-7efc-4373-9eb5-382d5b501768")

;; This is the entry type id for Auto Open entry type
(def UUID_OF_ENTRY_TYPE_AUTO_OPEN "389368a9-73a9-4256-8247-321a2e60b2c7")

(def UUID-DEFAULT "00000000-0000-0000-0000-000000000000")

;; Standard Entry Type Names
;; These should match names used in 'standard_entry_types.rs'

(def LOGIN_TYPE_NAME "Login")
(def CREDIT_DEBIT_CARD_TYPE_NAME "Credit/Debit Card")
(def WIRELESS_ROUTER_TYPE_NAME "Wireless Router")
(def PASSPORT_TYPE_NAME "Passport")
(def BANK_ACCOUNT_TYPE_NAME "Bank Account")
(def AUTO_DB_OPEN_TYPE_NAME "Auto Database Open")

(def STANDARD_ENTRY_TYPES [LOGIN_TYPE_NAME
                           CREDIT_DEBIT_CARD_TYPE_NAME
                           WIRELESS_ROUTER_TYPE_NAME
                           BANK_ACCOUNT_TYPE_NAME
                           AUTO_DB_OPEN_TYPE_NAME])

;; Based on the enum 'EntryCategory'
(def CATEGORY_ALL_ENTRIES "AllEntries")
(def CATEGORY_FAV_ENTRIES "Favorites")
(def CATEGORY_DELETED_ENTRIES "Deleted")

(def ADDITIONAL_ONE_TIME_PASSWORDS "Additional One-Time Passwords")

;; Labels used for entry grouping section title 
(def GEN_SECTION_TITLE "General")
(def TYPE_SECTION_TITLE "Types")
(def TAG_SECTION_TITLE "Tags")
(def CAT_SECTION_TITLE "Categories")
(def GROUP_SECTION_TITLE "Groups")

(def PERMISSION_REQUIRED_TO_READ "PERMISSION_REQUIRED_TO_READ")
(def FILE_NOT_FOUND "FILE_NOT_FOUND")

(def OTP_KEY_DECODE_ERROR "OtpKeyDecodeError")

(def BIOMETRIC-AUTHENTICATION-SUCCESS "AuthenticationSucceeded")
(def BIOMETRIC-AUTHENTICATION-FAILED "AuthenticationFailed")

(def THEME "theme")
(def DARK-THEME "dark")
(def LIGHT-THEME "light")
(def DEFAULT-SYSTEM-THEME "system")

;; Sorting related 
(def TITLE "Title")
(def MODIFIED_TIME "Modified Time")
(def CREATED_TIME "Created Time")
(def PASSWORD "Password")
(def USERNAME "UserName")
(def URL "URL")
(def IFDEVICE "IfDevice")

(def ASCENDING "Ascending")
(def DESCENDING "Descending")

(def ONE_TIME_PASSWORD_TYPE "Field type" "OneTimePassword")

(def OTP "Standard field name used" "otp")

(def OTP_URL_PREFIX "otpauth://totp")

;; 'granted' | 'not-determined' | 'denied' | 'restricted'
(def CAMERA_PERMISSION_GRANTED "granted")
(def CAMERA_PERMISSION_DENIED "denied")
(def CAMERA_PERMISSION_NOT_DETERMINED "not-determined")
(def CAMERA_PERMISSION_RESTRICTED "restricted")


;;;;;;;;;;   Page ids
(def CAMERA_SCANNER_PAGE_ID :scan-otp-qr)
(def AUTOFILL_SETTINGS_PAGE_ID :autofill-settings)

(def BIOMETRIC_SETTINGS_PAGE_ID :biometric-settings)

(def ADDITIONAL_DATABASE_ACCESS_SETTINGS_PAGE_ID :additional-db-access-settings)

;; All remote storage related page ids
(def RS_CONNECTIONS_LIST_PAGE_ID :rs-connections-list)
;; Key :sftp or :webdav will determine which form data to use on this page
(def RS_CONNECTION_CONFIG_PAGE_ID :rs-connection-config)
(def RS_FILES_FOLDERS_PAGE_ID :rs-files-folders)

;;;;;;;;;;


(def TR-KEY-AUTOFILL 'autoFill)



;;;;;;;;;;  Variants for the enums used in enum dispatches in rust side ;;;;;;;;;;

;; from enum PickedFileHandler from file_util module in db-service-ffi crate
(def V-SFTP-PRIVATE-KEY-FILE "SftpPrivateKeyFile")

;; tag used in the enum serialization/deserialization of PickedFileHandler
(def PICKED-FILE-HANDLER-TAG :handler)


;; from enum RemoteStorageOperationType
(def V-SFTP "Sftp")
(def V-WEBDAV "Webdav")
;; tag used in the enum serialization/deserialization of RemoteStorageOperationType
(def REMOTE-STORAGE-OPERATION-TYPE-TAG :type)

;;;;;;;; 

(def BROWSE-TYPE-DB-OPEN :db-open)
(def BROWSE-TYPE-DB-NEW :db-new)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Icon names ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; react-native-vector-icons is used to display the icons
;; We need to get the name of icons from https://materialdesignicons.com/ ( new url https://pictogrammers.com/library/mdi/)
;; Need to use the lower case name with "-" 

;; Also see onekeepass.mobile.icons-list where the Entry and Group Icons listed

(def ICON-DATABASE "database")
(def ICON-DATABASE-OUTLINE "database-outline")
(def ICON-LOCKED-DATABASE "database-lock")
(def ICON-DATABASE-CHECK  "database-check")
(def ICON-DATABASE-ALERT  "database-alert")
(def ICON-DATABASE-EYE  "database-eye")
(def ICON-DATABASE-REMOVE "database-remove")
(def ICON-DATABASE-OFF "database-off")
(def ICON-DATABASE-OFF-OUTLINE "database-off-outline")
(def ICON-DATABASE-ARROW-LEFT  "database-arrow-left")

(def ICON-COG "cog")
(def ICON-COG-OUTLINE "cog-outline")
(def ICON-SORT "sort")


(def ICON-CHEVRON-RIGHT  "chevron-right")
(def ICON-CACHED  "cached")
(def ICON-CLOSE "close")

(def ICON-EYE  "eye")
(def ICON-EYE-OFF  "eye-off")

(def ICON-PLUS  "plus")

(def ICON-FILE  "file")
(def ICON-FILE-OUTLINE "file-outline")
(def ICON-FOLDER "folder")
(def ICON-FOLDER-OUTLINE "folder-outline")

(def ICON-PDF "file-pdf-box")
(def ICON-FILE-PNG  "file-png-box")
(def ICON-FILE-JPG  "file-jpg-box")
(def ICON-FILE-GIF  "file-gif-box")
(def ICON-NOTE-TEXT-OUTLINE "note-text-outline")
(def ICON-FILE-DOC-OUTLINE "file-document-outline")
(def ICON-FILE-QUESTION-OUTLINE  "file-question-outline")

(def ICON-UPLOAD-OUTLINE  "upload-outline")

(def ICON-TAGS  "tag-multiple")

;; Icons used for general entry categories
(def ICON-CHECK-ALL  "check-all")
(def ICON-HEART-OUTLINE  "heart-outline")
(def ICON-TRASH-CAN-OUTLINE  "trash-can-outline")

;; Entry Types' icons
(def ICON-LOGIN  "login")
(def ICON-BANK-OUTLINE  "bank-outline")
(def ICON-ROUTER-WIRELESS  "router-wireless")
(def ICON-CREDIT-CARD-OUTLINE  "credit-card-outline")

(def ICON-LAUNCH "launch")

;;"checkbox-outline" "checkbox-blank-outline"

(def ICON-CHECKBOX-OUTLINE  "checkbox-outline")
(def ICON-CHECKBOX-BLANK-OUTLINE  "checkbox-blank-outline")

;; (def ICON-  "")
;; (def ICON-  "")
;; (def ICON-  "")

;;(def ICON-  "")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;