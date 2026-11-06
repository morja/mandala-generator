import Foundation

/// The settings JSON as the files hold it: the typed fields, this app's `formatVersion`, and the
/// unknown fields of the root object and the six fixed layer objects, kept in
/// `MandalaParameters.extras` so they come back out unchanged (ADR-0003). A typed field always wins
/// over a kept one with the same name; `formatVersion` is not kept, the app writes its own. Unknown
/// fields inside the `layers` list are not kept. The Android app does the same in `ParamsJson`.
enum ParamsJSON {
    static let formatVersion = 1
    private static let versionKey = "formatVersion"
    private static let rootKeys = keys(MandalaParameters.CodingKeys.self)
    private static let layerKeys: [String: Set<String>] = [
        "baseLayer": keys(BaseLayerSettings.CodingKeys.self),
        "effectsLayer": keys(EffectsLayerSettings.CodingKeys.self),
        "graffitiLayer": keys(GraffitiLayerSettings.CodingKeys.self),
        "drawingLayer": keys(DrawingLayerSettings.CodingKeys.self),
        "textLayer": keys(TextLayerSettings.CodingKeys.self),
        "emblemLayer": keys(EmblemLayerSettings.CodingKeys.self),
    ]

    private static func keys<K: CodingKey & CaseIterable>(_ type: K.Type) -> Set<String> {
        Set(type.allCases.map(\.stringValue))
    }

    // MARK: Reading

    /// One settings object (a saved settings file).
    static func decode(_ data: Data) throws -> MandalaParameters {
        var params = try JSONDecoder().decode(MandalaParameters.self, from: data)
        if let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
            params.extras = extras(of: object)
        }
        return params
    }

    /// A list of settings objects (history.json).
    static func decodeList(_ data: Data) throws -> [MandalaParameters] {
        var list = try JSONDecoder().decode([MandalaParameters].self, from: data)
        let objects = (try? JSONSerialization.jsonObject(with: data)) as? [Any] ?? []
        for i in list.indices where i < objects.count {
            if let object = objects[i] as? [String: Any] { list[i].extras = extras(of: object) }
        }
        return list
    }

    /// The favourites file; each favourite's parameters keep their unknown fields.
    static func decodeFavorites(_ data: Data) throws -> [Favorite] {
        var list = try JSONDecoder().decode([Favorite].self, from: data)
        let objects = (try? JSONSerialization.jsonObject(with: data)) as? [Any] ?? []
        for i in list.indices where i < objects.count {
            if let object = (objects[i] as? [String: Any])?["parameters"] as? [String: Any] {
                list[i].parameters.extras = extras(of: object)
            }
        }
        return list
    }

    // MARK: Writing

    static func encode(_ params: MandalaParameters) throws -> Data {
        let data = try JSONEncoder().encode(params)
        guard params.extras != nil, let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { return data }
        return try JSONSerialization.data(withJSONObject: merged(object, with: params.extras))
    }

    static func encode(_ list: [MandalaParameters]) throws -> Data {
        let data = try JSONEncoder().encode(list)
        guard list.contains(where: { $0.extras != nil }), var objects = try JSONSerialization.jsonObject(with: data) as? [Any] else { return data }
        for i in list.indices where i < objects.count {
            if let object = objects[i] as? [String: Any] { objects[i] = merged(object, with: list[i].extras) }
        }
        return try JSONSerialization.data(withJSONObject: objects)
    }

    static func encode(_ favorites: [Favorite]) throws -> Data {
        let data = try JSONEncoder().encode(favorites)
        guard favorites.contains(where: { $0.parameters.extras != nil }), var objects = try JSONSerialization.jsonObject(with: data) as? [Any] else { return data }
        for i in favorites.indices where i < objects.count {
            if var favorite = objects[i] as? [String: Any], let object = favorite["parameters"] as? [String: Any] {
                favorite["parameters"] = merged(object, with: favorites[i].parameters.extras)
                objects[i] = favorite
            }
        }
        return try JSONSerialization.data(withJSONObject: objects)
    }

    // MARK: Unknown fields

    /// The unknown keys of `object` and of its six fixed layer objects, as JSON; nil when there are none.
    static func extras(of object: [String: Any]) -> Data? {
        var unknown: [String: Any] = [:]
        for (key, value) in object where !rootKeys.contains(key) && key != versionKey {
            unknown[key] = value
        }
        for (layer, known) in layerKeys {
            guard let inner = object[layer] as? [String: Any] else { continue }
            let add = inner.filter { !known.contains($0.key) }
            if !add.isEmpty { unknown[layer] = add }
        }
        guard !unknown.isEmpty else { return nil }
        return try? JSONSerialization.data(withJSONObject: unknown, options: [.sortedKeys])
    }

    /// `typed` with the kept fields filled in around it; a typed field always wins.
    private static func merged(_ typed: [String: Any], with extras: Data?) -> [String: Any] {
        guard let extras, let unknown = (try? JSONSerialization.jsonObject(with: extras)) as? [String: Any] else { return typed }
        var out = typed
        for (key, value) in unknown {
            if layerKeys[key] != nil, var layer = out[key] as? [String: Any], let add = value as? [String: Any] {
                for (k, v) in add where layer[k] == nil { layer[k] = v }
                out[key] = layer
            } else if out[key] == nil {
                out[key] = value
            }
        }
        return out
    }
}
