//
//  Configuration.swift
//  OneKeePassMobile
//
//  Created  on 12/26/24.
//

import Foundation

enum XcodeConfiguration {

    // Note we need to add the key 'DEVELOPMENT_TEAM' to App's Info.plist and also in AutoFill extension's Info.plist
    // The value for DEVELOPMENT_TEAM is defined in the build settings and that is pulled set in $(DEVELOPMENT_TEAM)
    // Ref: https://stackoverflow.com/questions/11726672/access-app-identifier-prefix-programmatically
    static var developmentTeam: String {
      string(for: "DEVELOPMENT_TEAM")
    }

    // MARK: - Helper Methods

    static private func string(for key: String) -> String {
        Bundle.main.infoDictionary?[key] as! String
    }

}
