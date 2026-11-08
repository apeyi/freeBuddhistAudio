import Foundation

struct Talk: Identifiable, Codable, Equatable {
    let catNum: String
    let title: String
    let speaker: String
    let year: Int
    let genre: String
    let durationSeconds: Int
    let imageUrl: String
    let audioUrl: String
    let description: String
    var tracks: [Track]
    let transcriptUrl: String
    let series: String
    let seriesHref: String
    /// Restricted to members of the Triratna Buddhist Order.
    let omOnly: Bool
    /// The listener's saved position on the FBA website (only present when logged in).
    let checkpoint: Checkpoint?
    /// Wide banner ("marquee") image, first choice for talk artwork.
    var bannerUrl: String
    /// The speaker's image, second choice for talk artwork.
    var speakerImageUrl: String
    /// Where the talk was given (link to all talks from that place).
    var placeName: String
    /// The place's browse slug (`/browse?p=…`).
    var placeSlug: String
    /// 1 once the v2 fields above have been merged in; talks cached before that are refetched once.
    var extrasVersion: Int

    var id: String { catNum }

    var hasRemaster: Bool { tracks.contains { $0.hasRemaster } }

    init(catNum: String, title: String = "", speaker: String = "", year: Int = 0,
         genre: String = "", durationSeconds: Int = 0, imageUrl: String = "",
         audioUrl: String = "", description: String = "", tracks: [Track] = [],
         transcriptUrl: String = "", series: String = "", seriesHref: String = "",
         omOnly: Bool = false, checkpoint: Checkpoint? = nil,
         bannerUrl: String = "", speakerImageUrl: String = "",
         placeName: String = "", placeSlug: String = "", extrasVersion: Int = 0) {
        self.catNum = catNum
        self.title = title
        self.speaker = speaker
        self.year = year
        self.genre = genre
        self.durationSeconds = durationSeconds
        self.imageUrl = imageUrl
        self.audioUrl = audioUrl
        self.description = description
        self.tracks = tracks
        self.transcriptUrl = transcriptUrl
        self.series = series
        self.seriesHref = seriesHref
        self.omOnly = omOnly
        self.checkpoint = checkpoint
        self.bannerUrl = bannerUrl
        self.speakerImageUrl = speakerImageUrl
        self.placeName = placeName
        self.placeSlug = placeSlug
        self.extrasVersion = extrasVersion
    }

    // Tolerant decoding: talks cached before these fields existed must still load.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        catNum = try c.decode(String.self, forKey: .catNum)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        speaker = try c.decodeIfPresent(String.self, forKey: .speaker) ?? ""
        year = try c.decodeIfPresent(Int.self, forKey: .year) ?? 0
        genre = try c.decodeIfPresent(String.self, forKey: .genre) ?? ""
        durationSeconds = try c.decodeIfPresent(Int.self, forKey: .durationSeconds) ?? 0
        imageUrl = try c.decodeIfPresent(String.self, forKey: .imageUrl) ?? ""
        audioUrl = try c.decodeIfPresent(String.self, forKey: .audioUrl) ?? ""
        description = try c.decodeIfPresent(String.self, forKey: .description) ?? ""
        tracks = try c.decodeIfPresent([Track].self, forKey: .tracks) ?? []
        transcriptUrl = try c.decodeIfPresent(String.self, forKey: .transcriptUrl) ?? ""
        series = try c.decodeIfPresent(String.self, forKey: .series) ?? ""
        seriesHref = try c.decodeIfPresent(String.self, forKey: .seriesHref) ?? ""
        omOnly = try c.decodeIfPresent(Bool.self, forKey: .omOnly) ?? false
        checkpoint = try c.decodeIfPresent(Checkpoint.self, forKey: .checkpoint)
        bannerUrl = try c.decodeIfPresent(String.self, forKey: .bannerUrl) ?? ""
        speakerImageUrl = try c.decodeIfPresent(String.self, forKey: .speakerImageUrl) ?? ""
        placeName = try c.decodeIfPresent(String.self, forKey: .placeName) ?? ""
        placeSlug = try c.decodeIfPresent(String.self, forKey: .placeSlug) ?? ""
        extrasVersion = try c.decodeIfPresent(Int.self, forKey: .extrasVersion) ?? 0
    }
}

struct Track: Codable, Equatable {
    let title: String
    let durationSeconds: Int
    let audioUrl: String
    /// Website track id — needed for resume-position sync with the FBA account.
    let trackId: String
    /// Digitally remastered version of this track, or "" when none exists.
    let remasterAudioUrl: String
    let remasterDurationSeconds: Int

    var hasRemaster: Bool { !remasterAudioUrl.isEmpty }

    init(title: String, durationSeconds: Int, audioUrl: String,
         trackId: String = "", remasterAudioUrl: String = "", remasterDurationSeconds: Int = 0) {
        self.title = title
        self.durationSeconds = durationSeconds
        self.audioUrl = audioUrl
        self.trackId = trackId
        self.remasterAudioUrl = remasterAudioUrl
        self.remasterDurationSeconds = remasterDurationSeconds
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        durationSeconds = try c.decodeIfPresent(Int.self, forKey: .durationSeconds) ?? 0
        audioUrl = try c.decodeIfPresent(String.self, forKey: .audioUrl) ?? ""
        trackId = try c.decodeIfPresent(String.self, forKey: .trackId) ?? ""
        remasterAudioUrl = try c.decodeIfPresent(String.self, forKey: .remasterAudioUrl) ?? ""
        remasterDurationSeconds = try c.decodeIfPresent(Int.self, forKey: .remasterDurationSeconds) ?? 0
    }
}

struct Checkpoint: Codable, Equatable {
    let trackId: String
    let timeSeconds: Int
}
