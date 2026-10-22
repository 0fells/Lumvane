# Main CMake file for compiling the library itself, examples and tests.
#
# Copyright (c) 2012-2025 Sebastien Rombauts (sebastien.rombauts@gmail.com)
#
# Distributed under the MIT License (MIT) (See accompanying file LICENSE.txt
# or copy at http://opensource.org/licenses/MIT)
cmake_minimum_required(VERSION 3.5)
list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
project(SQLiteCpp VERSION 3.3.3)

if (NOT CMAKE_CXX_STANDARD)
    set(CMAKE_CXX_STANDARD 11)
elseif (CMAKE_CXX_STANDARD LESS 11)
    message(WARNING "CMAKE_CXX_STANDARD has been set to '${CMAKE_CXX_STANDARD}' which is lower than the minimum required standard (c++11).")
endif ()
message(STATUS "Using c++ standard c++${CMAKE_CXX_STANDARD}")
set(CMAKE_CXX_STANDARD_REQUIRED ON)

message (STATUS "CMake version: ${CMAKE_VERSION}")
message (STATUS "Project version: ${PROJECT_VERSION}")

option(SQLITECPP_BUILD_TESTS "Build and run tests." OFF)

option(BUILD_SHARED_LIBS "Build shared libraries (DLLs)." OFF)

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

if (WIN32)
    set(DEV_NULL                "NUL")
else (WIN32)
    set(DEV_NULL                "/dev/null")
endif (WIN32)

if (MSVC)
    set(CPPLINT_ARG_OUTPUT      "--output=vs7")
    set(CPPCHECK_ARG_TEMPLATE   "--template=vs")
    add_definitions(-D_CRT_SECURE_NO_WARNINGS)
    option(SQLITECPP_USE_STATIC_RUNTIME "Use MSVC static runtime (default for internal googletest)." FALSE)
    if (SQLITECPP_USE_STATIC_RUNTIME)
        message(STATUS "Linking against multithread static C++ runtime")
        foreach (flag CMAKE_C_FLAGS
                      CMAKE_C_FLAGS_DEBUG
                      CMAKE_C_FLAGS_RELEASE
                      CMAKE_C_FLAGS_MINSIZEREL
                      CMAKE_C_FLAGS_RELWITHDEBINFO
                      CMAKE_CXX_FLAGS
                      CMAKE_CXX_FLAGS_DEBUG
                      CMAKE_CXX_FLAGS_RELEASE
                      CMAKE_CXX_FLAGS_MINSIZEREL
                      CMAKE_CXX_FLAGS_RELWITHDEBINFO)

            string(REGEX REPLACE "/MDd" "/MTd" ${flag} "${${flag}}")
            string(REGEX REPLACE "/MD" "/MT" ${flag} "${${flag}}")

        endforeach()
    else (SQLITECPP_USE_STATIC_RUNTIME)
        if (SQLITECPP_BUILD_TESTS)
            message(STATUS "Force googletest to link against dynamic C++ runtime")
            set(gtest_force_shared_crt ON CACHE BOOL "Use shared (DLL) run-time lib even when Google Test is built as static lib.")
        endif (SQLITECPP_BUILD_TESTS)
    endif (SQLITECPP_USE_STATIC_RUNTIME)
    if (MSVC_VERSION LESS 1900)
      message(ERROR "Visual Studio prior to 2015 is not supported anymore.")
    endif (MSVC_VERSION LESS 1900)
else (MSVC)
    set(CPPLINT_ARG_OUTPUT      "--output=eclipse")
    set(CPPCHECK_ARG_TEMPLATE   "--template=gcc")
    if (NOT MINGW)
        option(SQLITECPP_USE_STACK_PROTECTION "USE Stack Protection hardening." ON)
        if (SQLITECPP_USE_STACK_PROTECTION)
            message (STATUS "Using Stack Protection hardening")
            add_compile_options(-fstack-protector)
        endif (SQLITECPP_USE_STACK_PROTECTION)
    endif (NOT MINGW)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -Wpedantic -Wswitch-enum -Wshadow -Wno-long-long")
    if (CMAKE_COMPILER_IS_GNUCXX)
        option(SQLITECPP_USE_GCOV "USE GCov instrumentation." OFF)
        if (SQLITECPP_USE_GCOV)
            message (STATUS "Using GCov instrumentation")
            add_compile_options (-coverage)
            set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -coverage")
        endif ()
    endif (CMAKE_COMPILER_IS_GNUCXX)
endif (MSVC)
set(CPPLINT_ARG_VERBOSE "--verbose=3")
set(CPPLINT_ARG_LINELENGTH "--linelength=120")

message (STATUS "CMAKE_CXX_COMPILER '${CMAKE_CXX_COMPILER}' '${CMAKE_CXX_COMPILER_ID}' '${CMAKE_CXX_COMPILER_VERSION}'")

message (STATUS "CMAKE_CXX_FLAGS                '${CMAKE_CXX_FLAGS}'")
if (MSVC)
    message (STATUS "CMAKE_CXX_FLAGS_DEBUG          '${CMAKE_CXX_FLAGS_DEBUG}'")
    message (STATUS "CMAKE_CXX_FLAGS_RELEASE        '${CMAKE_CXX_FLAGS_RELEASE}'")
    message (STATUS "CMAKE_CXX_FLAGS_RELWITHDEBINFO '${CMAKE_CXX_FLAGS_RELWITHDEBINFO}'")
    message (STATUS "CMAKE_CXX_FLAGS_MINSIZEREL     '${CMAKE_CXX_FLAGS_MINSIZEREL}'")
else ()
    if     (CMAKE_BUILD_TYPE STREQUAL Debug)
        message (STATUS "CMAKE_CXX_FLAGS_DEBUG          '${CMAKE_CXX_FLAGS_DEBUG}'")
    elseif (CMAKE_BUILD_TYPE STREQUAL RelWithDebInfo)
        message (STATUS "CMAKE_CXX_FLAGS_RELWITHDEBINFO '${CMAKE_CXX_FLAGS_RELWITHDEBINFO}'")
    elseif (CMAKE_BUILD_TYPE STREQUAL MinSizeRel)
        message (STATUS "CMAKE_CXX_FLAGS_MINSIZEREL     '${CMAKE_CXX_FLAGS_MINSIZEREL}'")
    else ()
        message (STATUS "CMAKE_CXX_FLAGS_RELEASE        '${CMAKE_CXX_FLAGS_RELEASE}'")
    endif ()
endif ()

set(SQLITECPP_SRC
 ${PROJECT_SOURCE_DIR}/src/Backup.cpp
 ${PROJECT_SOURCE_DIR}/src/Column.cpp
 ${PROJECT_SOURCE_DIR}/src/Database.cpp
 ${PROJECT_SOURCE_DIR}/src/Exception.cpp
 ${PROJECT_SOURCE_DIR}/src/Savepoint.cpp
 ${PROJECT_SOURCE_DIR}/src/Statement.cpp
 ${PROJECT_SOURCE_DIR}/src/Transaction.cpp
)
source_group(src FILES ${SQLITECPP_SRC})

set(SQLITECPP_INC
 ${PROJECT_SOURCE_DIR}/include/SQLiteCpp/SQLiteCpp.h
 ${PROJECT_SOURCE_DIR}/include/SQLiteCpp/Assertion.h
 ${PROJECT_SOURCE_DIR}/include/SQLiteCpp/Backup.h
 ${PROJECT_SOURCE_DIR}/include/SQLiteCpp/Column.h
 ${PROJECT_SOURCE_DIR}/include/SQLiteCpp/Database.h
 ${PROJECT_SOURCE_DIR}/include/SQLiteCpp/Exception.h
 ${PROJECT_SOURCE_DIR}/include/SQLiteCpp/Savepoint.h
 ${PROJECT_SOURCE_DIR}/include/SQLiteCpp/Statement.h
 ${PROJECT_SOURCE_DIR}/include/SQLiteCpp/Transaction.h
 ${PROJECT_SOURCE_DIR}/include/SQLiteCpp/VariadicBind.h
 ${PROJECT_SOURCE_DIR}/include/SQLiteCpp/ExecuteMany.h
)
source_group(include FILES ${SQLITECPP_INC})

set(SQLITECPP_TESTS
 tests/Column_test.cpp
 tests/Database_test.cpp
 tests/Savepoint_test.cpp
 tests/Statement_test.cpp
 tests/Backup_test.cpp
 tests/Transaction_test.cpp
 tests/VariadicBind_test.cpp
 tests/Exception_test.cpp
 tests/ExecuteMany_test.cpp
)
source_group(tests FILES ${SQLITECPP_TESTS})

set(SQLITECPP_EXAMPLES
 examples/example1/main.cpp
)
source_group(example1 FILES ${SQLITECPP_EXAMPLES})

set(SQLITECPP_DOC
 README.md
 LICENSE.txt
 CHANGELOG.md
 TODO.txt
)
source_group(doc FILES ${SQLITECPP_DOC})

option(SQLITECPP_INCLUDE_SCRIPT "Include config & script files." ON)
if (SQLITECPP_INCLUDE_SCRIPT)
    set(SQLITECPP_SCRIPT
     .editorconfig
     .gitbugtraq
     .github/dependabot.yml
     .github/workflows/cmake.yml
     .github/workflows/cmake_builtin_lib.yml
     .github/workflows/cmake_subdir_example.yml
     .github/workflows/meson.yml
     .gitignore
     .gitmodules
     .travis.yml
     appveyor.yml
     build.bat
     build.sh
     cmake/FindSQLite3.cmake
     cmake/SQLiteCppConfig.cmake.in
     cpplint.py
     Doxyfile
     meson.build
     meson_options.txt
     package.xml
    )
    source_group(scripts FILES ${SQLITECPP_SCRIPT})
endif()

add_library(SQLiteCpp ${SQLITECPP_SRC} ${SQLITECPP_INC} ${SQLITECPP_DOC} ${SQLITECPP_SCRIPT})

option(SQLITE_ENABLE_COLUMN_METADATA "Enable Column::getColumnOriginName(). Require support from sqlite3 library." ON)
if (SQLITE_ENABLE_COLUMN_METADATA)
    target_compile_definitions(SQLiteCpp PUBLIC SQLITE_ENABLE_COLUMN_METADATA)
endif (SQLITE_ENABLE_COLUMN_METADATA)

option(SQLITE_ENABLE_ASSERT_HANDLER "Enable the user definition of a assertion_failed() handler." OFF)
if (SQLITE_ENABLE_ASSERT_HANDLER)
    target_compile_definitions(SQLiteCpp PUBLIC SQLITECPP_ENABLE_ASSERT_HANDLER)
endif (SQLITE_ENABLE_ASSERT_HANDLER)

option(SQLITE_HAS_CODEC "Enable database encryption API. Not available in the public release of SQLite." OFF)
if (SQLITE_HAS_CODEC)
    target_compile_definitions(SQLiteCpp PUBLIC SQLITE_HAS_CODEC)
endif (SQLITE_HAS_CODEC)

option(SQLITE_USE_LEGACY_STRUCT "Fallback to forward declaration of legacy struct sqlite3_value (pre SQLite 3.19)" OFF)
if (SQLITE_USE_LEGACY_STRUCT)
    target_compile_definitions(SQLiteCpp PUBLIC SQLITE_USE_LEGACY_STRUCT)
endif (SQLITE_USE_LEGACY_STRUCT)

if (BUILD_SHARED_LIBS)
    if (WIN32)
        message(STATUS "Build shared libraries (DLLs).")
        target_compile_definitions(SQLiteCpp PUBLIC "SQLITECPP_COMPILE_DLL")
        target_compile_definitions(SQLiteCpp PRIVATE "SQLITECPP_DLL_EXPORT")
    endif()
endif()

option(SQLITE_OMIT_LOAD_EXTENSION "Enable omit load extension" OFF)
if (SQLITE_OMIT_LOAD_EXTENSION)
    target_compile_definitions(SQLiteCpp PUBLIC SQLITE_OMIT_LOAD_EXTENSION)
endif (SQLITE_OMIT_LOAD_EXTENSION)

if (UNIX AND (CMAKE_COMPILER_IS_GNUCXX OR ${CMAKE_CXX_COMPILER_ID} STREQUAL "Clang"))
    set_target_properties(SQLiteCpp PROPERTIES COMPILE_FLAGS "-fPIC")
endif (UNIX AND (CMAKE_COMPILER_IS_GNUCXX OR ${CMAKE_CXX_COMPILER_ID} STREQUAL "Clang"))

option(SQLITECPP_USE_ASAN "Use Address Sanitizer." OFF)
if (SQLITECPP_USE_ASAN)
    if ((CMAKE_CXX_COMPILER_VERSION GREATER_EQUAL 6) OR ("${CMAKE_CXX_COMPILER_ID}" STREQUAL "Clang"))
        message (STATUS "Using Address Sanitizer")
        set_target_properties(SQLiteCpp PROPERTIES COMPILE_FLAGS "-fsanitize=address -fno-omit-frame-pointer")
        set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -fsanitize=address")
        if (CMAKE_COMPILER_IS_GNUCXX)
            set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -fuse-ld=gold")
        endif ()
    endif ()
endif (SQLITECPP_USE_ASAN)

if (SQLITECPP_USE_GCOV)
    set_target_properties(SQLiteCpp PROPERTIES COMPILE_FLAGS "-fkeep-inline-functions -fkeep-static-functions")
endif ()

option(SQLITECPP_INTERNAL_SQLITE "Add the internal SQLite3 source to the project." ON)
if (SQLITECPP_INTERNAL_SQLITE)
    message(STATUS "Compile sqlite3 from source in subdirectory")
    option(SQLITE_ENABLE_RTREE "Enable RTree extension when building internal sqlite3 library." OFF)
    option(SQLITE_ENABLE_DBSTAT_VTAB "Enable DBSTAT read-only eponymous virtual table extension when building internal sqlite3 library." OFF)
    add_subdirectory(sqlite3)
    target_link_libraries(SQLiteCpp PUBLIC SQLite::SQLite3)
else (SQLITECPP_INTERNAL_SQLITE)
    if(SQLITE_HAS_CODEC)
        find_package(PkgConfig QUIET)
        if(PKG_CONFIG_FOUND)
            if(CMAKE_VERSION VERSION_LESS 3.6.3)
                pkg_check_modules(sqlcipher REQUIRED sqlcipher)
                target_link_libraries(SQLiteCpp PRIVATE ${sqlcipher_LIBRARIES})
                target_include_directories(SQLiteCpp PRIVATE ${sqlcipher_INCLUDE_DIRS})
            else()
                pkg_check_modules(sqlcipher REQUIRED IMPORTED_TARGET sqlcipher)
                target_link_libraries(SQLiteCpp PRIVATE PkgConfig::sqlcipher)
            endif()
        else()
            find_library(sqlcipher_LIBRARY "sqlcipher")
            find_path(sqlcipher_INCLUDE_DIR "sqlcipher/sqlite3.h"
                PATH_SUFFIXES
                    "include"
                    "includes"
            )
            mark_as_advanced(sqlcipher_LIBRARY sqlcipher_INCLUDE_DIR)
            if(NOT sqlcipher_INCLUDE_DIR)
                message(FATAL_ERROR "${PROJECT_NAME} requires the \"<sqlcipher/sqlite3.h>\" header to use the codec functionality but it wasn't found.")
            elseif(NOT sqlcipher_LIBRARY)
                message(FATAL_ERROR "${PROJECT_NAME} requires the sqlcipher library to use the codec functionality but it wasn't found.")
            endif()
            target_include_directories(SQLiteCpp PRIVATE "${sqlcipher_INCLUDE_DIR}/sqlcipher")
            target_link_libraries(SQLiteCpp PRIVATE ${sqlcipher_LIBRARY})
        endif()
    else()
        find_package (SQLite3 REQUIRED)
        message(STATUS "Link to sqlite3 system library ${SQLite3_VERSION}")
        target_link_libraries(SQLiteCpp PUBLIC SQLite::SQLite3)
        if(SQLite3_VERSION VERSION_LESS "3.19")
            set_target_properties(SQLiteCpp PROPERTIES COMPILE_FLAGS "-DSQLITECPP_HAS_MEM_STRUCT")
        endif()
    endif()
endif (SQLITECPP_INTERNAL_SQLITE)

option(SQLITECPP_DISABLE_STD_FILESYSTEM "Disable the use of std::filesystem in SQLiteCpp." OFF)
if (SQLITECPP_DISABLE_STD_FILESYSTEM)
    message (STATUS "Disabling std::filesystem support")
    target_compile_definitions(SQLiteCpp PUBLIC SQLITECPP_DISABLE_STD_FILESYSTEM)
endif (SQLITECPP_DISABLE_STD_FILESYSTEM)

option(SQLITECPP_DISABLE_EXPANDED_SQL "Disable the use of sqlite3_expanded_sql in SQLiteCpp." OFF)
if (SQLITECPP_DISABLE_EXPANDED_SQL)
    message (STATUS "Disabling sqlite3_expanded_sql support")
    target_compile_definitions(SQLiteCpp PUBLIC SQLITECPP_DISABLE_EXPANDED_SQL)
endif (SQLITECPP_DISABLE_EXPANDED_SQL)

if (UNIX)
    set(THREADS_PREFER_PTHREAD_FLAG ON)
    find_package(Threads REQUIRED)
    target_link_libraries(SQLiteCpp PUBLIC Threads::Threads ${CMAKE_DL_LIBS})
endif (UNIX)

target_include_directories(SQLiteCpp
  PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:include/>)

include(GNUInstallDirs)
install(TARGETS SQLiteCpp
    EXPORT ${PROJECT_NAME}Targets
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
    COMPONENT libraries)
install(DIRECTORY include/ DESTINATION ${CMAKE_INSTALL_INCLUDEDIR} COMPONENT headers FILES_MATCHING REGEX ".*\\.(hpp|h)$")
install(EXPORT ${PROJECT_NAME}Targets DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/${PROJECT_NAME})
install(FILES ${PROJECT_SOURCE_DIR}/package.xml DESTINATION ${CMAKE_INSTALL_DATADIR}/${PROJECT_NAME})

include(CMakePackageConfigHelpers)
write_basic_package_version_file(
    cmake/${PROJECT_NAME}ConfigVersion.cmake
    VERSION ${PROJECT_VERSION}
    COMPATIBILITY AnyNewerVersion)
configure_package_config_file(
    cmake/${PROJECT_NAME}Config.cmake.in
    cmake/${PROJECT_NAME}Config.cmake
    INSTALL_DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/${PROJECT_NAME})
install(FILES
    ${CMAKE_CURRENT_BINARY_DIR}/cmake/${PROJECT_NAME}Config.cmake
    ${CMAKE_CURRENT_BINARY_DIR}/cmake/${PROJECT_NAME}ConfigVersion.cmake
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/${PROJECT_NAME})

option(SQLITECPP_RUN_CPPLINT "Run cpplint.py tool for Google C++ StyleGuide." ON)
if (SQLITECPP_RUN_CPPLINT)
    if(${CMAKE_VERSION} VERSION_LESS "3.12.0") 
        find_package(PythonInterp)
        if (PYTHONINTERP_FOUND)
            add_custom_target(SQLiteCpp_cpplint
            ALL
            COMMAND ${PYTHON_EXECUTABLE} ${PROJECT_SOURCE_DIR}/cpplint.py ${CPPLINT_ARG_OUTPUT} ${CPPLINT_ARG_VERBOSE} ${CPPLINT_ARG_LINELENGTH} ${SQLITECPP_SRC} ${SQLITECPP_INC}
            )
        endif (PYTHONINTERP_FOUND)
    else()
        find_package(Python)
        if (PYTHON_INTERPRETER_FOUND)
            add_custom_target(SQLiteCpp_cpplint
            ALL
            COMMAND ${PYTHON_EXECUTABLE} ${PROJECT_SOURCE_DIR}/cpplint.py ${CPPLINT_ARG_OUTPUT} ${CPPLINT_ARG_VERBOSE} ${CPPLINT_ARG_LINELENGTH} ${SQLITECPP_SRC} ${SQLITECPP_INC}
            )
        endif (PYTHON_INTERPRETER_FOUND)
    endif()
else (SQLITECPP_RUN_CPPLINT)
    message(STATUS "SQLITECPP_RUN_CPPLINT OFF")
endif (SQLITECPP_RUN_CPPLINT)

option(SQLITECPP_RUN_CPPCHECK "Run cppcheck C++ static analysis tool." ON)
if (SQLITECPP_RUN_CPPCHECK)
    find_program(CPPCHECK_EXECUTABLE NAMES cppcheck)
    if (CPPCHECK_EXECUTABLE)
        add_custom_target(SQLiteCpp_cppcheck
         ALL
         COMMAND ${CPPCHECK_EXECUTABLE} -j 8 cppcheck --enable=style --quiet ${CPPCHECK_ARG_TEMPLATE} ${PROJECT_SOURCE_DIR}/src
        )
        execute_process(COMMAND "${CPPCHECK_EXECUTABLE}" --version OUTPUT_VARIABLE CPPCHECK_VERSION OUTPUT_STRIP_TRAILING_WHITESPACE)
        message(STATUS "Found Cppcheck: ${CPPCHECK_EXECUTABLE} ${CPPCHECK_VERSION}")
    else (CPPCHECK_EXECUTABLE)
        message(STATUS "Could NOT find cppcheck")
    endif (CPPCHECK_EXECUTABLE)
else (SQLITECPP_RUN_CPPCHECK)
    message(STATUS "SQLITECPP_RUN_CPPCHECK OFF")
endif (SQLITECPP_RUN_CPPCHECK)

option(SQLITECPP_RUN_DOXYGEN "Run Doxygen C++ documentation tool." OFF)
if (SQLITECPP_RUN_DOXYGEN)
    find_package(Doxygen)
    if (DOXYGEN_FOUND)
        add_custom_target(SQLiteCpp_doxygen
         ALL
         COMMAND doxygen Doxyfile > ${DEV_NULL}
         WORKING_DIRECTORY ${PROJECT_SOURCE_DIR}
        )
    endif (DOXYGEN_FOUND)
else (SQLITECPP_RUN_DOXYGEN)
    message(STATUS "SQLITECPP_RUN_DOXYGEN OFF")
endif (SQLITECPP_RUN_DOXYGEN)

option(SQLITECPP_BUILD_EXAMPLES "Build examples." OFF)
if (SQLITECPP_BUILD_EXAMPLES)

    add_executable(SQLiteCpp_example1 ${SQLITECPP_EXAMPLES})
    
    target_link_libraries(SQLiteCpp_example1 SQLiteCpp)
    if (MSYS OR MINGW)
        target_link_libraries(SQLiteCpp_example1 ssp)
    endif ()
else (SQLITECPP_BUILD_EXAMPLES)
    message(STATUS "SQLITECPP_BUILD_EXAMPLES OFF")
endif (SQLITECPP_BUILD_EXAMPLES)

if (SQLITECPP_BUILD_TESTS)
    add_executable(SQLiteCpp_tests ${SQLITECPP_TESTS})
    target_link_libraries(SQLiteCpp_tests SQLiteCpp)

    find_package(GTest)
    if (GTEST_FOUND)
        message(STATUS "Link to GTest system library")
        target_link_libraries(SQLiteCpp_tests GTest::GTest GTest::Main)
    else (GTEST_FOUND)
        message(STATUS "Compile googletest from source in submodule")
        if (NOT MSVC)
            add_compile_options(-Wno-switch-enum)
        endif (NOT MSVC)

        if (NOT EXISTS "${PROJECT_SOURCE_DIR}/googletest/CMakeLists.txt")
            message(FATAL_ERROR "Missing 'googletest' submodule! Either use 'git submodule init' and 'git submodule update' to get googletest according to the README, or deactivate unit tests with -DSQLITECPP_BUILD_TESTS=OFF")
        endif ()
        set(BUILD_GMOCK OFF CACHE BOOL "Builds the googlemock subproject")
        add_subdirectory(googletest)
        include_directories("${PROJECT_SOURCE_DIR}/googletest/googletest/include")

        if (MSVC)
            if (MSVC_VERSION GREATER_EQUAL 1910 AND MSVC_VERSION LESS_EQUAL 1919)
                target_compile_definitions(gtest PUBLIC _SILENCE_TR1_NAMESPACE_DEPRECATION_WARNING)
                target_compile_definitions(gtest_main PUBLIC _SILENCE_TR1_NAMESPACE_DEPRECATION_WARNING)
                target_compile_definitions(gmock PUBLIC _SILENCE_TR1_NAMESPACE_DEPRECATION_WARNING)
                target_compile_definitions(gmock_main PUBLIC _SILENCE_TR1_NAMESPACE_DEPRECATION_WARNING)
            endif (MSVC_VERSION GREATER_EQUAL 1910 AND MSVC_VERSION LESS_EQUAL 1919)
        endif (MSVC)

        target_link_libraries(SQLiteCpp_tests gtest_main)
    endif (GTEST_FOUND)

    enable_testing()

    add_test(UnitTests bin/SQLiteCpp_tests)

    if (SQLITECPP_BUILD_EXAMPLES)
        add_test(Example1Run bin/SQLiteCpp_example1)
    endif (SQLITECPP_BUILD_EXAMPLES)
else (SQLITECPP_BUILD_TESTS)
    message(STATUS "SQLITECPP_BUILD_TESTS OFF")
endif (SQLITECPP_BUILD_TESTS)

set_property(TARGET SQLiteCpp PROPERTY SOVERSION 0)
