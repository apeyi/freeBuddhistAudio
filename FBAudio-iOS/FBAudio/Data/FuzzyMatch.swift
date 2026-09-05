import Foundation

/// Typo-tolerant name matching for speakers, places and collections in search
/// ("adhistana" → Adhisthana). Substring matches always count; otherwise the
/// query may differ from a word of the name (or its prefix) by a small edit
/// distance that scales with the query length. Mirrors Android's FuzzyMatch.
enum FuzzyMatch {

    static func normalize(_ s: String) -> String {
        s.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: nil)
            .lowercased()
            .trimmingCharacters(in: .whitespaces)
    }

    /// Allowed typos: none for short queries, one from 5 characters, two from 8.
    static func allowedEdits(_ queryLength: Int) -> Int {
        queryLength >= 8 ? 2 : (queryLength >= 5 ? 1 : 0)
    }

    static func matches(_ query: String, _ name: String) -> Bool {
        let q = normalize(query)
        guard !q.isEmpty else { return false }
        let n = normalize(name)
        if n.contains(q) { return true }
        let allowed = allowedEdits(q.count)
        if allowed == 0 { return false }
        let words = n.split(whereSeparator: { !$0.isLetter && !$0.isNumber }).map(String.init) + [n]
        let qc = Array(q)
        for w in words {
            let wc = Array(w)
            if distance(qc, wc) <= allowed { return true }
            if wc.count > qc.count {
                if distance(qc, Array(wc.prefix(qc.count))) <= allowed { return true }
                if distance(qc, Array(wc.prefix(qc.count + 1))) <= allowed { return true }
            }
        }
        return false
    }

    /// Damerau–Levenshtein distance (transpositions count as one edit).
    static func distance(_ a: [Character], _ b: [Character]) -> Int {
        if a == b { return 0 }
        if a.isEmpty { return b.count }
        if b.isEmpty { return a.count }
        var d = Array(repeating: Array(repeating: 0, count: b.count + 1), count: a.count + 1)
        for i in 0...a.count { d[i][0] = i }
        for j in 0...b.count { d[0][j] = j }
        for i in 1...a.count {
            for j in 1...b.count {
                let cost = a[i - 1] == b[j - 1] ? 0 : 1
                d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if i > 1, j > 1, a[i - 1] == b[j - 2], a[i - 2] == b[j - 1] {
                    d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[a.count][b.count]
    }

    static func distance(_ a: String, _ b: String) -> Int { distance(Array(a), Array(b)) }
}
