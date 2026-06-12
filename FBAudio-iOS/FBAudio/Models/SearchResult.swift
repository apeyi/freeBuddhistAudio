import Foundation

struct SearchResult: Identifiable, Equatable {
    let catNum: String
    let title: String
    let speaker: String
    let imageUrl: String
    let path: String
    let year: Int

    // Includes the path: series and talk numbers are separate namespaces on FBA,
    // so two results can legitimately share a catNum (duplicate ids break ForEach).
    var id: String { "\(path)|\(catNum)" }

    init(catNum: String, title: String = "", speaker: String = "",
         imageUrl: String = "", path: String = "", year: Int = 0) {
        self.catNum = catNum
        self.title = title
        self.speaker = speaker
        self.imageUrl = imageUrl
        self.path = path
        self.year = year
    }
}
