// tinyjson.h — minimal strict JSON parser for the Supertonic model/config files.
//
// Why hand-rolled: the bridge conventions forbid new third-party dependencies
// ("standard library and vendored APIs only"), and the engine only needs to
// read two machine-written contract files: tts.json (a handful of nested ints)
// and voice_styles/*.json (two dims vectors + two nested float arrays). A full
// JSON library is dead weight for that; this parser covers the complete JSON
// grammar anyway (objects, arrays, strings with \uXXXX escapes, numbers,
// booleans, null) so contract extensions keep working.
//
// Failure mode: parse throws tinyjson::Error with a byte offset; callers turn
// that into a load-time fail-fast ("do not throw across the JNI boundary" is
// honored in supertonic.cc, which catches everything).

#ifndef APP_VERSTA_TINYJSON_H
#define APP_VERSTA_TINYJSON_H

#include <cctype>
#include <cstdint>
#include <map>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

namespace tinyjson {

class Error : public std::runtime_error {
public:
    explicit Error(const std::string& msg) : std::runtime_error(msg) {}
};

struct Value {
    enum class Type { Null, Bool, Number, String, Array, Object };

    Type type = Type::Null;
    bool boolean = false;
    double number = 0.0;
    std::string str;
    std::vector<Value> array;
    // std::map keeps keys ordered; lookup only, insertion order is irrelevant
    // for our contract files.
    std::map<std::string, Value> object;

    static Value makeNull() { return Value(); }
    static Value makeBool(bool b) { Value v; v.type = Type::Bool; v.boolean = b; return v; }
    static Value makeNumber(double n) { Value v; v.type = Type::Number; v.number = n; return v; }
    static Value makeString(std::string s) { Value v; v.type = Type::String; v.str = std::move(s); return v; }
    static Value makeArray(std::vector<Value> a) { Value v; v.type = Type::Array; v.array = std::move(a); return v; }
    static Value makeObject(std::map<std::string, Value> o) { Value v; v.type = Type::Object; v.object = std::move(o); return v; }

    bool isNull() const { return type == Type::Null; }

    // Typed accessors. All throw Error on type mismatch so a malformed bundle
    // fails at load, never silently mid-synthesis.
    const Value& at(const std::string& key) const;
    const Value* find(const std::string& key) const;
    long asLong() const;
    double asDouble() const;
    const std::string& asString() const;
    const std::vector<Value>& asArray() const;

    // Flattens nested arrays of numbers (e.g. dims [1,50,256] stored as nested
    // JSON lists) into row-major flat data and validates sizes along the way.
    std::vector<float> flattenFloats() const;
};

// Parses exactly one JSON document; trailing non-whitespace is an error.
Value parse(const std::string& text);

namespace detail {

class Cursor {
public:
    explicit Cursor(const std::string& text) : text_(text) {}

    Value parseDocument() {
        skipWs();
        Value v = parseValue();
        skipWs();
        if (pos_ != text_.size()) {
            fail("trailing characters after JSON document");
        }
        return v;
    }

private:
    const std::string& text_;
    size_t pos_ = 0;

    [[noreturn]] void fail(const std::string& what) const {
        throw Error("tinyjson: " + what + " at byte " + std::to_string(pos_));
    }

    char peek() const { return pos_ < text_.size() ? text_[pos_] : '\0'; }
    char take() { return pos_ < text_.size() ? text_[pos_++] : '\0'; }

    void skipWs() {
        while (pos_ < text_.size()) {
            char c = text_[pos_];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                ++pos_;
            } else {
                break;
            }
        }
    }

    void expect(char c) {
        if (take() != c) {
            fail(std::string("expected '") + c + "'");
        }
    }

    bool consume(char c) {
        if (peek() == c) {
            ++pos_;
            return true;
        }
        return false;
    }

    Value parseValue() {
        skipWs();
        switch (peek()) {
            case '{': return parseObject();
            case '[': return parseArray();
            case '"': return Value::makeString(parseString());
            case 't': parseLiteral("true"); return Value::makeBool(true);
            case 'f': parseLiteral("false"); return Value::makeBool(false);
            case 'n': parseLiteral("null"); return Value::makeNull();
            default: return parseNumber();
        }
    }

    void parseLiteral(const char* word) {
        for (const char* p = word; *p; ++p) {
            if (take() != *p) {
                fail(std::string("bad literal, expected '") + word + "'");
            }
        }
    }

    Value parseObject() {
        expect('{');
        std::map<std::string, Value> obj;
        skipWs();
        if (consume('}')) {
            return Value::makeObject(std::move(obj));
        }
        while (true) {
            skipWs();
            if (peek() != '"') {
                fail("object keys must be strings");
            }
            std::string key = parseString();
            skipWs();
            expect(':');
            obj[std::move(key)] = parseValue();
            skipWs();
            if (consume('}')) {
                break;
            }
            expect(',');
        }
        return Value::makeObject(std::move(obj));
    }

    Value parseArray() {
        expect('[');
        std::vector<Value> arr;
        skipWs();
        if (consume(']')) {
            return Value::makeArray(std::move(arr));
        }
        while (true) {
            arr.push_back(parseValue());
            skipWs();
            if (consume(']')) {
                break;
            }
            expect(',');
        }
        return Value::makeArray(std::move(arr));
    }

    std::string parseString() {
        expect('"');
        std::string out;
        while (true) {
            char c = take();
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                char e = take();
                switch (e) {
                    case '"': out += '"'; break;
                    case '\\': out += '\\'; break;
                    case '/': out += '/'; break;
                    case 'b': out += '\b'; break;
                    case 'f': out += '\f'; break;
                    case 'n': out += '\n'; break;
                    case 'r': out += '\r'; break;
                    case 't': out += '\t'; break;
                    case 'u': {
                        uint32_t cp = parseHex4();
                        // Surrogate pair handling for \uD800-\uDFFF.
                        if (cp >= 0xD800 && cp <= 0xDBFF) {
                            if (take() != '\\' || take() != 'u') {
                                fail("unpaired high surrogate in string");
                            }
                            uint32_t lo = parseHex4();
                            if (lo < 0xDC00 || lo > 0xDFFF) {
                                fail("unpaired high surrogate in string");
                            }
                            cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                        } else if (cp >= 0xDC00 && cp <= 0xDFFF) {
                            fail("unpaired low surrogate in string");
                        }
                        appendUtf8(out, cp);
                        break;
                    }
                    default:
                        fail("bad escape in string");
                }
            } else if (static_cast<unsigned char>(c) < 0x20) {
                fail("unescaped control character in string");
            } else {
                out += c;
            }
        }
        return out;
    }

    uint32_t parseHex4() {
        uint32_t v = 0;
        for (int i = 0; i < 4; ++i) {
            char c = take();
            v <<= 4;
            if (c >= '0' && c <= '9') {
                v |= static_cast<uint32_t>(c - '0');
            } else if (c >= 'a' && c <= 'f') {
                v |= static_cast<uint32_t>(c - 'a' + 10);
            } else if (c >= 'A' && c <= 'F') {
                v |= static_cast<uint32_t>(c - 'A' + 10);
            } else {
                fail("bad \\u escape");
            }
        }
        return v;
    }

    static void appendUtf8(std::string& out, uint32_t cp) {
        if (cp < 0x80) {
            out += static_cast<char>(cp);
        } else if (cp < 0x800) {
            out += static_cast<char>(0xC0 | (cp >> 6));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        } else if (cp < 0x10000) {
            out += static_cast<char>(0xE0 | (cp >> 12));
            out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        } else {
            out += static_cast<char>(0xF0 | (cp >> 18));
            out += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
            out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        }
    }

    Value parseNumber() {
        size_t start = pos_;
        if (peek() == '-') {
            ++pos_;
        }
        if (peek() == '0') {
            ++pos_;
        } else if (std::isdigit(static_cast<unsigned char>(peek()))) {
            while (std::isdigit(static_cast<unsigned char>(peek()))) {
                ++pos_;
            }
        } else {
            fail("bad number");
        }
        if (consume('.')) {
            if (!std::isdigit(static_cast<unsigned char>(peek()))) {
                fail("bad number: fraction");
            }
            while (std::isdigit(static_cast<unsigned char>(peek()))) {
                ++pos_;
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            ++pos_;
            if (peek() == '+' || peek() == '-') {
                ++pos_;
            }
            if (!std::isdigit(static_cast<unsigned char>(peek()))) {
                fail("bad number: exponent");
            }
            while (std::isdigit(static_cast<unsigned char>(peek()))) {
                ++pos_;
            }
        }
        return Value::makeNumber(std::stod(text_.substr(start, pos_ - start)));
    }
};

}  // namespace detail

inline Value parse(const std::string& text) {
    return detail::Cursor(text).parseDocument();
}

inline const Value& Value::at(const std::string& key) const {
    if (type != Type::Object) {
        throw Error("tinyjson: expected object when reading key '" + key + "'");
    }
    auto it = object.find(key);
    if (it == object.end()) {
        throw Error("tinyjson: missing key '" + key + "'");
    }
    return it->second;
}

inline const Value* Value::find(const std::string& key) const {
    if (type != Type::Object) {
        return nullptr;
    }
    auto it = object.find(key);
    return it == object.end() ? nullptr : &it->second;
}

inline long Value::asLong() const {
    if (type != Type::Number) {
        throw Error("tinyjson: expected number");
    }
    return static_cast<long>(number);
}

inline double Value::asDouble() const {
    if (type != Type::Number) {
        throw Error("tinyjson: expected number");
    }
    return number;
}

inline const std::string& Value::asString() const {
    if (type != Type::String) {
        throw Error("tinyjson: expected string");
    }
    return str;
}

inline const std::vector<Value>& Value::asArray() const {
    if (type != Type::Array) {
        throw Error("tinyjson: expected array");
    }
    return array;
}

inline std::vector<float> Value::flattenFloats() const {
    std::vector<float> out;
    if (type == Type::Number) {
        out.push_back(static_cast<float>(number));
        return out;
    }
    for (const auto& item : asArray()) {
        auto flat = item.flattenFloats();
        out.insert(out.end(), flat.begin(), flat.end());
    }
    return out;
}

}  // namespace tinyjson

#endif  // APP_VERSTA_TINYJSON_H
