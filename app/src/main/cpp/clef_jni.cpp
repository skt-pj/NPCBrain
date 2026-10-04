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

enum class ClefBackendMode {
    GPU,
    CPU,
};

std::mutex g_mutex;
llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
std::string g_model_path;
ClefBackendMode g_backend_mode = ClefBackendMode::CPU;
bool g_backend_mode_set = false;
bool g_backend_initialized = false;

void log_error(const std::string & message) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", message.c_str());
}

void throw_java(JNIEnv * env, const std::string & message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) env->ThrowNew(cls, message.c_str());
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

std::vector<int32_t> jint_array_to_ints(JNIEnv * env, jintArray array) {
    std::vector<int32_t> result;
    if (array == nullptr) return result;
    const jsize n = env->GetArrayLength(array);
    result.resize(static_cast<size_t>(n));
    if (n > 0) {
        std::vector<jint> values(static_cast<size_t>(n));
        env->GetIntArrayRegion(array, 0, n, values.data());
        for (jsize i = 0; i < n; ++i) result[static_cast<size_t>(i)] = values[static_cast<size_t>(i)];
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
        if (n == INT32_MIN) throw std::runtime_error("CLEF tokenizer failed");
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
    if (n < 0) throw std::runtime_error("CLEF tokenizer capacity retry failed");
    tokens.resize(static_cast<size_t>(n));
    return tokens;
}

struct PromptInput {
    std::vector<llama_token> tokens;
    std::vector<int32_t> orders;
    int32_t n_scores = 0;
};

struct FieldSpec {
    std::string id;
    std::string instruction;
    std::vector<std::string> option_ids;
    std::vector<std::string> option_descriptions;
};

void append_tokens(
        PromptInput & input,
        const std::vector<llama_token> & tokens,
        int32_t order) {
    if (order != LLAMA_DECISION_ORDER_NONE && tokens.empty()) {
        throw std::runtime_error("CLEF question/option token span is empty");
    }
    input.tokens.insert(input.tokens.end(), tokens.begin(), tokens.end());
    input.orders.resize(input.tokens.size(), order);
    if (order == LLAMA_DECISION_ORDER_OPTION) input.n_scores++;
}

void append_piece(
        PromptInput & input,
        const llama_vocab * vocab,
        const std::string & text,
        int32_t order) {
    append_tokens(input, tokenize_piece(vocab, text), order);
}

void append_bounded_state(
        PromptInput & input,
        const llama_vocab * vocab,
        const std::string & state,
        size_t max_state_tokens) {
    std::vector<llama_token> tokens = tokenize_piece(vocab, state);
    if (tokens.size() <= max_state_tokens) {
        append_tokens(input, tokens, LLAMA_DECISION_ORDER_NONE);
        return;
    }

    static const std::string omitted =
            "\n...[middle of grounded state omitted to fit local CLEF context]...\n";
    std::vector<llama_token> omitted_tokens = tokenize_piece(vocab, omitted);
    if (max_state_tokens <= omitted_tokens.size()) {
        tokens.resize(max_state_tokens);
        append_tokens(input, tokens, LLAMA_DECISION_ORDER_NONE);
        return;
    }

    const size_t payload = max_state_tokens - omitted_tokens.size();
    const size_t head_count = payload * 2 / 3;
    const size_t tail_count = payload - head_count;
    std::vector<llama_token> head(tokens.begin(), tokens.begin() + head_count);
    std::vector<llama_token> tail(tokens.end() - tail_count, tokens.end());
    append_tokens(input, head, LLAMA_DECISION_ORDER_NONE);
    append_tokens(input, omitted_tokens, LLAMA_DECISION_ORDER_NONE);
    append_tokens(input, tail, LLAMA_DECISION_ORDER_NONE);
}

PromptInput build_prompt(
        const llama_vocab * vocab,
        const std::string & state,
        const std::vector<FieldSpec> & fields,
        size_t max_state_tokens) {
    if (fields.empty()) throw std::runtime_error("CLEF specialist fields are empty");

    static const std::string system_prompt =
            "You are one fixed specialist reaction function inside an NPC brain. "
            "Read only the grounded state. For every field choose exactly one allowed reaction. "
            "Do not invent events, plans, actions, memories, or explanations.";

    PromptInput input;
    append_piece(
            input,
            vocab,
            "<|im_start|>system\n" + system_prompt
                    + "<|im_end|>\n<|im_start|>user\nGROUNDED STATE:\n",
            LLAMA_DECISION_ORDER_NONE);
    append_bounded_state(input, vocab, state, max_state_tokens);
    append_piece(input, vocab, "\n\nFIXED REACTION FIELDS:\n", LLAMA_DECISION_ORDER_NONE);

    for (size_t field_index = 0; field_index < fields.size(); ++field_index) {
        const FieldSpec & field = fields[field_index];
        if (field.id.empty() || field.instruction.empty()
                || field.option_ids.size() < 2
                || field.option_ids.size() != field.option_descriptions.size()) {
            throw std::runtime_error("CLEF specialist field is invalid");
        }

        append_piece(
                input,
                vocab,
                "\nFIELD " + std::to_string(field_index + 1)
                        + "\nID: " + field.id
                        + "\nTYPE: choice\nINSTRUCTION: ",
                LLAMA_DECISION_ORDER_NONE);
        append_piece(
                input,
                vocab,
                field.instruction,
                LLAMA_DECISION_ORDER_QUESTION_CHOICE);
        append_piece(input, vocab, "\nALLOWED OPTIONS:\n", LLAMA_DECISION_ORDER_NONE);

        for (size_t option_index = 0; option_index < field.option_ids.size(); ++option_index) {
            append_piece(
                    input,
                    vocab,
                    "OPTION " + std::to_string(option_index + 1) + ": ",
                    LLAMA_DECISION_ORDER_NONE);
            append_piece(
                    input,
                    vocab,
                    option_json(
                            field.option_ids[option_index],
                            field.option_descriptions[option_index]),
                    LLAMA_DECISION_ORDER_OPTION);
            append_piece(input, vocab, "\n", LLAMA_DECISION_ORDER_NONE);
        }
        append_piece(input, vocab, "END FIELD\n", LLAMA_DECISION_ORDER_NONE);
    }

    append_piece(
            input,
            vocab,
            "\n<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"
                    "FIXED REACTION DECISIONS:",
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
    g_backend_mode_set = false;
}

void ensure_loaded_locked(const std::string & path, ClefBackendMode mode) {
    if (g_model != nullptr
            && g_context != nullptr
            && g_model_path == path
            && g_backend_mode_set
            && g_backend_mode == mode) {
        return;
    }
    unload_locked();

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    const bool gpu = mode == ClefBackendMode::GPU;
    if (gpu && !llama_supports_gpu_offload()) {
        throw std::runtime_error("CLEF GPU backend is unavailable");
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = gpu ? -1 : 0;
    g_model = llama_model_load_from_file(path.c_str(), model_params);
    if (g_model == nullptr) throw std::runtime_error("CLEF GGUFの読み込みに失敗しました");

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
    context_params.offload_kqv = gpu;
    context_params.op_offload = gpu;
    context_params.no_perf = false;

    g_context = llama_init_from_model(g_model, context_params);
    if (g_context == nullptr) {
        unload_locked();
        throw std::runtime_error("CLEF contextの初期化に失敗しました");
    }
    g_model_path = path;
    g_backend_mode = mode;
    g_backend_mode_set = true;
}

std::vector<double> run_decision_once_locked(
        const std::string & model_path,
        const std::string & state,
        const std::vector<FieldSpec> & fields,
        ClefBackendMode mode) {
    ensure_loaded_locked(model_path, mode);
    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    PromptInput fixed = build_prompt(vocab, "", fields, 0);
    if (fixed.tokens.size() >= CLEF_CONTEXT_TOKENS) {
        throw std::runtime_error(
                "CLEF固定schemaがローカルcontext上限を超えました: "
                        + std::to_string(fixed.tokens.size())
                        + " / " + std::to_string(CLEF_CONTEXT_TOKENS) + " tokens");
    }

    const size_t state_budget =
            static_cast<size_t>(CLEF_CONTEXT_TOKENS) - fixed.tokens.size();
    PromptInput prompt = build_prompt(vocab, state, fields, state_budget);

    int32_t expected_scores = 0;
    for (const FieldSpec & field : fields) {
        expected_scores += static_cast<int32_t>(field.option_ids.size());
    }
    if (prompt.tokens.empty() || prompt.tokens.size() > CLEF_CONTEXT_TOKENS) {
        throw std::runtime_error(
                "CLEF入力がローカルcontext上限を超えました: "
                        + std::to_string(prompt.tokens.size())
                        + " / " + std::to_string(CLEF_CONTEXT_TOKENS) + " tokens");
    }
    if (prompt.n_scores != expected_scores) {
        throw std::runtime_error("CLEF option score countが不正です");
    }

    llama_memory_clear(llama_get_memory(g_context), false);
    llama_batch_ext * batch = llama_batch_ext_init(g_context);
    if (batch == nullptr) throw std::runtime_error("CLEF batch初期化に失敗しました");

    try {
        for (size_t i = 0; i < prompt.tokens.size(); ++i) {
            const int32_t idx = llama_batch_ext_add_token(batch, 0, prompt.tokens[i]);
            if (idx < 0) throw std::runtime_error("CLEF batchへtokenを追加できません");
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

std::vector<double> run_decision_locked(
        const std::string & model_path,
        const std::string & state,
        const std::vector<FieldSpec> & fields) {
    try {
        return run_decision_once_locked(
                model_path, state, fields, ClefBackendMode::GPU);
    } catch (const std::exception & gpu_error) {
        log_error(std::string("CLEF GPU failed; retrying CPU: ") + gpu_error.what());
        unload_locked();
        try {
            return run_decision_once_locked(
                    model_path, state, fields, ClefBackendMode::CPU);
        } catch (const std::exception & cpu_error) {
            throw std::runtime_error(
                    std::string("CLEF GPU/CPU execution failed. GPU: ")
                    + gpu_error.what()
                    + "; CPU: "
                    + cpu_error.what());
        }
    }
}

std::vector<FieldSpec> make_fields(
        const std::vector<std::string> & field_ids,
        const std::vector<std::string> & instructions,
        const std::vector<std::string> & flat_option_ids,
        const std::vector<std::string> & flat_option_descriptions,
        const std::vector<int32_t> & option_counts) {
    if (field_ids.empty()
            || field_ids.size() != instructions.size()
            || field_ids.size() != option_counts.size()
            || flat_option_ids.size() != flat_option_descriptions.size()) {
        throw std::runtime_error("CLEF JNI schema shape is invalid");
    }

    std::vector<FieldSpec> fields;
    fields.reserve(field_ids.size());
    size_t offset = 0;
    for (size_t i = 0; i < field_ids.size(); ++i) {
        const int32_t count = option_counts[i];
        if (count < 2 || offset + static_cast<size_t>(count) > flat_option_ids.size()) {
            throw std::runtime_error("CLEF JNI option count is invalid");
        }
        FieldSpec field;
        field.id = field_ids[i];
        field.instruction = instructions[i];
        field.option_ids.assign(
                flat_option_ids.begin() + static_cast<std::ptrdiff_t>(offset),
                flat_option_ids.begin() + static_cast<std::ptrdiff_t>(offset + count));
        field.option_descriptions.assign(
                flat_option_descriptions.begin() + static_cast<std::ptrdiff_t>(offset),
                flat_option_descriptions.begin() + static_cast<std::ptrdiff_t>(offset + count));
        fields.push_back(std::move(field));
        offset += static_cast<size_t>(count);
    }
    if (offset != flat_option_ids.size()) {
        throw std::runtime_error("CLEF JNI flattened options contain unused entries");
    }
    return fields;
}

} // namespace

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_com_sktpj_npcbrain_ClefNativeRuntime_nativeEvaluate(
        JNIEnv * env,
        jclass,
        jstring model_path,
        jstring state,
        jobjectArray field_ids,
        jobjectArray instructions,
        jobjectArray option_ids,
        jobjectArray option_descriptions,
        jintArray option_counts) {
    try {
        const std::string model = jstring_to_utf8(env, model_path);
        const std::string state_text = jstring_to_utf8(env, state);
        const std::vector<FieldSpec> fields = make_fields(
                jobject_array_to_strings(env, field_ids),
                jobject_array_to_strings(env, instructions),
                jobject_array_to_strings(env, option_ids),
                jobject_array_to_strings(env, option_descriptions),
                jint_array_to_ints(env, option_counts));

        std::lock_guard<std::mutex> guard(g_mutex);
        const std::vector<double> scores =
                run_decision_locked(model, state_text, fields);
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
