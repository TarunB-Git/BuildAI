// Android bridge for the pinned llama.cpp submodule (MIT license).
#include <jni.h>
#include <llama.h>
#include <chat.h>
#include <algorithm>
#include <atomic>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
constexpr int BATCH_SIZE = 256;
struct Request { std::atomic_bool cancelled{false}; };
struct Engine {
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    common_chat_templates_ptr templates;
    std::vector<llama_token> cached_tokens;
    ~Engine() {
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};
std::once_flag backend_once;

bool cancelled(void *data) {
    return static_cast<Request *>(data)->cancelled.load(std::memory_order_relaxed);
}
// Loading may report from a native worker. Global refs and an attached thread are
// required; never pass a caller-thread local JNI reference to that worker.
struct LoadProgress {
    JavaVM *vm;
    jobject callbacks;
    jmethodID method;
    Request *request;
    std::atomic_bool callback_failed{false};
};
bool load_progress(float value, void *data) {
    auto *progress = static_cast<LoadProgress *>(data);
    if (cancelled(progress->request)) return false;
    JNIEnv *env = nullptr;
    bool attached = false;
    if (progress->vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (progress->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return false;
        attached = true;
    }
    if (!env) return false;
    env->CallVoidMethod(progress->callbacks, progress->method, 0,
        static_cast<jlong>(std::clamp(value, 0.0f, 1.0f) * 1000), static_cast<jlong>(1000));
    const bool failed = env->ExceptionCheck();
    if (failed) {
        progress->callback_failed.store(true);
        env->ExceptionClear();
    }
    if (attached) progress->vm->DetachCurrentThread();
    return !failed && !cancelled(progress->request);
}

void fail(JNIEnv *env, const std::string &message) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) {
        env->ThrowNew(type, message.c_str());
        env->DeleteLocalRef(type);
    }
}

// Java strings use UTF-16, while llama.cpp expects standard UTF-8 (including emoji).
std::string utf8(JNIEnv *env, jstring value) {
    if (!value) return {};
    const auto *chars = env->GetStringChars(value, nullptr);
    if (!chars) return {};
    const auto size = env->GetStringLength(value);
    std::string text;
    for (jsize i = 0; i < size; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xd800 && cp <= 0xdbff && i + 1 < size && chars[i + 1] >= 0xdc00 && chars[i + 1] <= 0xdfff) {
            cp = 0x10000 + ((cp - 0xd800) << 10) + (chars[++i] - 0xdc00);
        } else if (cp >= 0xd800 && cp <= 0xdfff) {
            cp = 0xfffd;
        }
        if (cp < 0x80) text.push_back(static_cast<char>(cp));
        else if (cp < 0x800) {
            text.push_back(static_cast<char>(0xc0 | (cp >> 6)));
            text.push_back(static_cast<char>(0x80 | (cp & 0x3f)));
        } else if (cp < 0x10000) {
            text.push_back(static_cast<char>(0xe0 | (cp >> 12)));
            text.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3f)));
            text.push_back(static_cast<char>(0x80 | (cp & 0x3f)));
        } else {
            text.push_back(static_cast<char>(0xf0 | (cp >> 18)));
            text.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3f)));
            text.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3f)));
            text.push_back(static_cast<char>(0x80 | (cp & 0x3f)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return text;
}

// Buffer incomplete token pieces until they form a complete UTF-8 string.
jstring java_string(JNIEnv *env, const std::string &text) {
    std::vector<jchar> chars;
    for (size_t i = 0; i < text.size();) {
        unsigned char a = text[i++];
        uint32_t cp;
        size_t extra;
        if (a < 0x80) { cp = a; extra = 0; }
        else if ((a & 0xe0) == 0xc0) { cp = a & 0x1f; extra = 1; }
        else if ((a & 0xf0) == 0xe0) { cp = a & 0x0f; extra = 2; }
        else if ((a & 0xf8) == 0xf0) { cp = a & 0x07; extra = 3; }
        else return nullptr;
        if (i + extra > text.size()) return nullptr;
        for (size_t j = 0; j < extra; ++j) {
            unsigned char b = text[i++];
            if ((b & 0xc0) != 0x80) return nullptr;
            cp = (cp << 6) | (b & 0x3f);
        }
        if (cp > 0x10ffff || (cp >= 0xd800 && cp <= 0xdfff)) return nullptr;
        if (cp <= 0xffff) chars.push_back(static_cast<jchar>(cp));
        else {
            cp -= 0x10000;
            chars.push_back(static_cast<jchar>(0xd800 + (cp >> 10)));
            chars.push_back(static_cast<jchar>(0xdc00 + (cp & 0x3ff)));
        }
    }
    return env->NewString(chars.data(), static_cast<jsize>(chars.size()));
}

struct DecodeGuard {
    Engine *engine;
    bool complete = false;
    DecodeGuard(Engine *value, Request *request) : engine(value) {
        llama_set_abort_callback(engine->context, cancelled, request);
    }
    ~DecodeGuard() {
        // The request is freed after JNI returns; never leave its pointer in the cached context.
        llama_set_abort_callback(engine->context, nullptr, nullptr);
        if (!complete) {
            llama_memory_clear(llama_get_memory(engine->context), false);
            engine->cached_tokens.clear();
        }
    }
};
struct Batch {
    llama_batch value = llama_batch_init(BATCH_SIZE, 0, 1);
    ~Batch() { llama_batch_free(value); }
    void add(llama_token token, llama_pos position, bool logits) {
        int i = value.n_tokens++;
        value.token[i] = token;
        value.pos[i] = position;
        value.n_seq_id[i] = 1;
        value.seq_id[i][0] = 0;
        value.logits[i] = logits;
    }
};

void generate(JNIEnv *env, Engine *engine, jobjectArray roles, jobjectArray contents,
              int max_tokens, Request *request, jobject callbacks, const std::string &grammar) {
    if (!engine || !request || !roles || !contents || !callbacks) throw std::runtime_error("Model or prompt is missing.");
    const auto n = env->GetArrayLength(roles);
    if (n != env->GetArrayLength(contents) || n < 2) throw std::runtime_error("Invalid chat history.");
    DecodeGuard guard(engine, request);
    jclass callback_type = env->GetObjectClass(callbacks);
    jmethodID on_token = env->GetMethodID(callback_type, "onToken", "(Ljava/lang/String;)Z");
    jmethodID on_stage = env->GetMethodID(callback_type, "onStage", "(I)V");
    jmethodID on_progress = env->GetMethodID(callback_type, "onProgress", "(IJJ)V");
    env->DeleteLocalRef(callback_type);
    if (!on_token || !on_stage || !on_progress || env->ExceptionCheck() || cancelled(request)) return;
    env->CallVoidMethod(callbacks, on_stage, 1);
    if (env->ExceptionCheck()) return;
    common_chat_templates_inputs inputs;
    inputs.enable_thinking = false;
    inputs.force_pure_content = true; // Text replies need template rendering, not tool/reasoning parser discovery.
    inputs.messages.reserve(n);
    for (jsize i = 0; i < n; ++i) {
        auto role = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
        auto content = static_cast<jstring>(env->GetObjectArrayElement(contents, i));
        auto role_text = utf8(env, role);
        auto content_text = utf8(env, content);
        env->DeleteLocalRef(role);
        env->DeleteLocalRef(content);
        if (env->ExceptionCheck()) return;
        if (role_text != "system" && role_text != "user" && role_text != "assistant") throw std::runtime_error("Unsupported conversation role.");
        inputs.messages.push_back({role_text, content_text});
    }
    auto *vocab = llama_model_get_vocab(engine->model);
    const int context_size = static_cast<int>(llama_n_ctx(engine->context));
    const int reserve = std::max(32, std::min(max_tokens, 512));
    std::string prompt;
    int count;
    while (true) {
        if (cancelled(request)) return;
        try {
            prompt = common_chat_templates_apply(engine->templates.get(), inputs).prompt;
        } catch (const std::exception &error) {
            throw std::runtime_error(std::string("Chat formatting failed: ") + error.what());
        }
        count = llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
        count = count < 0 ? -count : count;
        if (count <= 0) throw std::runtime_error("Could not tokenize the chat prompt.");
        if (count + reserve < context_size) break;
        if (inputs.messages.size() <= 2) throw std::runtime_error("This message is too long for the model context. Shorten it and retry.");
        inputs.messages.erase(inputs.messages.begin() + 1);
        if (inputs.messages.size() > 2 && inputs.messages[1].role == "assistant") inputs.messages.erase(inputs.messages.begin() + 1);
    }
    std::vector<llama_token> tokens(count);
    int parsed = llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), count, true, true);
    if (parsed <= 0) throw std::runtime_error("Could not tokenize the prompt.");
    tokens.resize(parsed);

    // Reuse only a verified token prefix; recurrent models may require a full reset.
    size_t shared = 0;
    while (shared < tokens.size() - 1 && shared < engine->cached_tokens.size() && tokens[shared] == engine->cached_tokens[shared]) ++shared;
    auto memory = llama_get_memory(engine->context);
    if (!llama_memory_seq_rm(memory, 0, static_cast<llama_pos>(shared), -1)) {
        llama_memory_clear(memory, false);
        shared = 0;
    }
    engine->cached_tokens.resize(shared);
    env->CallVoidMethod(callbacks, on_stage, 2);
    env->CallVoidMethod(callbacks, on_progress, 1, static_cast<jlong>(shared), static_cast<jlong>(tokens.size()));
    if (env->ExceptionCheck()) return;
    Batch batch;
    for (size_t offset = shared; offset < tokens.size(); offset += BATCH_SIZE) {
        if (cancelled(request)) return;
        const auto length = std::min<size_t>(BATCH_SIZE, tokens.size() - offset);
        batch.value.n_tokens = 0;
        for (size_t j = 0; j < length; ++j) batch.add(tokens[offset + j], offset + j, offset + j + 1 == tokens.size());
        const int result = llama_decode(engine->context, batch.value);
        if (cancelled(request)) return;
        if (result != 0) throw std::runtime_error("Prompt processing failed (code " + std::to_string(result) + "). Try a smaller model.");
        engine->cached_tokens.insert(engine->cached_tokens.end(), tokens.begin() + offset, tokens.begin() + offset + length);
        env->CallVoidMethod(callbacks, on_progress, 1,
            static_cast<jlong>(offset + length), static_cast<jlong>(tokens.size()));
        if (env->ExceptionCheck()) return;
    }

    auto sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>(
        llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
    if (!grammar.empty()) {
        auto *format_sampler = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
        if (!format_sampler) throw std::runtime_error("The app output format could not initialize.");
        llama_sampler_chain_add(sampler.get(), format_sampler);
    }
    char architecture[64] = {};
    llama_model_meta_val_str(engine->model, "general.architecture", architecture, sizeof(architecture));
    const bool qwen3 = std::string(architecture) == "qwen3";
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(qwen3 ? 20 : 40));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(qwen3 ? 0.8f : 0.9f, 1));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    env->CallVoidMethod(callbacks, on_stage, 3);
    if (env->ExceptionCheck()) return;
    std::string pending;
    const int limit = std::min(max_tokens, context_size - parsed - 1);
    for (int i = 0; i < limit; ++i) {
        if (cancelled(request)) return;
        const llama_token next = llama_sampler_sample(sampler.get(), engine->context, -1);
        if (llama_vocab_is_eog(vocab, next)) break;
        int piece_size = llama_token_to_piece(vocab, next, nullptr, 0, 0, false);
        if (piece_size < 0) piece_size = -piece_size;
        if (piece_size > 0 && piece_size < 65536) {
            std::string piece(piece_size, '\0');
            const int actual = llama_token_to_piece(vocab, next, piece.data(), piece_size, 0, false);
            if (actual > 0) pending.append(piece.data(), actual);
        }
        if (!pending.empty()) {
            jstring chunk = java_string(env, pending);
            if (chunk) {
                const auto keep_going = env->CallBooleanMethod(callbacks, on_token, chunk);
                env->DeleteLocalRef(chunk);
                pending.clear();
                if (env->ExceptionCheck() || !keep_going) return;
            }
        }
        batch.value.n_tokens = 0;
        batch.add(next, parsed + i, true);
        const int result = llama_decode(engine->context, batch.value);
        if (cancelled(request)) return;
        if (result != 0) throw std::runtime_error("Token generation failed (code " + std::to_string(result) + "). Try a smaller model.");
        engine->cached_tokens.push_back(next);
        env->CallVoidMethod(callbacks, on_progress, 2, static_cast<jlong>(i + 1), static_cast<jlong>(limit));
        if (env->ExceptionCheck()) return;
    }
    guard.complete = true;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_buildai_app_data_local_NativeLlmEngine_nativeCreateRequest(JNIEnv *env, jobject) {
    try { return reinterpret_cast<jlong>(new Request()); }
    catch (const std::exception &error) { fail(env, error.what()); return 0; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_buildai_app_data_local_NativeLlmEngine_nativeCancelRequest(JNIEnv *, jobject, jlong request) {
    if (request) reinterpret_cast<Request *>(request)->cancelled.store(true, std::memory_order_relaxed);
}
extern "C" JNIEXPORT void JNICALL
Java_com_buildai_app_data_local_NativeLlmEngine_nativeReleaseRequest(JNIEnv *, jobject, jlong request) {
    delete reinterpret_cast<Request *>(request);
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_buildai_app_data_local_NativeLlmEngine_nativeLoad(JNIEnv *env, jobject, jstring path,
                                                        jint context_size, jint threads, jlong request_handle, jobject callbacks) {
    try {
        auto *request = reinterpret_cast<Request *>(request_handle);
        if (!request) throw std::runtime_error("The local request could not start.");
        if (cancelled(request)) return 0;
        std::call_once(backend_once, [] { llama_backend_init(); });
        auto params = llama_model_default_params();
        JavaVM *vm = nullptr;
        env->GetJavaVM(&vm);
        jclass callback_type = env->GetObjectClass(callbacks);
        jmethodID on_progress = env->GetMethodID(callback_type, "onProgress", "(IJJ)V");
        env->DeleteLocalRef(callback_type);
        if (!on_progress || env->ExceptionCheck()) return 0;
        auto global_callback = env->NewGlobalRef(callbacks);
        if (!global_callback) return 0;
        auto delete_ref = [env](void *ref) { env->DeleteGlobalRef(static_cast<jobject>(ref)); };
        std::unique_ptr<void, decltype(delete_ref)> callback_guard(global_callback, delete_ref);
        LoadProgress progress{vm, global_callback, on_progress, request};
        params.n_gpu_layers = 0;
        params.load_mode = LLAMA_LOAD_MODE_MMAP;
        params.progress_callback = load_progress;
        params.progress_callback_user_data = &progress;
        auto engine = std::make_unique<Engine>();
        const auto model_path = utf8(env, path);
        if (env->ExceptionCheck()) return 0;
        engine->model = llama_model_load_from_file(model_path.c_str(), params);
        if (progress.callback_failed.load()) throw std::runtime_error("Could not report model loading progress.");
        if (cancelled(request)) return 0;
        if (!engine->model) throw std::runtime_error("Could not load this GGUF. Check that the download is complete and try a smaller model.");
        auto context_params = llama_context_default_params();
        const int trained = llama_model_n_ctx_train(engine->model);
        context_params.n_ctx = std::clamp(static_cast<int>(context_size), 512, 8192);
        if (trained > 0) context_params.n_ctx = std::min(context_params.n_ctx, static_cast<uint32_t>(trained));
        context_params.n_batch = BATCH_SIZE;
        context_params.n_ubatch = BATCH_SIZE;
        context_params.n_threads = std::clamp(static_cast<int>(threads), 1, 4);
        context_params.n_threads_batch = context_params.n_threads;
        engine->context = llama_init_from_model(engine->model, context_params);
        if (cancelled(request)) return 0;
        if (!engine->context) throw std::runtime_error("The model loaded, but there is not enough memory for its context. Try a smaller model.");
        engine->templates = common_chat_templates_init(engine->model, "");
        return reinterpret_cast<jlong>(engine.release());
    } catch (const std::exception &error) {
        fail(env, error.what());
        return 0;
    }
}
extern "C" JNIEXPORT void JNICALL
Java_com_buildai_app_data_local_NativeLlmEngine_nativeUnload(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<Engine *>(handle);
}
extern "C" JNIEXPORT void JNICALL
Java_com_buildai_app_data_local_NativeLlmEngine_nativeGenerate(JNIEnv *env, jobject, jlong handle,
    jobjectArray roles, jobjectArray contents, jint max_tokens, jlong request_handle, jobject callbacks, jstring grammar) {
    try {
        generate(env, reinterpret_cast<Engine *>(handle), roles, contents, max_tokens,
                 reinterpret_cast<Request *>(request_handle), callbacks, utf8(env, grammar));
    } catch (const std::exception &error) {
        fail(env, error.what());
    }
}
