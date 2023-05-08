import Foundation

enum CallError:String {
  case coordinateError = "E_COORDINATE_CALL_ERROR"
  
  func errorDescription(_ msg:String?) -> String {
    switch self {
    case .coordinateError: return "NSCoordinate call failed \(msg ?? "")"
    }
  }
  
  func errorDescription(_ error:NSError) -> String {
    switch self {
    case .coordinateError: return "NSCoordinate call failed \(error)"
    }
  }
  
  static func errorDescription(_ error:NSError) -> String {
    return "\(error)"
  }
}
