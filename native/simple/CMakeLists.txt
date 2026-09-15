cmake_minimum_required(VERSION 3.22)
project(rikkahub_simple LANGUAGES CXX)
if(POLICY CMP0135)
    cmake_policy(SET CMP0135 NEW)
endif()

include(FetchContent)

# Simple revision available when 2.4.5 added the extension. Its embedded pinyin
# table matches the original Android libraries. Keep cppjieba at that revision's pin.
FetchContent_Declare(simple_source
    URL https://codeload.github.com/wangfenjin/simple/tar.gz/ea854b0a727e9f15f4878ad0ae9f1b738a92afe4
    URL_HASH SHA256=a5a63d9bdd079c9a2a6555a9f13cabbf236e6141857246f8d44cd91a450c63ed
    SOURCE_SUBDIR source_only
)
FetchContent_Declare(cppjieba_source
    URL https://codeload.github.com/yanyiwu/cppjieba/tar.gz/194c144d8b5ed1baf3190d07c5226e804454ab47
    URL_HASH SHA256=95e19dccad97c8607461452fab539d125a1354707f84f3179818aff34c26c800
    SOURCE_SUBDIR source_only
)
FetchContent_MakeAvailable(simple_source cppjieba_source)

include("${simple_source_SOURCE_DIR}/contrib/CMakeRC.cmake")
cmrc_add_resource_library(simple_pinyin TYPE OBJECT NAMESPACE pinyin_text
    WHENCE "${simple_source_SOURCE_DIR}"
    "${simple_source_SOURCE_DIR}/contrib/pinyin.txt"
)
set_target_properties(simple_pinyin PROPERTIES POSITION_INDEPENDENT_CODE ON)

# SQLite calls sqlite3_simple_init and supplies its own API table. Do not link
# another SQLite implementation into the extension.
add_library(simple SHARED
    "${simple_source_SOURCE_DIR}/src/entry.cc"
    "${simple_source_SOURCE_DIR}/src/pinyin.cc"
    "${simple_source_SOURCE_DIR}/src/simple_highlight.cc"
    "${simple_source_SOURCE_DIR}/src/simple_tokenizer.cc"
)
target_compile_features(simple PRIVATE cxx_std_14)
set_target_properties(simple PROPERTIES CXX_STANDARD 14 CXX_STANDARD_REQUIRED ON)
target_compile_definitions(simple PRIVATE USE_JIEBA=1)
target_include_directories(simple PRIVATE
    "${simple_source_SOURCE_DIR}/src"
    "${simple_source_SOURCE_DIR}/contrib/sqlite3"
    "${cppjieba_source_SOURCE_DIR}/include"
    "${cppjieba_source_SOURCE_DIR}/deps"
)
target_link_libraries(simple PRIVATE simple_pinyin)

if(WIN32)
    set_target_properties(simple PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS ON)
endif()
if(ANDROID)
    target_link_options(simple PRIVATE "-Wl,-z,max-page-size=16384")
endif()
if(IOS)
    set_target_properties(simple PROPERTIES
        FRAMEWORK TRUE
        MACOSX_FRAMEWORK_IDENTIFIER "me.rerere.rikkahub.simple"
        XCODE_ATTRIBUTE_CODE_SIGNING_ALLOWED "NO"
    )
endif()

install(TARGETS simple LIBRARY DESTINATION . RUNTIME DESTINATION . FRAMEWORK DESTINATION .)
install(FILES "${simple_source_SOURCE_DIR}/LICENSE" DESTINATION licenses RENAME simple-LICENSE)
install(FILES "${cppjieba_source_SOURCE_DIR}/LICENSE" DESTINATION licenses RENAME cppjieba-LICENSE)
