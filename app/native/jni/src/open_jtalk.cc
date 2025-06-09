#include <jni.h>
#include <string>
#include <unordered_set>
#include <map>

#include "open-jtalk/src/mecab/src/mecab.h"
#include "open-jtalk/src/njd/njd.h"
#include "open-jtalk/src/jpcommon/jpcommon.h"
#include "open-jtalk/src/text2mecab/text2mecab.h"
#include "open-jtalk/src/mecab2njd/mecab2njd.h"
#include "open-jtalk/src/njd_set_pronunciation/njd_set_pronunciation.h"
#include "open-jtalk/src/njd_set_digit/njd_set_digit.h"
#include "open-jtalk/src/njd_set_accent_phrase/njd_set_accent_phrase.h"
#include "open-jtalk/src/njd_set_long_vowel/njd_set_long_vowel.h"
#include "open-jtalk/src/njd_set_accent_type/njd_set_accent_type.h"
#include "open-jtalk/src/njd_set_unvoiced_vowel/njd_set_unvoiced_vowel.h"
#include "open-jtalk/src/njd2jpcommon/njd2jpcommon.h"

static const int MAXBUFLEN = 8192;

static const std::map<std::string, std::string> M2P = {
        {"ァ",   "a"},
        {"ア",   "a"},
        {"ィ",   "i"},
        {"イ",   "i"},
        {"ゥ",   "u"},
        {"ウ",   "u"},
        {"ェ",   "e"},
        {"エ",   "e"},
        {"ォ",   "o"},
        {"オ",   "o"},
        {"カ",   "ka"},
        {"ガ",   "ga"},
        {"キ",   "ki"},
        {"ギ",   "gi"},
        {"ク",   "ku"},
        {"グ",   "gu"},
        {"ケ",   "ke"},
        {"ゲ",   "ge"},
        {"コ",   "ko"},
        {"ゴ",   "go"},
        {"サ",   "sa"},
        {"ザ",   "za"},
        {"シ",   "ɕi"},
        {"ジ",   "ʥi"},
        {"ス",   "su"},
        {"ズ",   "zu"},
        {"セ",   "se"},
        {"ゼ",   "ze"},
        {"ソ",   "so"},
        {"ゾ",   "zo"},
        {"タ",   "ta"},
        {"ダ",   "da"},
        {"チ",   "ʨi"},
        {"ヂ",   "ʥi"},
        {"ツ",   "ʦu"},
        {"ヅ",   "zu"},
        {"テ",   "te"},
        {"デ",   "de"},
        {"ト",   "to"},
        {"ド",   "do"},
        {"ナ",   "na"},
        {"ニ",   "ni"},
        {"ヌ",   "nu"},
        {"ネ",   "ne"},
        {"ノ",   "no"},
        {"ハ",   "ha"},
        {"バ",   "ba"},
        {"パ",   "pa"},
        {"ヒ",   "hi"},
        {"ビ",   "bi"},
        {"ピ",   "pi"},
        {"フ",   "fu"},
        {"ブ",   "bu"},
        {"プ",   "pu"},
        {"ヘ",   "he"},
        {"ベ",   "be"},
        {"ペ",   "pe"},
        {"ホ",   "ho"},
        {"ボ",   "bo"},
        {"ポ",   "po"},
        {"マ",   "ma"},
        {"ミ",   "mi"},
        {"ム",   "mu"},
        {"メ",   "me"},
        {"モ",   "mo"},
        {"ャ",   "ja"},
        {"ヤ",   "ja"},
        {"ュ",   "ju"},
        {"ユ",   "ju"},
        {"ョ",   "jo"},
        {"ヨ",   "jo"},
        {"ラ",   "ra"},
        {"リ",   "ri"},
        {"ル",   "ru"},
        {"レ",   "re"},
        {"ロ",   "ro"},
        {"ヮ",   "wa"},
        {"ワ",   "wa"},
        {"ヰ",   "i"},
        {"ヱ",   "e"},
        {"ヲ",   "o"},
        {"ヴ",   "vu"},
        {"ヵ",   "ka"},
        {"ヶ",   "ke"},
        {"ヷ",   "va"},
        {"ヸ",   "vi"},
        {"ヹ",   "ve"},
        {"ヺ",   "vo"},
        {"イェ", "je"},
        {"ウィ", "wi"},
        {"ウゥ", "wu"},
        {"ウェ", "we"},
        {"ウォ", "wo"},
        {"キィ", "ᶄi"},
        {"キェ", "ᶄe"},
        {"キャ", "ᶄa"},
        {"キュ", "ᶄu"},
        {"キョ", "ᶄo"},
        {"ギィ", "ᶃi"},
        {"ギェ", "ᶃe"},
        {"ギャ", "ᶃa"},
        {"ギュ", "ᶃu"},
        {"ギョ", "ᶃo"},
        {"クァ", "Ka"},
        {"クィ", "Ki"},
        {"クゥ", "Ku"},
        {"クェ", "Ke"},
        {"クォ", "Ko"},
        {"クヮ", "Ka"},
        {"グァ", "Ga"},
        {"グィ", "Gi"},
        {"グゥ", "Gu"},
        {"グェ", "Ge"},
        {"グォ", "Go"},
        {"グヮ", "Ga"},
        {"シェ", "ɕe"},
        {"シャ", "ɕa"},
        {"シュ", "ɕu"},
        {"ショ", "ɕo"},
        {"ジェ", "ʥe"},
        {"ジャ", "ʥa"},
        {"ジュ", "ʥu"},
        {"ジョ", "ʥo"},
        {"スィ", "si"},
        {"ズィ", "zi"},
        {"チェ", "ʨe"},
        {"チャ", "ʨa"},
        {"チュ", "ʨu"},
        {"チョ", "ʨo"},
        {"ヂェ", "ʥe"},
        {"ヂャ", "ʥa"},
        {"ヂュ", "ʥu"},
        {"ヂョ", "ʥo"},
        {"ツァ", "ʦa"},
        {"ツィ", "ʦi"},
        {"ツェ", "ʦe"},
        {"ツォ", "ʦo"},
        {"ティ", "ti"},
        {"テェ", "ƫe"},
        {"テャ", "ƫa"},
        {"テュ", "ƫu"},
        {"テョ", "ƫo"},
        {"ディ", "di"},
        {"デェ", "ᶁe"},
        {"デャ", "ᶁa"},
        {"デュ", "ᶁu"},
        {"デョ", "ᶁo"},
        {"トゥ", "tu"},
        {"ドゥ", "du"},
        {"ニィ", "ɲi"},
        {"ニェ", "ɲe"},
        {"ニャ", "ɲa"},
        {"ニュ", "ɲu"},
        {"ニョ", "ɲo"},
        {"ヒィ", "çi"},
        {"ヒェ", "çe"},
        {"ヒャ", "ça"},
        {"ヒュ", "çu"},
        {"ヒョ", "ço"},
        {"ビィ", "ᶀi"},
        {"ビェ", "ᶀe"},
        {"ビャ", "ᶀa"},
        {"ビュ", "ᶀu"},
        {"ビョ", "ᶀo"},
        {"ピィ", "ᶈi"},
        {"ピェ", "ᶈe"},
        {"ピャ", "ᶈa"},
        {"ピュ", "ᶈu"},
        {"ピョ", "ᶈo"},
        {"ファ", "fa"},
        {"フィ", "fi"},
        {"フェ", "fe"},
        {"フォ", "fo"},
        {"ミィ", "ᶆi"},
        {"ミェ", "ᶆe"},
        {"ミャ", "ᶆa"},
        {"ミュ", "ᶆu"},
        {"ミョ", "ᶆo"},
        {"リィ", "ᶉi"},
        {"リェ", "ᶉe"},
        {"リャ", "ᶉa"},
        {"リュ", "ᶉu"},
        {"リョ", "ᶉo"},
        {"ヴァ", "va"},
        {"ヴィ", "vi"},
        {"ヴェ", "ve"},
        {"ヴォ", "vo"},
        {"ヴャ", "ᶀa"},
        {"ヴュ", "ᶀu"},
        {"ヴョ", "ᶀo"},
};

static const std::map<std::string, std::string> PUNCT_MAP = {
        {"«", "“"},
        {"»", "”"},
        {"、", ","},
        {"。", "."},
        {"〈", "“"},
        {"〉", "”"},
        {"《", "“"},
        {"》", "”"},
        {"「", "“"},
        {"」", "”"},
        {"『", "“"},
        {"』", "”"},
        {"【", "“"},
        {"】", "”"},
        {"！", "!"},
        {"（", "("},
        {"）", ")"},
        {"：", ":"},
        {"；", ";"},
        {"？", "?"}
};

static const std::unordered_set<std::string> VOWELS = {"a", "e", "i", "o", "u"};
static const std::unordered_set<std::string> PUNCT_VALUES = {"!", "\"", "(", ")", ",", ".", ":",
                                                             ";", "?", "—", "“", "”", "…"};
static const std::unordered_set<std::string> PUNCT_STARTS = {"(", "“"};
static const std::unordered_set<std::string> PUNCT_STOPS = {"!", ")", ",", ".", ":", ";", "?", "”"};

struct NodeFeature {
    std::string string;
    std::string pos;
    std::string pos_group1;
    std::string pos_group2;
    std::string pos_group3;
    std::string ctype;
    std::string cform;
    std::string orig;
    std::string read;
    std::string pron;
    int acc{};
    int mora_size{};
    std::string chain_rule;
    bool chain_flag{};
};

class OpenJTalk {
public:
    OpenJTalk(const char *path) {
        Mecab_initialize(&mecab);
        NJD_initialize(&njd);
        JPCommon_initialize(&jpCommon);

        Mecab_load(&mecab, path);
    }

    const char *phonemize(const char *text) {
        char buff[MAXBUFLEN];
        text2mecab(buff, text);
        Mecab_analysis(&mecab, buff);
        mecab2njd(&njd, Mecab_get_feature(&mecab), Mecab_get_size(&mecab));

        njd_set_pronunciation(&njd);
        njd_set_digit(&njd);
        njd_set_accent_phrase(&njd);
        njd_set_accent_type(&njd);
        njd_set_unvoiced_vowel(&njd);
        njd_set_long_vowel(&njd);

        auto features = njdToFeature(&njd);

        JPCommon_refresh(&jpCommon);
        NJD_refresh(&njd);
        Mecab_refresh(&mecab);

        std::string output;
        for (const auto &feature: features) {
            std::string pron = feature.pron;
            int mora_size = feature.mora_size;
            std::vector<std::string> moras;

            if (mora_size > 0) {
                moras = pronounciationToMoras(pron);
            }

            bool chain_flag = mora_size > 0 && !output.empty() && output.back() > 0 &&
                              (feature.chain_flag == 1 || moras[0] == "ー");

            int acc = feature.acc;
            int mcount = 0;
            std::vector<int> accents;
            int last_a = 0;

            for (const auto &mora: moras) {
                mcount += 1;
                if (acc == 0) {
                    accents.push_back(mcount == 1 ? 0 : (last_a == 0 ? 1 : 2));
                } else if (acc == mcount) {
                    accents.push_back(3);
                } else if (1 < mcount && mcount < acc) {
                    accents.push_back(last_a == 0 ? 1 : 2);
                } else {
                    accents.push_back(0);
                }
                last_a = accents.back();
            }

            std::string surface = feature.string;
            if (PUNCT_MAP.find(surface) != PUNCT_MAP.end()) {
                auto result = PUNCT_MAP.find(surface);
                if (result != PUNCT_MAP.end()) {
                    surface += result->second;
                }
            }

            std::string whitespace, phonemes, pitch;
            if (!moras.empty()) {
                for (size_t i = 0; i < moras.size(); ++i) {
                    auto result = M2P.find(moras[i]);
                    if (result == M2P.end()) {
                        continue;
                    }

                    auto ps = result->second;
                    auto a = accents[i];
                    if (a == 0 || a == 2) {
                        bool vowels = false;
                        for (const auto& v : VOWELS) {
                            if (ps.find(v) == std::string::npos) {
                                vowels = true;
                            }
                        }
                        if (vowels) {
                            phonemes += ps;
                        }
                    } else if (a == 1) {
                        phonemes += "↑" + ps;
                    } else {
                        if (i > 0 && accents[i - 1] == 0) {
                            phonemes += "↑";
                        } else if (i == 0 && chain_flag && !phonemes.empty() && !phonemes.back() && phonemes.back() == 0) {
                            phonemes += "↑";
                        }
                        phonemes += ps + "↓";
                    }
                }
            } else if (!surface.empty() && std::all_of(surface.begin(), surface.end(), [](char s) {
                return PUNCT_VALUES.find(std::string(1, s)) != PUNCT_VALUES.end();
            })) {
                phonemes = surface;
                if (PUNCT_STOPS.find(std::string(1, surface.back())) != PUNCT_STOPS.end()) {
                    if (!phonemes.empty()) {
                        phonemes.append(" ");
                    }
                }
            }

            output.append(phonemes);
        }

        return strdup(output.c_str());
    }

private:
    Mecab mecab{};
    NJD njd{};
    JPCommon jpCommon{};

    static std::vector<NodeFeature> njdToFeature(NJD *instance) {
        std::vector<NodeFeature> features;

        auto node = instance->head;
        while (node != nullptr) {
            features.push_back(nodeToFeature(node));
            node = node->next;
        }

        return features;
    }

    static NodeFeature nodeToFeature(_NJDNode *node) {
        NodeFeature feature;

        feature.string = NJDNode_get_string(node);
        feature.pos = NJDNode_get_pos(node);
        feature.pos_group1 = NJDNode_get_pos_group1(node);
        feature.pos_group2 = NJDNode_get_pos_group2(node);
        feature.pos_group3 = NJDNode_get_pos_group3(node);
        feature.ctype = NJDNode_get_ctype(node);
        feature.cform = NJDNode_get_cform(node);
        feature.orig = NJDNode_get_orig(node);
        feature.read = NJDNode_get_read(node);
        feature.pron = NJDNode_get_pron(node);
        feature.acc = NJDNode_get_acc(node);
        feature.mora_size = NJDNode_get_mora_size(node);
        feature.chain_rule = NJDNode_get_chain_rule(node);
        feature.chain_flag = NJDNode_get_chain_flag(node);

        return feature;
    }

    static std::vector<std::string> pronounciationToMoras(const std::string &pron) {
        std::vector<std::string> moras;
        size_t index = 0;

        while (index < pron.length()) {
            std::string currentChar = getNextUtf8Char(pron, index);

            if (index < pron.length()) {
                std::string nextChar = getNextUtf8Char(pron, index);
                std::string twoCharKey = currentChar + nextChar;

                if (M2P.find(twoCharKey) != M2P.end()) {
                    if (!moras.empty()) {
                        std::string combined = moras.back() + twoCharKey;
                        if (M2P.find(combined) != M2P.end()) {
                            moras.back() = combined;
                            continue;
                        }
                    }
                    moras.push_back(twoCharKey);
                    continue;
                } else {
                    index -= nextChar.length(); // Revert index increment for nextChar
                }
            }

            if (M2P.find(currentChar) != M2P.end()) {
                if (!moras.empty()) {
                    std::string combined = moras.back() + currentChar;
                    if (M2P.find(combined) != M2P.end()) {
                        moras.back() = combined;
                        continue;
                    }
                }
                moras.push_back(currentChar);
            }
        }

        return moras;
    }

    static std::string getNextUtf8Char(const std::string &str, size_t &index) {
        if (index >= str.length()) {
            throw std::out_of_range("Index out of range");
        }

        unsigned char byte = str[index];
        size_t charLength = 1;

        if ((byte & 0xF0) == 0xF0) {
            charLength = 4;
        } else if ((byte & 0xE0) == 0xE0) {
            charLength = 3;
        } else if ((byte & 0xC0) == 0xC0) {
            charLength = 2;
        }

        if (index + charLength > str.length()) {
            throw std::runtime_error("Invalid UTF-8 sequence");
        }

        std::string utf8Char = str.substr(index, charLength);
        index += charLength;
        return utf8Char;
    }
};

std::unordered_map<jlong, std::unique_ptr<OpenJTalk>> openJTalkInstances;
jlong openJTalkInstanceCounter = 0;

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_speech_OpenJTalk_construct(
        JNIEnv *env,
        jobject,
        jstring path
) {
    const char *c_path = path ? env->GetStringUTFChars(path, nullptr) : nullptr;

    auto openJTalk = std::make_unique<OpenJTalk>(c_path);
    jlong handle = ++openJTalkInstanceCounter;
    openJTalkInstances[handle] = std::move(openJTalk);
    return handle;
}

JNIEXPORT jstring JNICALL
Java_app_versta_translate_bridge_speech_OpenJTalk_phonemize(
        JNIEnv *env,
        jobject,
        jlong handle,
        jstring text
) {
    const char *nativeText = env->GetStringUTFChars(text, nullptr);

    auto openJTalk = openJTalkInstances[handle].get();
    if (!openJTalk) {
        return nullptr;
    }

    auto phonemes = openJTalk->phonemize(nativeText);
    jstring result = env->NewStringUTF(phonemes);

    env->ReleaseStringUTFChars(text, nativeText);

    return result;
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_speech_OpenJTalk_close(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    if (openJTalkInstances.erase(handle) > 0) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

#ifdef __cplusplus
}
#endif