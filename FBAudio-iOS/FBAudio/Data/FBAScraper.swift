import Foundation
import SwiftSoup

actor FBAScraper {
    private static let baseUrl = "https://www.freebuddhistaudio.com"

    init() {}

    // MARK: - HTML Fetching

    /// All website requests go through the login session (serialized while logged in).
    private func fetch(_ request: URLRequest) async throws -> (Data, URLResponse) {
        try await FbaSession.shared.data(for: request)
    }

    private func fetchHtml(_ urlString: String) async throws -> String {
        guard let url = URL(string: urlString) else { throw ScraperError.invalidUrl(urlString) }
        let (data, response) = try await fetch(URLRequest(url: url))
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
        // Negative or absurd durations (a known data problem on the site) → 0, so the
        // UI derives the length from the tracks instead.
        let rawDuration = int(json, "durationSeconds") ?? int(json, "duration") ?? 0
        let duration = PlaybackMath.isPlausibleDuration(rawDuration) ? rawDuration : 0
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

        // Saved position from the FBA account — only present on logged-in page loads.
        var checkpoint: Checkpoint?
        if let cp = json["checkpoint"] as? [String: Any], let trackId = str(cp, "track_id"),
           let seconds = int(cp, "time_seconds") {
            checkpoint = Checkpoint(trackId: trackId, timeSeconds: seconds)
        }

        return Talk(
            catNum: catNum, title: title, speaker: speaker, year: year,
            genre: genre, durationSeconds: duration, imageUrl: resolveUrl(imageUrl),
            audioUrl: resolveUrl(audioUrl), description: description, tracks: tracks,
            transcriptUrl: transcriptUrl, series: seriesTitle, seriesHref: seriesHref,
            omOnly: (int(json, "om") ?? 0) != 0, checkpoint: checkpoint
        )
    }

    private func parseTracksArray(_ json: [String: Any]) -> [Track] {
        guard let tracksArr = json["tracks"] as? [[String: Any]] else { return [] }
        return tracksArr.compactMap { t in
            guard let audio = t["audio"] as? [String: Any],
                  let mp3 = audio["mp3"] as? String else { return nil }
            let remaster = (t["remasterAudio"] as? [String: Any])?["mp3"] as? String ?? ""
            return Track(
                title: unescape(str(t, "title") ?? ""),
                durationSeconds: { let d = int(t, "durationSeconds") ?? 0; return PlaybackMath.isPlausibleDuration(d) ? d : 0 }(),
                audioUrl: resolveUrl(mp3),
                trackId: str(t, "trackId") ?? "",
                remasterAudioUrl: resolveUrl(remaster),
                remasterDurationSeconds: max(int(t, "remasterDurationSeconds") ?? 0, 0)
            )
        }
    }

    // MARK: - Browse

    nonisolated func fetchBrowseCategories() -> [BrowseCategory] {
        var categories: [BrowseCategory] = []

        categories.append(BrowseCategory(id: "Sangharakshita", name: "Sangharakshita",
                                          type: .sangharakshita, browseUrl: "sang://root"))

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
        var seen = Set<String>()
        for obj in items {
            let path = str(obj, "url") ?? ""
            guard path.contains("/audio/") else { continue }
            let catNum = str(obj, "cat_num") ?? str(obj, "catNum") ?? ""
            // Dedup: SearchResult.id == catNum; duplicates break SwiftUI ForEach identity
            guard !catNum.isEmpty, seen.insert(catNum).inserted else { continue }
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
        var seen = Set<String>()
        for obj in members {
            let catNum = str(obj, "cat_num") ?? str(obj, "catNum") ?? str(obj, "member_cat_num") ?? ""
            guard !catNum.isEmpty, seen.insert(catNum).inserted else { continue }
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
        try await searchByType(query, type: "audio")
    }

    func searchSeries(_ query: String) async throws -> [SearchResult] {
        try await searchByType(query, type: "series")
    }

    private func searchByType(_ query: String, type: String) async throws -> [SearchResult] {
        var components = URLComponents(string: "\(Self.baseUrl)/api/v1/search")!
        components.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "type", value: type),
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
        // Percent-encode the pagination query string — fetchMoreItems interpolates
        // it into URL(string:), which returns nil for raw spaces (multi-word
        // speakers would silently get no Load More results).
        let encodedName = speakerName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? speakerName
        return parseBrowseCollectionPage(html, queryString: "s=\(encodedName)&t=audio")
    }

    // MARK: - Pagination

    func fetchMoreItems(apiBaseUrl: String, browseQueryString: String, startIndex: Int, count: Int) async -> [SearchResult] {
        await withTaskGroup(of: SearchResult?.self) { group in
            for idx in startIndex..<(startIndex + count) {
                group.addTask {
                    do {
                        let url = "\(apiBaseUrl)?\(browseQueryString)&page=\(idx)"
                        guard let urlObj = URL(string: url) else { return nil }
                        let (data, _) = try await FbaSession.shared.data(from: urlObj)
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

    // MARK: - Website content: menu, collections, series, Digital Legacy

    private func fetchJson(_ urlString: String) async throws -> [String: Any] {
        guard let url = URL(string: urlString) else { throw ScraperError.invalidUrl(urlString) }
        var request = URLRequest(url: url)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await fetch(request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw ScraperError.httpError((response as? HTTPURLResponse)?.statusCode ?? 0)
        }
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ScraperError.parseError("Not JSON: \(urlString)")
        }
        return obj
    }

    private func bool(_ dict: [String: Any], _ key: String) -> Bool {
        if let b = dict[key] as? Bool { return b }
        if let n = dict[key] as? Int { return n != 0 }
        if let s = dict[key] as? String { return s == "1" || s == "true" }
        return false
    }

    /// Talk, series or speaker/place/year item of any listing.
    private func makeResult(_ obj: [String: Any], catNum: String, path: String) -> SearchResult {
        SearchResult(
            catNum: catNum,
            title: unescape(str(obj, "title") ?? ""),
            speaker: unescape(str(obj, "speaker") ?? str(obj, "author") ?? ""),
            imageUrl: resolveUrl(str(obj, "image_url") ?? str(obj, "image") ?? ""),
            path: resolveUrl(path),
            year: int(obj, "year") ?? 0,
            centre: unescape(str(obj, "centre") ?? ""),
            omOnly: bool(obj, "om_only") || (str(obj, "om") == "1")
        )
    }

    /// Items of any collection/browse listing: talks, series, and speaker/place/year links.
    private func parseListItems(_ items: [[String: Any]]?) -> [SearchResult] {
        guard let items else { return [] }
        var out: [SearchResult] = []
        var seen = Set<String>()
        for obj in items {
            guard let path = str(obj, "url") ?? str(obj, "link") else { continue }
            var catNum = str(obj, "cat_num") ?? str(obj, "catNum") ?? ""
            if catNum.isEmpty {
                catNum = ContentSource.queryValue("num", in: path) ?? path
            }
            guard !catNum.isEmpty, seen.insert("\(path)|\(catNum)").inserted else { continue }
            var result = makeResult(obj, catNum: catNum, path: path)
            if path.contains("/browse") {
                // Speaker/place/year tiles carry a count in the title: "Abayanandi (1)"
                let cleaned = result.title.replacing(/\s*\(\d+\)$/, with: "")
                result = SearchResult(catNum: result.catNum, title: cleaned, speaker: result.speaker,
                                      imageUrl: result.imageUrl, path: result.path, year: result.year,
                                      centre: result.centre, omOnly: result.omOnly)
            }
            out.append(result)
        }
        return out
    }

    /// HTML blurb → readable plain text with paragraph breaks.
    func htmlToText(_ html: String) -> String {
        guard html.contains("<") else { return unescape(html).trimmingCharacters(in: .whitespacesAndNewlines) }
        guard let doc = try? SwiftSoup.parse(html) else { return html }
        try? doc.select("p").prepend("\n\n")
        try? doc.select("br").append("\n")
        let text = (try? doc.text()) ?? html
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// The website's curated side menu (collections, sangharakshita, themes, people, places, languages…).
    func fetchSiteMenu() async throws -> [MenuNode] {
        let html = try await fetchHtml("\(Self.baseUrl)/")
        guard let json = extractFbaJson(html, key: "sidebar_menu") else { return [] }
        return SiteMenuParser.parse(json: json)
    }

    /// `document.__FBA__.user` from the homepage — present only when the session is logged in.
    func fetchLoggedInUser() async throws -> [String: Any]? {
        let html = try await fetchHtml("\(Self.baseUrl)/")
        return extractFbaJson(html, key: "user")
    }

    /// One page of an API collection (latest, introductions, speakers, all_series…).
    /// `limit` is the only page-size parameter the server honours.
    func fetchApiCollectionPage(type: String, page: Int, title: String = "") async throws -> ListPage {
        let encoded = type.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? type
        let json = try await fetchJson("\(Self.baseUrl)/api/v1/collections/\(encoded)?page=\(page)&limit=\(ListPage.pageSize)")
        guard let coll = json["collection"] as? [String: Any] else { return ListPage(items: [], totalItems: 0, page: page, title: title) }
        return ListPage(
            items: parseListItems(coll["items"] as? [[String: Any]]),
            totalItems: int(coll, "total_items") ?? 0,
            page: page,
            title: title.isEmpty ? unescape(str(coll, "label") ?? type) : title
        )
    }

    /// Images for a whole index collection (speakers, places): browse path →
    /// image URL, skipping the site's placeholder images. One request (limit=1000).
    func fetchIndexImages(type: String) async throws -> [String: String] {
        let encoded = type.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? type
        let json = try await fetchJson("\(Self.baseUrl)/api/v1/collections/\(encoded)?page=1&limit=1000")
        var out: [String: String] = [:]
        for obj in (json["collection"] as? [String: Any])?["items"] as? [[String: Any]] ?? [] {
            guard let path = str(obj, "url"), let image = str(obj, "image_url"),
                  !image.isEmpty, !image.contains("/default") else { continue }
            out[Self.normalizeBrowsePath(path)] = resolveUrl(image)
        }
        return out
    }

    /// "https://www.freebuddhistaudio.com/browse?p=Adhisthana " → "/browse?p=adhisthana"
    nonisolated static func normalizeBrowsePath(_ link: String) -> String {
        var l = link.trimmingCharacters(in: .whitespaces)
        if l.hasPrefix(baseUrl) { l = String(l.dropFirst(baseUrl.count)) }
        return l.lowercased()
    }

    /// One page of a `/browse?…` listing (a speaker, place, year or genre).
    func fetchBrowsePage(path: String, page: Int, apiUrl: String = "", apiQuery: String = "") async throws -> ListPage {
        if page > 1, !apiUrl.isEmpty {
            let json = try await fetchJson("\(apiUrl)?\(apiQuery)&page=\(page)&limit=\(ListPage.pageSize)")
            guard let coll = json["collection"] as? [String: Any] else { return ListPage(items: [], totalItems: 0, page: page) }
            return ListPage(items: parseListItems(coll["items"] as? [[String: Any]]),
                            totalItems: int(coll, "total_items") ?? 0, page: page,
                            apiUrl: apiUrl, apiQuery: apiQuery)
        }
        let resolved = resolveUrl(path)
        let html = try await fetchHtml(resolved)
        guard let coll = extractFbaJson(html, key: "collection") else { return ListPage(items: [], totalItems: 0, page: 1) }
        let query = resolved.split(separator: "?", maxSplits: 1).dropFirst().first.map(String.init) ?? ""
        return ListPage(
            items: parseListItems(coll["items"] as? [[String: Any]]),
            totalItems: int(coll, "total_items") ?? 0,
            page: 1,
            title: browseTitle(query: query, label: unescape(str(coll, "label") ?? "")),
            apiUrl: (str(coll, "url")).map(resolveUrl) ?? "",
            apiQuery: query
        )
    }

    /// "s=Subhuti&t=audio" → "Subhuti"; falls back to the collection label.
    private func browseTitle(query: String, label: String) -> String {
        for key in ["s", "p", "th", "ser", "y"] {
            if let v = ContentSource.queryValue(key, in: "?" + query) {
                let decoded = v.replacingOccurrences(of: "+", with: "%2B").removingPercentEncoding ?? v
                return decoded.replacingOccurrences(of: "_", with: " ")
            }
        }
        return label
    }

    /// One page of a curated `/collection/<slug>` page. Pages with `pageNo`.
    func fetchNamedCollectionPage(slug: String, page: Int) async throws -> ListPage {
        let encoded = slug.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? slug
        let html = try await fetchHtml("\(Self.baseUrl)/collection/\(encoded)?pageNo=\(page)")
        guard let data = extractFbaJson(html, key: "collectionData") else { return ListPage(items: [], totalItems: 0, page: page) }
        return ListPage(
            items: parseListItems(data["items"] as? [[String: Any]]),
            totalItems: int(data, "totalItems") ?? 0,
            page: int(data, "pageNo") ?? page,
            title: unescape(str(data, "title") ?? slug),
            description: (str(data, "description")).map(htmlToText) ?? "",
            imageUrl: resolveUrl(str(data, "marquee_image") ?? str(data, "image") ?? "")
        )
    }

    /// A series page: title, blurb, image, remaster flag and all member talks.
    func fetchSeriesPage(path: String) async throws -> ListPage {
        let html = try await fetchHtml(resolveUrl(path))
        guard let series = extractFbaJson(html, key: "series") else { return ListPage(items: [], totalItems: 0, page: 1) }
        var items: [SearchResult] = []
        var seen = Set<String>()
        for obj in series["members"] as? [[String: Any]] ?? [] {
            let catNum = str(obj, "cat_num") ?? str(obj, "member_cat_num") ?? ""
            guard !catNum.isEmpty, seen.insert(catNum).inserted else { continue }
            let link = str(obj, "link") ?? str(obj, "url") ?? "/audio/details?num=\(catNum)"
            items.append(makeResult(obj, catNum: catNum, path: link))
        }
        return ListPage(
            items: items,
            totalItems: items.count,
            page: 1,
            title: unescape(str(series, "title") ?? ""),
            description: (str(series, "blurb")).map(htmlToText) ?? "",
            imageUrl: resolveUrl(str(series, "marquee_image") ?? str(series, "image") ?? str(series, "speaker_image") ?? ""),
            hasRemaster: bool(series, "hasRemasteredTalk"),
            omOnly: (int(series, "om") ?? 0) != 0
        )
    }

    /// Copy and sample talk of the Digital Legacy page.
    func fetchDigitalLegacy() async throws -> DigitalLegacy? {
        let html = try await fetchHtml("\(Self.baseUrl)/digital-legacy")
        guard let page = extractFbaJson(html, key: "digitalLegacyPage") else { return nil }
        let descriptionHtml = str(page, "descriptionHtml") ?? ""
        let sample = page["sampleTalk"] as? [String: Any]
        let seriesPath = descriptionHtml.firstMatch(of: /\/series\/details\?num=[A-Za-z0-9]+/).map { String($0.output) } ?? "/series/details?num=X16"
        return DigitalLegacy(
            title: unescape(str(page, "title") ?? "The Digital Legacy"),
            description: htmlToText(descriptionHtml),
            sampleCatNum: (sample.flatMap { str($0, "catNum") ?? str($0, "cat_num") }) ?? "",
            seriesPath: seriesPath
        )
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
