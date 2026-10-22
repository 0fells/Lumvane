set(LUMVANE_VERSION "$ENV{INPUT_VERSION}")
if (NOT LUMVANE_VERSION)
    set(LUMVANE_VERSION "${PROJECT_VERSION}")
endif ()
configure_file("${CMAKE_SOURCE_DIR}/cmake/LumvaneVersion.h.in" "${CMAKE_BINARY_DIR}/LumvaneVersion.h" @ONLY)

set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -DLUMVANE_DEBUG")

function(lumvane_add_compile_definitions arg)
    message("[add_compile_definitions] ${ARGV}")
    add_compile_definitions(${ARGV})
endfunction()
