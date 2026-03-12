import Foundation
import SwiftSoup

actor FBAScraper {
    private static let baseUrl = "https://www.freebuddhistaudio.com"

    private let session: URLSession

    init() {
        let config = URLSessionConfiguration.default
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
            "Referer": Self.baseUrl + "/",
        ]
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        session = URLSession(configuration: config)
    }

    // MARK: - HTML Fetching

    private func fetchHtml(_ urlString: String) async throws -> String {
        guard let url = URL(string: urlString) else { throw ScraperError.invalidUrl(urlString) }
        let (data, response) = try await session.data(from: url)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw ScraperError.httpError((response as? HTTPURLResponse)?.statusCode ?? 0)
        }
        guard let html = String(data: data, encoding: .utf8) else {
            throw ScraperError.emptyResponse
        }
        return html
    }

    // MARK: - JSON Extraction from HTML

    private func extractFbaJson(_ html: String, key: String) -> [String: Any]? {
        let marker = "document.__FBA__.\(key)"
        guard let range = html.range(of: marker) else { return nil }
        let rest = html[range.upperBound...]
        guard let eqRange = rest.range(of: "=") else { return nil }
        let afterEq = rest[eqRange.upperBound...]
        guard let braceIdx = afterEq.firstIndex(of: "{") else { return nil }
        guard let jsonStr = extractBalanced(String(afterEq[braceIdx...]), open: "{", close: "}") else { return nil }
        guard let data = jsonStr.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return obj
    }

    private func extractFbaJsonArray(_ html: String, key: String) -> [[String: Any]] {
        let marker = "document.__FBA__.\(key)"
        guard let range = html.range(of: marker) else { return [] }
        let rest = html[range.upperBound...]
        guard let eqRange = rest.range(of: "=") else { return [] }
        let afterEq = rest[eqRange.upperBound...]
        guard let bracketIdx = afterEq.firstIndex(of: "[") else { return [] }
        guard let jsonStr = extractBalanced(String(afterEq[bracketIdx...]), open: "[", close: "]") else { return [] }
        guard let data = jsonStr.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return arr
    }

    private func extractBalanced(_ data: String, open: Character, close: Character) -> String? {
        var depth = 0
        var inString = false
        var escape = false
        let chars = Array(data)
        for i in 0..<chars.count {
            let c = chars[i]
            if escape { escape = false; continue }
            if c == "\\" && inString { escape = true; continue }
            if c == "\"" { inString = !inString; continue }
            if !inString {
                if c == open { depth += 1 }
                else if c == close {
                    depth -= 1
                    if depth == 0 { return String(chars[0...i]) }
                }
            }
        }
        return nil
    }

    private func unescape(_ str: String) -> String {
        guard let doc = try? SwiftSoup.parse(str) else { return str }
        return (try? doc.text()) ?? str
    }

    nonisolated private func resolveUrl(_ url: String) -> String {
        if url.isEmpty { return "" }
        return url.hasPrefix("http") ? url : Self.baseUrl + url
    }

    private func str(_ dict: [String: Any], _ key: String) -> String? {
        dict[key] as? String
    }

    private func int(_ dict: [String: Any], _ key: String) -> Int? {
        if let i = dict[key] as? Int { return i }
        if let s = dict[key] as? String { return Int(s) }
        return nil
    }

    // MARK: - Talk Detail

    func fetchTalkDetail(_ catNum: String) async throws -> Talk {
        let html = try await fetchHtml("\(Self.baseUrl)/audio/details?num=\(catNum)")
        guard let json = extractFbaJson(html, key: "talk") else {
            throw ScraperError.parseError("No talk JSON found")
        }
        return parseTalk(json, html: html)
    }

    private func parseTalk(_ json: [String: Any], html: String = "") -> Talk {
        let catNum = str(json, "catNum") ?? str(json, "cat_num") ?? ""
        let title = unescape(str(json, "title") ?? "")
        let speaker: String
        if let s = str(json, "speaker") {
            speaker = unescape(s)
        } else if let speakers = json["speakers"] as? [String], let first = speakers.first {
            speaker = unescape(first)
        } else {
            speaker = ""
        }
        let year = int(json, "year") ?? 0
        let genre = str(json, "genre") ?? str(json, "genre1") ?? ""
        let duration = max(int(json, "durationSeconds") ?? int(json, "duration") ?? 0, 0)
        let imageUrl = str(json, "image") ?? str(json, "imageUrl") ?? str(json, "image_url") ?? ""

        let rawDesc = str(json, "blurb") ?? str(json, "description") ?? ""
        let description: String
        if rawDesc.contains("<") {
            let doc = try? SwiftSoup.parse(rawDesc)
            description = (try? doc?.text())?.trimmingCharacters(in: .whitespacesAndNewlines) ?? unescape(rawDesc)
        } else {
            description = unescape(rawDesc)
        }

        let tracks = parseTracksArray(json)
        let audioUrl = tracks.first?.audioUrl ?? "\(Self.baseUrl)/audio/stream?num=\(catNum)"

        // Transcript URL
        let transcriptUrl: String
        let fromJson = str(json, "transcriptHref") ?? str(json, "text_url") ?? str(json, "textUrl") ??
                        str(json, "transcriptUrl") ?? str(json, "transcript_url") ?? ""
        if !fromJson.isEmpty {
            transcriptUrl = resolveUrl(fromJson)
        } else if !html.isEmpty, let doc = try? SwiftSoup.parse(html),
                  let link = try? doc.select("a[href*=/texts]").first(),
                  let href = try? link.attr("href"), !href.isEmpty {
            transcriptUrl = resolveUrl(href)
        } else {
            transcriptUrl = ""
        }

        // Series
        let seriesTitle: String
        let seriesHref: String
        if let seriesObj = json["series"] as? [String: Any] {
            seriesTitle = unescape(str(seriesObj, "title") ?? "")
            seriesHref = str(seriesObj, "href") ?? ""
        } else if let seriesStr = json["series"] as? String {
            seriesTitle = unescape(seriesStr)
            seriesHref = ""
        } else {
            seriesTitle = ""
            seriesHref = ""
        }

        return Talk(
            catNum: catNum, title: title, speaker: speaker, year: year,
            genre: genre, durationSeconds: duration, imageUrl: resolveUrl(imageUrl),
            audioUrl: resolveUrl(audioUrl), description: description, tracks: tracks,
            transcriptUrl: transcriptUrl, series: seriesTitle, seriesHref: seriesHref
        )
    }

    private func parseTracksArray(_ json: [String: Any]) -> [Track] {
        guard let tracksArr = json["tracks"] as? [[String: Any]] else { return [] }
        return tracksArr.compactMap { t in
            guard let audio = t["audio"] as? [String: Any],
                  let mp3 = audio["mp3"] as? String else { return nil }
            return Track(
                title: unescape(str(t, "title") ?? ""),
                durationSeconds: max(int(t, "durationSeconds") ?? 0, 0),
                audioUrl: resolveUrl(mp3)
            )
        }
    }

    // MARK: - Browse

    nonisolated func fetchBrowseCategories() -> [BrowseCategory] {
        var categories: [BrowseCategory] = []

        categories.append(BrowseCategory(id: "Sangharakshita", name: "Sangharakshita",
                                          type: .sangharakshita, browseUrl: "sang://root"))
        categories.append(BrowseCategory(id: "mitra_study", name: "Mitra Study",
                                          type: .mitraStudy, browseUrl: "mitra://study"))

        let topics = ["Meditation", "Mindfulness", "Wisdom", "Ethics", "Sangha",
                       "The Buddha", "Dharma", "Devotion", "Death", "Relationships",
                       "Impermanence", "Compassion"]
        for topic in topics {
            let encoded = topic.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? topic
            categories.append(BrowseCategory(id: topic, name: topic, type: .theme,
                                              browseUrl: "\(Self.baseUrl)/browse?th=\(encoded)"))
        }

        let currentYear = Calendar.current.component(.year, from: Date())
        for year in stride(from: currentYear, through: 2010, by: -1) {
            categories.append(BrowseCategory(id: "year_\(year)", name: "\(year)", type: .year,
                                              browseUrl: "\(Self.baseUrl)/browse?y=\(year)&t=audio"))
        }

        return categories
    }

    func fetchFromBrowseUrl(_ browseUrl: String) async throws -> BrowsePage {
        let resolved = resolveUrl(browseUrl)
        let html = try await fetchHtml(resolved)
        if resolved.contains("/series/details") {
            return parseSeriesDetailPage(html)
        } else {
            let queryString = resolved.contains("?") ? String(resolved.split(separator: "?").last ?? "") : ""
            return parseBrowseCollectionPage(html, queryString: queryString)
        }
    }

    private func parseBrowseCollectionPage(_ html: String, queryString: String) -> BrowsePage {
        guard let collectionJson = extractFbaJson(html, key: "collection"),
              let items = collectionJson["items"] as? [[String: Any]] else {
            return BrowsePage(browseQueryString: queryString)
        }

        var results: [SearchResult] = []
        for obj in items {
            let path = str(obj, "url") ?? ""
            guard path.contains("/audio/") else { continue }
            let catNum = str(obj, "cat_num") ?? str(obj, "catNum") ?? ""
            guard !catNum.isEmpty else { continue }
            results.append(SearchResult(
                catNum: catNum,
                title: unescape(str(obj, "title") ?? ""),
                speaker: unescape(str(obj, "speaker") ?? ""),
                imageUrl: resolveUrl(str(obj, "image_url") ?? str(obj, "image") ?? ""),
                path: resolveUrl(path),
                year: int(obj, "year") ?? 0
            ))
        }

        let totalItems = int(collectionJson, "total_items") ?? results.count
        let apiPath = str(collectionJson, "url") ?? ""
        let apiBaseUrl = apiPath.isEmpty ? "" : resolveUrl(apiPath)

        return BrowsePage(items: results, totalItems: totalItems, apiBaseUrl: apiBaseUrl,
                          browseQueryString: queryString)
    }

    private func parseSeriesDetailPage(_ html: String) -> BrowsePage {
        guard let seriesJson = extractFbaJson(html, key: "series") else {
            return BrowsePage()
        }
        let seriesTitle = unescape(str(seriesJson, "title") ?? "")
        guard let members = seriesJson["members"] as? [[String: Any]] else {
            return BrowsePage(title: seriesTitle)
        }

        var results: [SearchResult] = []
        for obj in members {
            let catNum = str(obj, "cat_num") ?? str(obj, "catNum") ?? str(obj, "member_cat_num") ?? ""
            guard !catNum.isEmpty else { continue }
            let path = str(obj, "link") ?? str(obj, "url") ?? str(obj, "href") ?? "/audio/details?num=\(catNum)"
            results.append(SearchResult(
                catNum: catNum,
                title: unescape(str(obj, "title") ?? ""),
                speaker: unescape(str(obj, "speaker") ?? str(obj, "author") ?? ""),
                imageUrl: resolveUrl(str(obj, "image_url") ?? str(obj, "image") ?? ""),
                path: resolveUrl(path),
                year: int(obj, "year") ?? 0
            ))
        }

        return BrowsePage(items: results, totalItems: results.count, title: seriesTitle)
    }

    // MARK: - Search

    func searchAudio(_ query: String) async throws -> [SearchResult] {
        var components = URLComponents(string: "\(Self.baseUrl)/api/v1/search")!
        components.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "type", value: "audio"),
        ]
        let html = try await fetchHtml(components.url!.absoluteString)
        guard let data = html.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let searchObj = json["search"] as? [String: Any],
              let items = searchObj["results"] as? [[String: Any]] else {
            return []
        }

        var results: [SearchResult] = []
        var seen = Set<String>()
        for obj in items {
            guard results.count < 200 else { break }
            let catNum = str(obj, "cat_num") ?? str(obj, "catNum") ?? ""
            guard !catNum.isEmpty, seen.insert(catNum).inserted else { continue }
            let link = str(obj, "link") ?? "/audio/details?num=\(catNum)"
            results.append(SearchResult(
                catNum: catNum,
                title: unescape(str(obj, "title") ?? ""),
                speaker: unescape(str(obj, "speaker") ?? ""),
                imageUrl: resolveUrl(str(obj, "image_url") ?? str(obj, "image") ?? ""),
                path: resolveUrl(link),
                year: int(obj, "year") ?? 0
            ))
        }
        return results
    }

    // MARK: - Browse by Speaker

    func browseBySpeaker(_ speakerName: String) async throws -> BrowsePage {
        var components = URLComponents(string: "\(Self.baseUrl)/browse")!
        components.queryItems = [
            URLQueryItem(name: "s", value: speakerName),
            URLQueryItem(name: "t", value: "audio"),
        ]
        let html = try await fetchHtml(components.url!.absoluteString)
        return parseBrowseCollectionPage(html, queryString: "s=\(speakerName)&t=audio")
    }

    // MARK: - Pagination

    func fetchMoreItems(apiBaseUrl: String, browseQueryString: String, startIndex: Int, count: Int) async -> [SearchResult] {
        await withTaskGroup(of: SearchResult?.self) { group in
            for idx in startIndex..<(startIndex + count) {
                group.addTask {
                    do {
                        let url = "\(apiBaseUrl)?\(browseQueryString)&page=\(idx)"
                        guard let urlObj = URL(string: url) else { return nil }
                        let (data, _) = try await self.session.data(from: urlObj)
                        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                              let coll = json["collection"] as? [String: Any],
                              let items = coll["items"] as? [[String: Any]],
                              let obj = items.first else { return nil }
                        let path = obj["url"] as? String ?? ""
                        guard path.contains("/audio/") else { return nil }
                        let catNum = obj["cat_num"] as? String ?? obj["catNum"] as? String ?? ""
                        guard !catNum.isEmpty else { return nil }
                        return SearchResult(
                            catNum: catNum,
                            title: (obj["title"] as? String) ?? "",
                            speaker: (obj["speaker"] as? String) ?? "",
                            imageUrl: self.resolveUrl(obj["image_url"] as? String ?? obj["image"] as? String ?? ""),
                            path: self.resolveUrl(path),
                            year: Int(obj["year"] as? String ?? "") ?? 0
                        )
                    } catch {
                        return nil
                    }
                }
            }
            var results: [SearchResult] = []
            for await result in group {
                if let r = result { results.append(r) }
            }
            return results
        }
    }

    // MARK: - Transcript

    func fetchTranscript(_ transcriptUrl: String) async throws -> String {
        let url = resolveUrl(transcriptUrl)
        let html = try await fetchHtml(url)
        return TranscriptParser.parseTranscriptHtml(html)
    }

    // MARK: - Errors

    enum ScraperError: LocalizedError {
        case invalidUrl(String)
        case httpError(Int)
        case emptyResponse
        case parseError(String)

        var errorDescription: String? {
            switch self {
            case .invalidUrl(let url): return "Invalid URL: \(url)"
            case .httpError(let code): return "HTTP error \(code)"
            case .emptyResponse: return "Empty response"
            case .parseError(let msg): return msg
            }
        }
    }
}
