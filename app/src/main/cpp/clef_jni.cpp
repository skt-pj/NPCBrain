#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <climits>
#include <cstdint>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"
#include "llama-ext.h"

namespace {

constexpr char LOG_TAG[] = "NPCBrainCLEF";
constexpr uint32_t CLEF_CONTEXT_TOKENS = 2048;
constexpr uint32_t CLEF_BATCH_TOKENS = 2048;

std::mutex g_mutex;
llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
std::string g_model_path;
bool g_backend_initialized = false;

void log_error(const std::string & message) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", message.c_str());
}

void throw_java(JNIEnv * env, const std::string & message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message.c_str());
    }
}

std::string jstring_to_utf8(JNIEnv * env, jstring value) {
    if (value == nullptr) return "";
    const char * raw = env->GetStringUTFChars(value, nullptr);
    if (raw == nullptr) return "";
    std::string result(raw);
    env->ReleaseStringUTFChars(value, raw);
    return result;
}

std::vector<std::string> jobject_array_to_strings(JNIEnv * env, jobjectArray array) {
    std::vector<std::string> result;
    if (array == nullptr) return result;
    const jsize n = env->GetArrayLength(array);
    result.reserve(static_cast<size_t>(n));
    for (jsize i = 0; i < n; ++i) {
        auto value = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        result.push_back(jstring_to_utf8(env, value));
        env->DeleteLocalRef(value);
    }
    return result;
}

std::string json_escape(const std::string & value) {
    std::ostringstream out;
    for (unsigned char c : value) {
        switch (c) {
            case '"': out << "\\\""; break;
            case '\\': out << "\\\\"; break;
            case '\b': out << "\\b"; break;
            case '\f': out << "\\f"; break;
            case '\n': out << "\\n"; break;
            case '\r': out << "\\r"; break;
            case '\t': out << "\\t"; break;
            default:
                if (c < 0x20) {
                    const char hex[] = "0123456789abcdef";
                    out << "\\u00" << hex[(c >> 4) & 0x0f] << hex[c & 0x0f];
                } else {
                    out << static_cast<char>(c);
                }
        }
    }
    return out.str();
}

std::string option_json(const std::string & id, const std::string & description) {
    return "{\"description\":\"" + json_escape(description)
            + "\",\"option_id\":\"" + json_escape(id) + "\"}";
}

std::vector<llama_token> tokenize_piece(const llama_vocab * vocab, const std::string & text) {
    if (text.empty()) return {};
    int32_t capacity = static_cast<int32_t>(std::max<size_t>(16, text.size() + 8));
    std::vector<llama_token> tokens(static_cast<size_t>(capacity));
    int32_t n = llama_tokenize(
            vocab,
            text.data(),
            static_cast<int32_t>(text.size()),
            tokens.data(),
            capacity,
            false,
            true);
    if (n < 0) {
        if (n == INT32_MIN) {
            throw std::runtime_error("CLEF tokenizer failed");
        }
        tokens.resize(static_cast<size_t>(-n));
        n = llama_tokenize(
                vocab,
                text.data(),
                static_cast<int32_t>(text.size()),
                tokens.data(),
                static_cast<int32_t>(tokens.size()),
                false,
                true);
    }
    if (n < 0) {
        throw std::runtime_error("CLEF tokenizer capacity retry failed");
    }
    tokens.resize(static_cast<size_t>(n));
    return tokens;
}

struct PromptInput {
    std::vector<llama_token> tokens;
    std::vector<int32_t> orders;
    int32_t n_scores = 0;
};

void append_piece(
        PromptInput & input,
        const llama_vocab * vocab,
        const std::string & text,
        int32_t order) {
    std::vector<llama_token> tokens = tokenize_piece(vocab, text);
    if (order != LLAMA_DECISION_ORDER_NONE && tokens.empty()) {
        throw std::runtime_error("CLEF question/option token span is empty");
    }
    input.tokens.insert(input.tokens.end(), tokens.begin(), tokens.end());
    input.orders.resize(input.tokens.size(), order);
    if (order == LLAMA_DECISION_ORDER_OPTION) {
        input.n_scores++;
    }
}

PromptInput build_prompt(
        const llama_vocab * vocab,
        const std::string & state,
        const std::vector<std::string> & action_ids,
        const std::vector<std::string> & action_descriptions) {
    if (action_ids.empty() || action_ids.size() != action_descriptions.size()) {
        throw std::runtime_error("CLEF action criteria are invalid");
    }

    static const std::string system_prompt =
            "Read the complete state and schema. Decide every field jointly. Each answer "
            "must be exactly one of that field's allowed options.";

    PromptInput input;
    append_piece(
            input,
            vocab,
            "<|im_start|>system\n" + system_prompt
                    + "<|im_end|>\n<|im_start|>user\nSTATE:\n",
            LLAMA_DECISION_ORDER_NONE);
    append_piece(input, vocab, state, LLAMA_DECISION_ORDER_NONE);
    append_piece(input, vocab, "\n\nSCHEMA FIELDS:\n", LLAMA_DECISION_ORDER_NONE);

    append_piece(
            input,
            vocab,
            "\nFIELD 1\nID: action\nTYPE: choice\nINSTRUCTION: ",
            LLAMA_DECISION_ORDER_NONE);
    append_piece(
            input,
            vocab,
            "Choose the single action class this NPC should take now. "
                    "Use only the grounded state, goals, memory, personality, and constraints.",
            LLAMA_DECISION_ORDER_QUESTION_CHOICE);
    append_piece(input, vocab, "\nALLOWED OPTIONS:\n", LLAMA_DECISION_ORDER_NONE);
    for (size_t i = 0; i < action_ids.size(); ++i) {
        append_piece(
                input,
                vocab,
                "OPTION " + std::to_string(i + 1) + ": ",
                LLAMA_DECISION_ORDER_NONE);
        append_piece(
                input,
                vocab,
                option_json(action_ids[i], action_descriptions[i]),
                LLAMA_DECISION_ORDER_OPTION);
        append_piece(input, vocab, "\n", LLAMA_DECISION_ORDER_NONE);
    }
    append_piece(input, vocab, "END FIELD\n", LLAMA_DECISION_ORDER_NONE);

    append_piece(
            input,
            vocab,
            "\nFIELD 2\nID: commit_now\nTYPE: noul\nINSTRUCTION: ",
            LLAMA_DECISION_ORDER_NONE);
    append_piece(
            input,
            vocab,
            "Should this NPC commit to the chosen action class now rather than remain undecided?",
            LLAMA_DECISION_ORDER_QUESTION_NOUL);
    append_piece(input, vocab, "\nALLOWED OPTIONS:\n", LLAMA_DECISION_ORDER_NONE);
    append_piece(input, vocab, "OPTION 1: ", LLAMA_DECISION_ORDER_NONE);
    append_piece(
            input,
            vocab,
            option_json("true", "The proposition is true or the answer is yes."),
            LLAMA_DECISION_ORDER_OPTION);
    append_piece(input, vocab, "\n", LLAMA_DECISION_ORDER_NONE);
    append_piece(input, vocab, "OPTION 2: ", LLAMA_DECISION_ORDER_NONE);
    append_piece(
            input,
            vocab,
            option_json("false", "The proposition is false or the answer is no."),
            LLAMA_DECISION_ORDER_OPTION);
    append_piece(input, vocab, "\nEND FIELD\n", LLAMA_DECISION_ORDER_NONE);

    append_piece(
            input,
            vocab,
            "\n<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"
                    "JOINT SCHEMA DECISIONS:",
            LLAMA_DECISION_ORDER_NONE);

    return input;
}

void unload_locked() {
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_model_path.clear();
}

void ensure_loaded_locked(const std::string & path) {
    if (g_model != nullptr && g_context != nullptr && g_model_path == path) {
        return;
    }
    unload_locked();

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path.c_str(), model_params);
    if (g_model == nullptr) {
        throw std::runtime_error("CLEF GGUFの読み込みに失敗しました");
    }

    char architecture[64] = {};
    if (llama_model_meta_val_str(
            g_model, "general.architecture", architecture, sizeof(architecture)) < 0
            || std::string(architecture) != "clef") {
        unload_locked();
        throw std::runtime_error("CLEF joint headを含むarchitecture=clef GGUFではありません");
    }

    const int cores = static_cast<int>(std::max(1u, std::thread::hardware_concurrency()));
    const int threads = std::max(2, std::min(4, cores - 2));

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = CLEF_CONTEXT_TOKENS;
    context_params.n_batch = CLEF_BATCH_TOKENS;
    context_params.n_ubatch = CLEF_BATCH_TOKENS;
    context_params.n_seq_max = 1;
    context_params.n_rs_seq = 0;
    context_params.n_outputs_max = CLEF_BATCH_TOKENS;
    context_params.n_outputs_max_per_seq = CLEF_BATCH_TOKENS;
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    context_params.embeddings = true;
    context_params.pooling_type = LLAMA_POOLING_TYPE_NONE;
    context_params.offload_kqv = false;
    context_params.op_offload = false;
    context_params.no_perf = false;

    g_context = llama_init_from_model(g_model, context_params);
    if (g_context == nullptr) {
        unload_locked();
        throw std::runtime_error("CLEF contextの初期化に失敗しました");
    }
    g_model_path = path;
}

std::vector<double> run_decision_locked(
        const std::string & model_path,
        const std::string & state,
        const std::vector<std::string> & action_ids,
        const std::vector<std::string> & action_descriptions) {
    ensure_loaded_locked(model_path);
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    PromptInput prompt = build_prompt(vocab, state, action_ids, action_descriptions);

    if (prompt.tokens.empty() || prompt.tokens.size() > CLEF_BATCH_TOKENS) {
        throw std::runtime_error(
                "CLEF入力がローカルcontext上限を超えました: "
                        + std::to_string(prompt.tokens.size())
                        + " / " + std::to_string(CLEF_BATCH_TOKENS) + " tokens");
    }
    if (prompt.n_scores != static_cast<int32_t>(action_ids.size() + 2)) {
        throw std::runtime_error("CLEF option score countが不正です");
    }

    llama_memory_clear(llama_get_memory(g_context), false);
    llama_batch_ext * batch = llama_batch_ext_init(g_context);
    if (batch == nullptr) {
        throw std::runtime_error("CLEF batch初期化に失敗しました");
    }

    try {
        for (size_t i = 0; i < prompt.tokens.size(); ++i) {
            const int32_t idx = llama_batch_ext_add_token(batch, 0, prompt.tokens[i]);
            if (idx < 0) {
                throw std::runtime_error("CLEF batchへtokenを追加できません");
            }
            const llama_pos pos = static_cast<llama_pos>(i);
            if (!llama_batch_ext_set_pos(batch, idx, &pos)
                    || !llama_batch_ext_set_output_embd(batch, idx, true)) {
                throw std::runtime_error("CLEF batch metadataを設定できません");
            }
            if (prompt.orders[i] != LLAMA_DECISION_ORDER_NONE
                    && !llama_batch_ext_set_decision_order(
                            batch,
                            idx,
                            static_cast<llama_decision_order>(prompt.orders[i]))) {
                throw std::runtime_error("CLEF decision spanを設定できません");
            }
        }

        const int32_t status = llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, batch);
        if (status != 0) {
            throw std::runtime_error("CLEF native decode failed: " + std::to_string(status));
        }

        std::vector<double> scores;
        scores.reserve(static_cast<size_t>(prompt.n_scores));
        for (int32_t i = 0; i < prompt.n_scores; ++i) {
            const float * embedding = llama_get_embeddings_ith(g_context, i);
            if (embedding == nullptr || !std::isfinite(embedding[0])) {
                throw std::runtime_error("CLEF decision scoreを取得できません");
            }
            scores.push_back(static_cast<double>(embedding[0]));
        }
        llama_batch_ext_free(batch);
        return scores;
    } catch (...) {
        llama_batch_ext_free(batch);
        throw;
    }
}

} // namespace

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_com_sktpj_npcbrain_ClefNativeRuntime_nativeDecide(
        JNIEnv * env,
        jclass,
        jstring model_path,
        jstring state,
        jobjectArray action_ids,
        jobjectArray action_descriptions) {
    try {
        const std::string model = jstring_to_utf8(env, model_path);
        const std::string state_text = jstring_to_utf8(env, state);
        const std::vector<std::string> ids = jobject_array_to_strings(env, action_ids);
        const std::vector<std::string> descriptions =
                jobject_array_to_strings(env, action_descriptions);
        std::lock_guard<std::mutex> guard(g_mutex);
        const std::vector<double> scores =
                run_decision_locked(model, state_text, ids, descriptions);
        jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(scores.size()));
        if (result != nullptr && !scores.empty()) {
            env->SetDoubleArrayRegion(
                    result,
                    0,
                    static_cast<jsize>(scores.size()),
                    scores.data());
        }
        return result;
    } catch (const std::exception & error) {
        log_error(error.what());
        throw_java(env, error.what());
        return nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sktpj_npcbrain_ClefNativeRuntime_nativeUnload(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> guard(g_mutex);
    unload_locked();
}
