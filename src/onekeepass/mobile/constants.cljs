(ns onekeepass.mobile.constants)

;; This is the default Entry type to use 
(def UUID_OF_ENTRY_TYPE_LOGIN "ffef5f51-7efc-4373-9eb5-382d5b501768")

;; Standard Entry Type Names
;; These should match names used in 'standard_entry_types.rs'

(def LOGIN_TYPE_NAME "Login")
(def CREDIT_DEBIT_CARD_TYPE_NAME "Credit/Debit Card")
(def WIRELESS_ROUTER_TYPE_NAME "Wireless Router")
(def PASSPORT_TYPE_NAME "Passport")
(def BANK_ACCOUNT_TYPE_NAME "Bank Account")

(def STANDARD_ENTRY_TYPES [LOGIN_TYPE_NAME 
                           CREDIT_DEBIT_CARD_TYPE_NAME 
                           WIRELESS_ROUTER_TYPE_NAME
                           BANK_ACCOUNT_TYPE_NAME
                           ])

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


;;; Page ids
(def CAMERA_SCANNER_PAGE_ID :scan-otp-qr)


;;;;;;; Icon names ;;;;;

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

;;"checkbox-outline" "checkbox-blank-outline"

(def ICON-CHECKBOX-OUTLINE  "checkbox-outline")
(def ICON-CHECKBOX-BLANK-OUTLINE  "checkbox-blank-outline")

;; (def ICON-  "")
;; (def ICON-  "")
;; (def ICON-  "")

;;(def ICON-  "")