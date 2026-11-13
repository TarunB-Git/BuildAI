#include "llama-grammar.h"
#include "llama-vocab.h"
#include <fstream>
#include <iterator>
#include <iostream>
#include <cstdlib>
// These paths are not used by character-based grammars. Fail if reached.
int32_t llama_vocab::tokenize(const char*, int32_t, llama_token*, int32_t, bool, bool) const { std::abort(); }
extern "C" void ggml_abort(const char*, int, const char*, ...) { std::abort(); }
int main(int argc,char** argv) {
 for(int i=1;i<argc;i++) { std::ifstream in(argv[i]); std::string grammar((std::istreambuf_iterator<char>(in)),{});
  llama_grammar_parser parser;
  if(grammar.empty() || !parser.parse(grammar.c_str())) return 1;
  std::cout << "Valid pinned-parser grammar: " << argv[i] << " (" << parser.rules.size() << " rules)\n";
 }
}
