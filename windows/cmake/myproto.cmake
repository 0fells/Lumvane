add_subdirectory(3rdparty/simple-protobuf)

spb_protobuf_generate(
        OUT_VAR PROTO_FILES
        PROTOS ${CMAKE_CURRENT_SOURCE_DIR}/../core/windows/gen/libcore.proto
        IMPORT_DIRS ${CMAKE_CURRENT_SOURCE_DIR}/..
        )

add_library(myproto STATIC ${PROTO_FILES})
target_link_libraries(myproto
        PUBLIC
        spb-proto
        )
